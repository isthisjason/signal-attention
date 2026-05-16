from pathlib import Path

from app.schemas.market_regime_schema import MarketRegimeRequest, MarketRegimeResponse
from app.services.market_regime_config import MarketRegimeSettings


class TorchMarketRegimeClassifier:
    def __init__(self, settings: MarketRegimeSettings) -> None:
        self.settings = settings

    def classify(self, request: MarketRegimeRequest) -> MarketRegimeResponse:
        validate_artifact_path(self.settings)
        load_torch()
        raise RuntimeError("Torch market regime inference is not implemented yet.")


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
