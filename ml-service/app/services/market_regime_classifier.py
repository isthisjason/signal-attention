from typing import Protocol

from app.schemas.market_regime_schema import MarketRegimeRequest, MarketRegimeResponse


class MarketRegimeClassifier(Protocol):
    def classify(self, request: MarketRegimeRequest) -> MarketRegimeResponse:
        ...
