import builtins
from datetime import UTC, datetime, timedelta
from decimal import Decimal

import app.services.market_regime_torch_adapter as adapter
from app.schemas.market_regime_schema import MarketRegimeCandle, MarketRegimeRequest
from app.services.market_regime_config import MarketRegimeSettings
from app.services.market_regime_torch_adapter import (
    load_torch,
    normalize_features,
    validate_artifact_metadata,
    validate_artifact_path,
)
from app.services.market_regime_torch_features import TORCH_MARKET_REGIME_FEATURE_ORDER


def test_missing_torch_dependency_error_is_clear(monkeypatch) -> None:
    original_import = builtins.__import__

    def fake_import(name, *args, **kwargs):
        if name == "torch":
            raise ModuleNotFoundError("No module named 'torch'")
        return original_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", fake_import)

    try:
        load_torch()
    except RuntimeError as exc:
        assert "PyTorch is required when MARKET_REGIME_MODE=torch" in str(exc)
    else:
        raise AssertionError("Expected missing PyTorch dependency to fail")


def test_missing_torch_artifact_setting_error_is_clear() -> None:
    settings = MarketRegimeSettings(mode="torch")

    try:
        validate_artifact_path(settings)
    except RuntimeError as exc:
        assert str(exc) == "MARKET_REGIME_ARTIFACT_PATH is required when MARKET_REGIME_MODE=torch."
    else:
        raise AssertionError("Expected missing artifact setting to fail")


def test_missing_torch_artifact_file_error_is_clear(tmp_path) -> None:
    artifact_path = tmp_path / "missing.pt"
    settings = MarketRegimeSettings(mode="torch", artifact_path=str(artifact_path))

    try:
        validate_artifact_path(settings)
    except RuntimeError as exc:
        assert str(exc) == f"Market regime model artifact not found: {artifact_path}"
    else:
        raise AssertionError("Expected missing artifact file to fail")


def test_invalid_artifact_metadata_error_is_clear() -> None:
    artifact = {
        "metadata": {
            "sequenceLength": 20,
            "featureOrder": ["close"],
            "labels": ["SIDEWAYS"],
            "model": {},
        },
        "modelStateDict": {"weight": "value"},
    }

    try:
        validate_artifact_metadata(artifact)
    except RuntimeError as exc:
        assert "metadata.featureOrder does not match" in str(exc)
    else:
        raise AssertionError("Expected invalid artifact metadata to fail")


def test_missing_model_state_dict_error_is_clear() -> None:
    artifact = {
        "metadata": {
            "sequenceLength": 20,
            "featureOrder": TORCH_MARKET_REGIME_FEATURE_ORDER,
            "labels": ["SIDEWAYS"],
            "model": {},
        },
    }

    try:
        validate_artifact_metadata(artifact)
    except RuntimeError as exc:
        assert str(exc) == "Market regime artifact modelStateDict is required."
    else:
        raise AssertionError("Expected missing model state to fail")


def test_invalid_sequence_length_error_is_clear() -> None:
    artifact = {
        "metadata": {
            "sequenceLength": 0,
            "featureOrder": TORCH_MARKET_REGIME_FEATURE_ORDER,
            "labels": ["SIDEWAYS"],
            "model": {},
        },
        "modelStateDict": {"weight": "value"},
    }

    try:
        validate_artifact_metadata(artifact)
    except RuntimeError as exc:
        assert "metadata.sequenceLength must be a positive int" in str(exc)
    else:
        raise AssertionError("Expected invalid sequence length to fail")


def test_missing_labels_error_is_clear() -> None:
    artifact = {
        "metadata": {
            "sequenceLength": 20,
            "featureOrder": TORCH_MARKET_REGIME_FEATURE_ORDER,
            "model": {},
        },
        "modelStateDict": {"weight": "value"},
    }

    try:
        validate_artifact_metadata(artifact)
    except RuntimeError as exc:
        assert str(exc) == "Market regime artifact metadata.labels must be a non-empty string list."
    else:
        raise AssertionError("Expected missing labels to fail")


