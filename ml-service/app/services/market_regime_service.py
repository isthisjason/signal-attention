from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

from app.schemas.market_regime_schema import (
    MarketRegimeRequest,
    MarketRegimeResponse,
    MarketRegimeStatusResponse,
    RegimeRunPoint,
    RegimeRunRequest,
    RegimeRunResponse,
)
from app.services.anomaly_service import detect_anomaly
from app.schemas.anomaly_schema import AnomalyRequest
from app.services.market_regime_classifier import MarketRegimeClassifier
from app.services.market_regime_config import AUTO_MARKET_REGIME_MODE, MarketRegimeSettings, get_market_regime_settings
from app.services.market_regime_experiment import MARKET_REGIME_FEATURE_VERSION, describe_path
from app.services.market_regime_features import build_market_regime_features
from app.services.market_regime_torch_adapter import TorchMarketRegimeClassifier, load_artifact, load_torch, validate_artifact_metadata


def classify_market_regime(request: MarketRegimeRequest) -> MarketRegimeResponse:
    # Mode selection stays behind the classifier interface so routes do not care about rules vs torch.
    return get_market_regime_classifier().classify(request)


def market_regime_status(settings: MarketRegimeSettings | None = None) -> MarketRegimeStatusResponse:
    selected_settings = settings or get_market_regime_settings()
    effective_settings, warnings = resolve_effective_settings(selected_settings)
    artifact_summary = describe_artifact(effective_settings.artifact_path)
    metadata = read_artifact_metadata(effective_settings.artifact_path, warnings)
    return MarketRegimeStatusResponse(
        mode=selected_settings.mode,
        effectiveMode=effective_settings.mode,
        classifierSource=effective_settings.mode,
        ready=effective_settings.mode == "rules" or artifact_summary["exists"],
        artifactConfigured=bool(selected_settings.artifact_path),
        artifactExists=artifact_summary["exists"],
        artifactIdentifier=artifact_summary["sha256"],
        modelVersion=metadata.get("modelVersion"),
        featureVersion=metadata.get("featureVersion", MARKET_REGIME_FEATURE_VERSION),
        sequenceLength=metadata.get("sequenceLength"),
        runId=metadata.get("runId"),
        artifactName=artifact_summary["name"],
        artifactPath=artifact_summary["path"],
        architecture=metadata.get("architecture", "transformer-v1") if metadata else None,
        labels=metadata.get("labels", []),
        modelConfig=metadata.get("model"),
        warnings=warnings,
    )


def run_market_regime(request: RegimeRunRequest) -> RegimeRunResponse:
    classifier = get_market_regime_classifier()
    baseline_classifier = RuleBasedMarketRegimeClassifier()
    points: list[RegimeRunPoint] = []
    for end_index in range(request.windowSize, len(request.candles) + 1, request.stride):
        # Each point classifies one rolling candle window ending at end_index.
        window = request.candles[end_index - request.windowSize : end_index]
        regime = classifier.classify(
            MarketRegimeRequest(symbol=request.symbol, timeframe=request.timeframe, candles=window)
        )
        baseline = baseline_classifier.classify(
            MarketRegimeRequest(symbol=request.symbol, timeframe=request.timeframe, candles=window)
        )
        anomaly_score = None
        anomaly_label = None
        anomaly_reasons = None
        if request.includeAnomalies:
            anomaly = detect_anomaly(
                AnomalyRequest(symbol=request.symbol, timeframe=request.timeframe, candles=window)
            )
            anomaly_score = anomaly.anomalyScore
            anomaly_label = anomaly.anomalyLabel
            anomaly_reasons = anomaly.reasons
        points.append(
            RegimeRunPoint(
                windowStart=window[0].openTime,
                windowEnd=window[-1].openTime,
                regimeLabel=regime.regimeLabel,
                confidence=regime.confidence,
                reasons=regime.reasons,
                features=regime.features,
                anomalyScore=anomaly_score,
                anomalyLabel=anomaly_label,
                anomalyReasons=anomaly_reasons,
                baselineRegimeLabel=baseline.regimeLabel,
                baselineConfidence=baseline.confidence,
                disagreesWithBaseline=regime.regimeLabel != baseline.regimeLabel,
            )
        )

    return RegimeRunResponse(
        symbol=request.symbol,
        timeframe=request.timeframe,
        windowSize=request.windowSize,
        stride=request.stride,
        includeAnomalies=request.includeAnomalies,
        pointCount=len(points),
        points=points,
    )


