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
    classifierSource: str | None = None
    mode: str | None = None
    modelVersion: str | None = None
    featureVersion: str | None = None
    sequenceLength: int | None = None
    artifactIdentifier: str | None = None


class MarketRegimeStatusResponse(BaseModel):
    mode: str
    effectiveMode: str
    classifierSource: str
    ready: bool
    artifactConfigured: bool
    artifactExists: bool
    artifactIdentifier: str | None = None
    modelVersion: str | None = None
    featureVersion: str | None = None
    sequenceLength: int | None = None
    runId: str | None = None
    artifactName: str | None = None
    artifactPath: str | None = None
    architecture: str | None = None
    labels: list[str] = []
    modelConfig: dict | None = None
    warnings: list[str] = []


class RegimeRunRequest(BaseModel):
    symbol: str = Field(min_length=1)
    timeframe: str = Field(min_length=1)
    candles: list[MarketRegimeCandle] = Field(min_length=MIN_MARKET_REGIME_CANDLES)
    windowSize: int = Field(ge=MIN_MARKET_REGIME_CANDLES, le=500)
    stride: int = Field(ge=1, le=100)
    includeAnomalies: bool = True

    @model_validator(mode="after")
    def validate_window_inputs(self) -> "RegimeRunRequest":
        for previous, current in zip(self.candles, self.candles[1:]):
            if current.openTime <= previous.openTime:
                raise ValueError("candles must be ordered by openTime ascending")
        if self.windowSize > len(self.candles):
            raise ValueError("windowSize must be less than or equal to candle count")
        return self


class RegimeRunPoint(BaseModel):
    windowStart: datetime
    windowEnd: datetime
    regimeLabel: str
    confidence: Decimal
    reasons: list[str]
    features: MarketRegimeFeatures
    anomalyScore: Decimal | None = None
    anomalyLabel: str | None = None
    anomalyReasons: list[str] | None = None
    baselineRegimeLabel: str | None = None
    baselineConfidence: Decimal | None = None
    disagreesWithBaseline: bool | None = None


class RegimeRunResponse(BaseModel):
    symbol: str
    timeframe: str
    windowSize: int
    stride: int
    includeAnomalies: bool
    pointCount: int
    points: list[RegimeRunPoint]
