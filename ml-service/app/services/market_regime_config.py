import os
from dataclasses import dataclass


DEFAULT_MARKET_REGIME_MODE = "rules"
AUTO_MARKET_REGIME_MODE = "auto"
DEFAULT_MARKET_REGIME_PROMOTION_PATH = "models/experiments/promoted-market-regime.json"
MARKET_REGIME_ARTIFACT_PATH_ENV = "MARKET_REGIME_ARTIFACT_PATH"
MARKET_REGIME_MODE_ENV = "MARKET_REGIME_MODE"
MARKET_REGIME_PROMOTION_PATH_ENV = "MARKET_REGIME_PROMOTION_PATH"


@dataclass(frozen=True)
class MarketRegimeSettings:
    mode: str = DEFAULT_MARKET_REGIME_MODE
    artifact_path: str | None = None
    promotion_path: str | None = DEFAULT_MARKET_REGIME_PROMOTION_PATH


def get_market_regime_settings() -> MarketRegimeSettings:
    mode = os.getenv(MARKET_REGIME_MODE_ENV, DEFAULT_MARKET_REGIME_MODE).strip().lower()
    artifact_path = os.getenv(MARKET_REGIME_ARTIFACT_PATH_ENV)
    if artifact_path is not None:
        artifact_path = artifact_path.strip() or None
    promotion_path = os.getenv(MARKET_REGIME_PROMOTION_PATH_ENV, DEFAULT_MARKET_REGIME_PROMOTION_PATH)
    if promotion_path is not None:
        promotion_path = promotion_path.strip() or None
    return MarketRegimeSettings(
        mode=mode or DEFAULT_MARKET_REGIME_MODE,
        artifact_path=artifact_path,
        promotion_path=promotion_path,
    )
