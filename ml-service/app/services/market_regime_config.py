import os
from dataclasses import dataclass


DEFAULT_MARKET_REGIME_MODE = "rules"
MARKET_REGIME_MODE_ENV = "MARKET_REGIME_MODE"


@dataclass(frozen=True)
class MarketRegimeSettings:
    mode: str = DEFAULT_MARKET_REGIME_MODE


def get_market_regime_settings() -> MarketRegimeSettings:
    mode = os.getenv(MARKET_REGIME_MODE_ENV, DEFAULT_MARKET_REGIME_MODE).strip().lower()
    return MarketRegimeSettings(mode=mode or DEFAULT_MARKET_REGIME_MODE)
