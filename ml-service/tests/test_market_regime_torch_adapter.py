import builtins

from app.services.market_regime_config import MarketRegimeSettings
from app.services.market_regime_torch_adapter import load_torch, validate_artifact_path


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
