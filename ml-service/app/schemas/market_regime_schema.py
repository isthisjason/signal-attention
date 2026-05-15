from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, Field, model_validator


MIN_MARKET_REGIME_CANDLES = 20


class MarketRegimeCandle(BaseModel):
    openTime: datetime
    open: Decimal = Field(gt=0)
    high: Decimal = Field(gt=0)
    low: Decimal = Field(gt=0)
    close: Decimal = Field(gt=0)
    volume: Decimal = Field(ge=0)

    @model_validator(mode="after")
    def validate_ohlc_bounds(self) -> "MarketRegimeCandle":
        if self.high < max(self.open, self.close) or self.low > min(self.open, self.close):
            raise ValueError("high and low must bound open and close")
        return self


class MarketRegimeRequest(BaseModel):
    symbol: str = Field(min_length=1)
    timeframe: str = Field(min_length=1)
    candles: list[MarketRegimeCandle] = Field(min_length=MIN_MARKET_REGIME_CANDLES)

    @model_validator(mode="after")
    def validate_candle_order(self) -> "MarketRegimeRequest":
        for previous, current in zip(self.candles, self.candles[1:]):
            if current.openTime <= previous.openTime:
                raise ValueError("candles must be ordered by openTime ascending")
        return self


class MarketRegimeFeatures(BaseModel):
    latestReturnPercent: Decimal
    averageReturnPercent: Decimal
    volatilityPercent: Decimal
    trendSlopePercent: Decimal
    smaDistancePercent: Decimal
    volumeZScore: Decimal


class MarketRegimeResponse(BaseModel):
    regimeLabel: str
    confidence: Decimal
    reasons: list[str]
    features: MarketRegimeFeatures