def test_missing_model_config_error_is_clear() -> None:
    artifact = {
        "metadata": {
            "sequenceLength": 20,
            "featureOrder": TORCH_MARKET_REGIME_FEATURE_ORDER,
            "labels": ["SIDEWAYS"],
        },
        "modelStateDict": {"weight": "value"},
    }

    try:
        validate_artifact_metadata(artifact)
    except RuntimeError as exc:
        assert str(exc) == "Market regime artifact metadata.model must be an object."
    else:
        raise AssertionError("Expected missing model config to fail")


def test_normalizes_features_from_artifact_metadata() -> None:
    metadata = {
        "normalization": {
            "mean": [1 for _ in TORCH_MARKET_REGIME_FEATURE_ORDER],
            "std": [2 for _ in TORCH_MARKET_REGIME_FEATURE_ORDER],
        }
    }
    feature_matrix = [[3 for _ in TORCH_MARKET_REGIME_FEATURE_ORDER]]

    normalized = normalize_features(feature_matrix, metadata)

    assert normalized == [[1.0 for _ in TORCH_MARKET_REGIME_FEATURE_ORDER]]


def test_torch_classifier_returns_model_label(tmp_path, monkeypatch) -> None:
    artifact_path = tmp_path / "market-regime.pt"
    artifact_path.write_text("placeholder")
    artifact = {
        "metadata": {
            "sequenceLength": 20,
            "featureOrder": TORCH_MARKET_REGIME_FEATURE_ORDER,
            "labels": ["SIDEWAYS", "TRENDING_UP", "TRENDING_DOWN", "HIGH_VOLATILITY"],
            "model": {},
        },
        "modelStateDict": {"weight": "value"},
    }

    fake_torch = FakeTorch(artifact)
    fake_model = FakeModel()
    monkeypatch.setattr(adapter, "load_torch", lambda: fake_torch)
    monkeypatch.setattr(adapter, "build_transformer_model", lambda *args, **kwargs: fake_model)

    classifier = adapter.TorchMarketRegimeClassifier(
        MarketRegimeSettings(mode="torch", artifact_path=str(artifact_path))
    )

    response = classifier.classify(request_for([Decimal("100") + Decimal(index) for index in range(20)]))

    assert response.regimeLabel == "TRENDING_UP"
    assert response.confidence == Decimal("80.00")
    assert response.classifierSource == "torch"
    assert fake_model.loaded_state_dict == {"weight": "value"}


def request_for(closes: list[Decimal]) -> MarketRegimeRequest:
    return MarketRegimeRequest(
        symbol="BTC-USD",
        timeframe="1h",
        candles=[
            MarketRegimeCandle(
                openTime=datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
                open=close,
                high=close + Decimal("1"),
                low=close - Decimal("1"),
                close=close,
                volume=Decimal("1000"),
            )
            for index, close in enumerate(closes)
        ],
    )


class FakeScalar:
    def __init__(self, value) -> None:
        self.value = value

    def item(self):
        return self.value


class FakeProbabilities:
    def __getitem__(self, index: int) -> "FakeProbabilities":
        return self

    def max(self, dim: int):
        return FakeScalar(0.8), FakeScalar(1)


class FakeNoGrad:
    def __enter__(self) -> None:
        return None

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None


class FakeCuda:
    def is_available(self) -> bool:
        return False


class FakeTorch:
    float32 = "float32"

    def __init__(self, artifact) -> None:
        self.artifact = artifact
        self.cuda = FakeCuda()

    def device(self, name: str) -> str:
        return name

    def load(self, artifact_path, map_location):
        return self.artifact

    def tensor(self, values, dtype, device):
        return values

    def no_grad(self) -> FakeNoGrad:
        return FakeNoGrad()

    def softmax(self, logits, dim: int) -> FakeProbabilities:
        return FakeProbabilities()


class FakeModel:
    def __init__(self) -> None:
        self.loaded_state_dict = None

    def load_state_dict(self, state_dict) -> None:
        self.loaded_state_dict = state_dict

    def to(self, device: str) -> None:
        return None

    def eval(self) -> None:
        return None

    def __call__(self, input_tensor):
        return ["logits"]
