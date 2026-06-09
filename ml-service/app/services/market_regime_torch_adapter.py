from pathlib import Path
from typing import Any
from decimal import Decimal, ROUND_HALF_UP
from collections.abc import Mapping
import math

from app.schemas.market_regime_schema import MarketRegimeRequest, MarketRegimeResponse
from app.services.market_regime_config import MarketRegimeSettings
from app.services.market_regime_experiment import MARKET_REGIME_FEATURE_VERSION
from app.services.market_regime_features import build_market_regime_features
from app.services.market_regime_torch_features import (
    TORCH_MARKET_REGIME_FEATURE_ORDER,
    build_torch_feature_matrix,
)
from app.services.market_regime_torch_model import build_transformer_model


class TorchMarketRegimeClassifier:
    def __init__(self, settings: MarketRegimeSettings) -> None:
        self.settings = settings

    def classify(self, request: MarketRegimeRequest) -> MarketRegimeResponse:
        artifact_path = validate_artifact_path(self.settings)
        torch = load_torch()
        device = select_device(torch)
        artifact = load_artifact(torch, artifact_path, device)
        metadata = validate_artifact_metadata(artifact)
        sequence_length = int(metadata["sequenceLength"])

        if len(request.candles) < sequence_length:
            raise RuntimeError(
                f"Market regime torch artifact requires at least {sequence_length} candles."
            )

        # Torch artifacts define their own sequence length, so inference uses the latest matching tail.
        feature_matrix = build_torch_feature_matrix(request.candles[-sequence_length:])
        normalized_features = normalize_features(feature_matrix, metadata)
        input_tensor = torch.tensor([normalized_features], dtype=torch.float32, device=device)

        model = build_transformer_model(
            torch,
            feature_count=len(TORCH_MARKET_REGIME_FEATURE_ORDER),
            class_count=len(metadata["labels"]),
            config=metadata.get("model"),
        )
        model.load_state_dict(artifact["modelStateDict"])
        model.to(device)
        model.eval()

        with torch.no_grad():
            logits = model(input_tensor)
            probabilities = torch.softmax(logits, dim=-1)[0]
            confidence_value, label_index = probabilities.max(dim=0)

        label = metadata["labels"][int(label_index.item())]
        confidence = Decimal(str(float(confidence_value.item()) * 100)).quantize(
            Decimal("0.01"),
            rounding=ROUND_HALF_UP,
        )

        return MarketRegimeResponse(
            regimeLabel=label,
            confidence=confidence,
            reasons=[
                f"Torch sequence model selected {label} from the latest {sequence_length} candles.",
                f"Inference device: {device}.",
            ],
            features=build_market_regime_features(request.candles),
            classifierSource="torch",
            mode="torch",
            modelVersion=str(metadata.get("modelVersion", "local-artifact")),
            featureVersion=str(metadata.get("featureVersion", MARKET_REGIME_FEATURE_VERSION)),
            sequenceLength=sequence_length,
            artifactIdentifier=artifact_path.name,
        )


def load_torch():
    try:
        import torch
    except ModuleNotFoundError as exc:
        raise RuntimeError(
            "PyTorch is required when MARKET_REGIME_MODE=torch. "
            "Install ml-service/requirements-torch.txt to enable torch inference."
        ) from exc
    return torch


def validate_artifact_path(settings: MarketRegimeSettings) -> Path:
    if settings.artifact_path is None:
        raise RuntimeError("MARKET_REGIME_ARTIFACT_PATH is required when MARKET_REGIME_MODE=torch.")

    artifact_path = Path(settings.artifact_path)
    if not artifact_path.is_file():
        raise RuntimeError(f"Market regime model artifact not found: {settings.artifact_path}")

    return artifact_path


def select_device(torch):
    if torch.cuda.is_available():
        # GPU is used opportunistically; the default Docker path remains CPU-safe when CUDA is absent.
        return torch.device("cuda")
    return torch.device("cpu")


