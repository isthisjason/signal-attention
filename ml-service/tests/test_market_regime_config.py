from app.services.market_regime_config import get_market_regime_settings


def test_default_market_regime_mode_is_rules(monkeypatch) -> None:
    monkeypatch.delenv("MARKET_REGIME_MODE", raising=False)
    monkeypatch.delenv("MARKET_REGIME_ARTIFACT_PATH", raising=False)

    settings = get_market_regime_settings()

    assert settings.mode == "rules"
    assert settings.artifact_path is None


def test_market_regime_mode_is_normalized(monkeypatch) -> None:
    monkeypatch.setenv("MARKET_REGIME_MODE", " RULES ")

    settings = get_market_regime_settings()

    assert settings.mode == "rules"


def test_market_regime_artifact_path_is_optional(monkeypatch) -> None:
    monkeypatch.setenv("MARKET_REGIME_ARTIFACT_PATH", "  ")

    settings = get_market_regime_settings()

    assert settings.artifact_path is None


def test_market_regime_artifact_path_is_trimmed(monkeypatch) -> None:
    monkeypatch.setenv("MARKET_REGIME_ARTIFACT_PATH", " models/regime.pt ")

    settings = get_market_regime_settings()

    assert settings.artifact_path == "models/regime.pt"
