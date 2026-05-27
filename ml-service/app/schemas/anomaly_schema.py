from decimal import Decimal

from pydantic import BaseModel

from app.schemas.market_regime_schema import MarketRegimeFeatures, MarketRegimeRequest


class AnomalyRequest(MarketRegimeRequest):
    pass


class AnomalyResponse(BaseModel):
    anomalyScore: Decimal
    anomalyLabel: str
    reasons: list[str]
    features: MarketRegimeFeatures
    classifierSource: str = "rules"