def load_artifact(torch, artifact_path: Path, device) -> dict[str, Any]:
    try:
        artifact = torch.load(artifact_path, map_location=device)
    except Exception as exc:
        raise RuntimeError(f"Unable to load market regime artifact: {artifact_path}") from exc
    if not isinstance(artifact, dict):
        raise RuntimeError("Market regime artifact must be a dictionary.")
    return artifact


def validate_artifact_metadata(artifact: dict[str, Any]) -> dict[str, Any]:
    metadata = artifact.get("metadata")
    if not isinstance(metadata, dict):
        raise RuntimeError("Market regime artifact metadata is required.")

    # Validate the artifact contract before building the model to fail with useful setup errors.
    model_state = artifact.get("modelStateDict")
    if not isinstance(model_state, Mapping) or not model_state:
        raise RuntimeError("Market regime artifact modelStateDict is required.")

    sequence_length = metadata.get("sequenceLength")
    if not isinstance(sequence_length, int) or sequence_length <= 0:
        raise RuntimeError("Market regime artifact metadata.sequenceLength must be a positive int.")

    feature_order = metadata.get("featureOrder")
    if feature_order != TORCH_MARKET_REGIME_FEATURE_ORDER:
        raise RuntimeError(
            "Market regime artifact metadata.featureOrder does not match the service feature order."
        )

    labels = metadata.get("labels")
    if not isinstance(labels, list) or not labels or not all(isinstance(label, str) for label in labels):
        raise RuntimeError("Market regime artifact metadata.labels must be a non-empty string list.")
    if len(labels) != len(set(labels)):
        # Duplicate labels would make class indexes ambiguous in responses and evaluation reports.
        raise RuntimeError("Market regime artifact metadata.labels must not contain duplicates.")
    metadata["labels"] = labels

    model_version = metadata.get("modelVersion")
    if model_version is not None and not isinstance(model_version, str):
        raise RuntimeError("Market regime artifact metadata.modelVersion must be a string when provided.")

    feature_version = metadata.get("featureVersion")
    if feature_version is not None and not isinstance(feature_version, str):
        raise RuntimeError("Market regime artifact metadata.featureVersion must be a string when provided.")

    model_config = metadata.get("model")
    if not isinstance(model_config, dict):
        raise RuntimeError("Market regime artifact metadata.model must be an object.")

    normalization = metadata.get("normalization")
    if normalization is not None:
        validate_normalization(normalization)

    return metadata


def validate_normalization(normalization: Any) -> None:
    if not isinstance(normalization, dict):
        raise RuntimeError("Market regime artifact metadata.normalization must be an object.")

    for key in ("mean", "std"):
        values = normalization.get(key)
        if not isinstance(values, list) or len(values) != len(TORCH_MARKET_REGIME_FEATURE_ORDER):
            raise RuntimeError(
                f"Market regime artifact metadata.normalization.{key} must match feature count."
            )
        if not all(is_finite_number(value) for value in values):
            raise RuntimeError(
                f"Market regime artifact metadata.normalization.{key} must contain only finite numbers."
            )

    if any(float(value) == 0.0 for value in normalization["std"]):
        raise RuntimeError("Market regime artifact normalization std values must be non-zero.")


def is_finite_number(value: Any) -> bool:
    try:
        return math.isfinite(float(value))
    except (TypeError, ValueError):
        return False


def normalize_features(feature_matrix: list[list[float]], metadata: dict[str, Any]) -> list[list[float]]:
    normalization = metadata.get("normalization")
    if normalization is None:
        return feature_matrix

    # Training writes mean/std values into metadata so inference can use the same scaling.
    means = [float(value) for value in normalization["mean"]]
    stds = [float(value) for value in normalization["std"]]
    return [
        [(value - means[index]) / stds[index] for index, value in enumerate(row)]
        for row in feature_matrix
    ]
