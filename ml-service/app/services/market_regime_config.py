import os
from dataclasses import dataclass


DEFAULT_MARKET_REGIME_MODE = "rules"
AUTO_MARKET_REGIME_MODE = "auto"
MARKET_REGIME_ARTIFACT_PATH_ENV = "MARKET_REGIME_ARTIFACT_PATH"
MARKET_REGIME_MODE_ENV = "MARKET_REGIME_MODE"


@dataclass(frozen=True)
class MarketRegimeSettings:
    mode: str = DEFAULT_MARKET_REGIME_MODE
    artifact_path: str | None = None


def get_market_regime_settings() -> MarketRegimeSettings:
    mode = os.getenv(MARKET_REGIME_MODE_ENV, DEFAULT_MARKET_REGIME_MODE).strip().lower()
    artifact_path = os.getenv(MARKET_REGIME_ARTIFACT_PATH_ENV)
    if artifact_path is not None:
        artifact_path = artifact_path.strip() or None
    return MarketRegimeSettings(mode=mode or DEFAULT_MARKET_REGIME_MODE, artifact_path=artifact_path)
