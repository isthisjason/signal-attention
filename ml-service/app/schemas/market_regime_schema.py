from datetime import datetime
from decimal import Decimal
from typing import Any

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


class AttentionTimestepEvidence(BaseModel):
    openTime: datetime
    attentionScore: Decimal
    close: Decimal
    returnPercent: Decimal


class FeatureEvidence(BaseModel):
    name: str
    value: Decimal
    importance: Decimal


class MarketRegimeDiagnosticsResponse(BaseModel):
    symbol: str
    timeframe: str
    windowStart: datetime
    windowEnd: datetime
    regimeLabel: str
    confidence: Decimal
    baselineRegimeLabel: str
    baselineConfidence: Decimal
    disagreesWithBaseline: bool
    evidenceSource: str
    reasons: list[str]
    topTimesteps: list[AttentionTimestepEvidence]
    featureEvidence: list[FeatureEvidence]
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
    labels: list[str] = Field(default_factory=list)
    modelConfig: dict | None = None
    promotionStatus: str | None = None
    promotedRunId: str | None = None
    promotionGeneratedAt: str | None = None
    promotionArtifactMatches: bool | None = None
    promotionWarnings: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


class PromotionGateDiagnostics(BaseModel):
    eligible: bool = False
    failures: list[str] = Field(default_factory=list)
    gates: dict[str, Any] = Field(default_factory=dict)


class WeakLabelDiagnostics(BaseModel):
    label: str | None = None
    f1: float | None = None
    recall: float | None = None
    precision: float | None = None
    support: int | None = None


class ConfusionPairDiagnostics(BaseModel):
    expected: str | None = None
    predicted: str | None = None
    count: int | None = None


class MarketRegimeExperimentRunDiagnostics(BaseModel):
    name: str | None = None
    runId: str | None = None
    hasTraining: bool = False
    hasEvaluation: bool = False
    validationAccuracy: float | None = None
    validationMacroF1: float | None = None
    accuracy: float | None = None
    macroF1: float | None = None
    balancedAccuracy: float | None = None
    baselineAccuracy: float | None = None
    liftOverBaseline: float | None = None
    confidence: dict[str, Any] | None = None
    labelDistribution: dict[str, Any] | None = None
    windowRanges: dict[str, Any] | None = None
    holdoutSource: str | None = None
    promotionGate: PromotionGateDiagnostics = Field(default_factory=PromotionGateDiagnostics)
    weakestLabels: list[WeakLabelDiagnostics] = Field(default_factory=list)
    confusionPairs: list[ConfusionPairDiagnostics] = Field(default_factory=list)
    reportPath: str | None = None


class MarketRegimeExperimentSummary(BaseModel):
    totalRuns: int = 0
    trainedRuns: int = 0
    evaluatedRuns: int = 0
    promotionEligibleRuns: int = 0
    bestRun: MarketRegimeExperimentRunDiagnostics | None = None


class MarketRegimeExperimentDiagnosticsResponse(BaseModel):
    summary: MarketRegimeExperimentSummary
    runs: list[MarketRegimeExperimentRunDiagnostics] = Field(default_factory=list)
    incompleteRuns: list[MarketRegimeExperimentRunDiagnostics] = Field(default_factory=list)
    promotion: dict[str, Any] | None = None
    warnings: list[str] = Field(default_factory=list)


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
