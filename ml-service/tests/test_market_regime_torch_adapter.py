import builtins

from app.services.market_regime_torch_adapter import load_torch


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