def get_market_regime_classifier(settings: MarketRegimeSettings | None = None) -> MarketRegimeClassifier:
    selected_settings = settings or get_market_regime_settings()
    selected_settings, _warnings = resolve_effective_settings(selected_settings)
    # Rules are the default path; torch is opt-in because it needs an artifact and extra deps.
    if selected_settings.mode == "rules":
        return RuleBasedMarketRegimeClassifier()
    if selected_settings.mode == "torch":
        return TorchMarketRegimeClassifier(selected_settings)
    raise ValueError(f"Unsupported market regime mode: {selected_settings.mode}")


def resolve_effective_settings(settings: MarketRegimeSettings) -> tuple[MarketRegimeSettings, list[str]]:
    warnings: list[str] = []
    if settings.mode != AUTO_MARKET_REGIME_MODE:
        return settings, warnings

    artifact_path = settings.artifact_path
    # Auto mode keeps demos reproducible by preferring torch only when a local artifact is actually available.
    if artifact_path and Path(artifact_path).is_file():
        return MarketRegimeSettings(mode="torch", artifact_path=artifact_path), warnings

    warnings.append("auto mode fell back to rules because no loadable torch artifact was configured")
    return MarketRegimeSettings(mode="rules", artifact_path=artifact_path), warnings


def describe_artifact(artifact_path: str | None) -> dict:
    if not artifact_path:
        return {"exists": False, "sha256": None, "name": None, "path": None}
    summary = describe_path(Path(artifact_path))
    return {
        "exists": summary["sizeBytes"] is not None,
        "sha256": summary["sha256"],
        "name": summary["name"],
        "path": summary["path"],
    }


def read_artifact_metadata(artifact_path: str | None, warnings: list[str]) -> dict:
    if not artifact_path or not Path(artifact_path).is_file():
        return {}
    try:
        torch = load_torch()
        artifact = load_artifact(torch, Path(artifact_path), torch.device("cpu"))
        # Reuse inference validation so status reports the same artifact contract as prediction.
        return validate_artifact_metadata(artifact)
    except RuntimeError as exc:
        warnings.append(f"artifact metadata unavailable: {exc}")
        return {}


class RuleBasedMarketRegimeClassifier:
    def classify(self, request: MarketRegimeRequest) -> MarketRegimeResponse:
        features = build_market_regime_features(request.candles)
        reasons: list[str] = []

        # The order matters: high volatility is called out before trend labels.
        if features.volatilityPercent >= Decimal("4.00"):
            label = "HIGH_VOLATILITY"
            confidence = min(Decimal("95"), Decimal("70") + (features.volatilityPercent * Decimal("4")))
            reasons.append("Recent returns show elevated volatility.")
        elif features.trendSlopePercent >= Decimal("0.15") and features.smaDistancePercent >= Decimal("1.00"):
            label = "TRENDING_UP"
            confidence = trend_confidence(features.trendSlopePercent, features.smaDistancePercent)
            reasons.append("Price is rising and remains above its sequence average.")
        elif features.trendSlopePercent <= Decimal("-0.15") and features.smaDistancePercent <= Decimal("-1.00"):
            label = "TRENDING_DOWN"
            confidence = trend_confidence(abs(features.trendSlopePercent), abs(features.smaDistancePercent))
            reasons.append("Price is falling and remains below its sequence average.")
        else:
            label = "SIDEWAYS"
            confidence = Decimal("65")
            reasons.append("Trend and volatility signals are muted.")

        if abs(features.volumeZScore) >= Decimal("2.00"):
            reasons.append("Latest volume is unusually far from the sequence average.")

        # Rules responses include provenance fields so the backend can display which classifier path ran.
        return MarketRegimeResponse(
            regimeLabel=label,
            confidence=quantize_confidence(confidence),
            reasons=reasons,
            features=features,
            classifierSource="rules",
            mode="rules",
            featureVersion=MARKET_REGIME_FEATURE_VERSION,
            sequenceLength=len(request.candles),
        )

def trend_confidence(trend_slope: Decimal, sma_distance: Decimal) -> Decimal:
    return min(
        Decimal("92"),
        Decimal("62") + (trend_slope * Decimal("20")) + (sma_distance * Decimal("3")),
    )


def quantize_confidence(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
