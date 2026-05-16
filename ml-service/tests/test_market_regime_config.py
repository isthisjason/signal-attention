from app.services.market_regime_config import get_market_regime_settings


def test_default_market_regime_mode_is_rules(monkeypatch) -> None:
    monkeypatch.delenv("MARKET_REGIME_MODE", raising=False)

    settings = get_market_regime_settings()

    assert settings.mode == "rules"


def test_market_regime_mode_is_normalized(monkeypatch) -> None:
    monkeypatch.setenv("MARKET_REGIME_MODE", " RULES ")

    settings = get_market_regime_settings()

    assert settings.mode == "rules"
