from decimal import Decimal, ROUND_HALF_UP
import json
from pathlib import Path
from typing import Any

from app.schemas.market_regime_schema import (
    AttentionTimestepEvidence,
    FeatureEvidence,
    MarketRegimeDiagnosticsResponse,
    MarketRegimeExperimentDiagnosticsResponse,
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
from app.services.market_regime_diagnostics import build_experiment_diagnostics
from app.services.market_regime_experiment import MARKET_REGIME_FEATURE_VERSION, describe_path, load_experiment_registry
from app.services.market_regime_features import build_market_regime_features
from app.services.market_regime_torch_adapter import TorchMarketRegimeClassifier, load_artifact, load_torch, validate_artifact_metadata


def classify_market_regime(request: MarketRegimeRequest) -> MarketRegimeResponse:
    # Mode selection stays behind the classifier interface so routes do not care about rules vs torch.
    return get_market_regime_classifier().classify(request)


def diagnose_market_regime(request: MarketRegimeRequest) -> MarketRegimeDiagnosticsResponse:
    regime = classify_market_regime(request)
    baseline = RuleBasedMarketRegimeClassifier().classify(request)
    feature_evidence = rank_feature_evidence(regime.features)
    top_timesteps = rank_timestep_evidence(request)
    evidence_source = "attention" if regime.classifierSource == "torch" and regime.mode == "torch" else "attribution"
    reasons = [
        *regime.reasons,
        "Evidence ranks recent sequence points and feature magnitudes; v1 artifacts use deterministic attribution fallback.",
    ]
    return MarketRegimeDiagnosticsResponse(
        symbol=request.symbol,
        timeframe=request.timeframe,
        windowStart=request.candles[0].openTime,
        windowEnd=request.candles[-1].openTime,
        regimeLabel=regime.regimeLabel,
        confidence=regime.confidence,
        baselineRegimeLabel=baseline.regimeLabel,
        baselineConfidence=baseline.confidence,
        disagreesWithBaseline=regime.regimeLabel != baseline.regimeLabel,
        evidenceSource=evidence_source,
        reasons=reasons,
        topTimesteps=top_timesteps,
        featureEvidence=feature_evidence,
        classifierSource=regime.classifierSource,
        mode=regime.mode,
        modelVersion=regime.modelVersion,
        featureVersion=regime.featureVersion,
        sequenceLength=regime.sequenceLength,
        artifactIdentifier=regime.artifactIdentifier,
    )


def market_regime_status(settings: MarketRegimeSettings | None = None) -> MarketRegimeStatusResponse:
    selected_settings = settings or get_market_regime_settings()
    effective_settings, warnings = resolve_effective_settings(selected_settings)
    artifact_summary = describe_artifact(effective_settings.artifact_path)
    metadata = read_artifact_metadata(effective_settings.artifact_path, warnings)
    promotion_summary = inspect_promotion_summary(selected_settings.promotion_path, warnings)
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
        promotionStatus=promotion_summary["status"],
        promotedRunId=promotion_summary["runId"],
        promotionGeneratedAt=promotion_summary["generatedAt"],
        promotionArtifactMatches=promotion_summary["artifactMatches"],
        promotionWarnings=promotion_summary["warnings"],
        warnings=warnings,
    )


def market_regime_experiment_diagnostics(
    settings: MarketRegimeSettings | None = None,
) -> MarketRegimeExperimentDiagnosticsResponse:
    selected_settings = settings or get_market_regime_settings()
    warnings: list[str] = []
    experiments_dir = Path(selected_settings.experiments_dir or "models/experiments")
    registry_path = experiments_dir / "index.json"
    try:
        diagnostics = build_experiment_diagnostics(load_experiment_registry(registry_path))
    except json.JSONDecodeError:
        # A malformed local registry should be visible to the workbench without breaking model status.
        warnings.append("experiment registry is not valid JSON")
        diagnostics = build_experiment_diagnostics({"experiments": []})

    promotion_path = Path(selected_settings.promotion_path) if selected_settings.promotion_path else experiments_dir / "promoted-market-regime.json"
    promotion = read_promotion_summary(promotion_path, warnings)
    return MarketRegimeExperimentDiagnosticsResponse(
        summary=diagnostics["summary"],
        runs=diagnostics["runs"],
        incompleteRuns=diagnostics["incompleteRuns"],
        promotion=promotion,
        warnings=warnings,
    )


def read_promotion_summary(path: Path, warnings: list[str]) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        warnings.append("promotion summary is not valid JSON")
        return None


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


def rank_timestep_evidence(request: MarketRegimeRequest) -> list[AttentionTimestepEvidence]:
    scored = []
    previous_close = request.candles[0].close
    for index, candle in enumerate(request.candles):
        if index == 0:
            return_percent = Decimal("0")
        else:
            return_percent = ((candle.close - previous_close) / previous_close * Decimal("100")).quantize(Decimal("0.01"))
        # The fallback evidence favors recent candles with larger movement because v1 artifacts
        # cannot expose internal attention weights after inference.
        recency_weight = Decimal(index + 1) / Decimal(len(request.candles))
        movement_weight = abs(return_percent) + Decimal("1")
        score = (recency_weight * movement_weight).quantize(Decimal("0.01"))
        scored.append((score, candle, return_percent))
        previous_close = candle.close
    return [
        AttentionTimestepEvidence(
            openTime=candle.openTime,
            attentionScore=score,
            close=candle.close,
            returnPercent=return_percent,
        )
        for score, candle, return_percent in sorted(scored, key=lambda item: item[0], reverse=True)[:5]
    ]


def rank_feature_evidence(features) -> list[FeatureEvidence]:
    values = {
        "latestReturnPercent": features.latestReturnPercent,
        "averageReturnPercent": features.averageReturnPercent,
        "volatilityPercent": features.volatilityPercent,
        "trendSlopePercent": features.trendSlopePercent,
        "smaDistancePercent": features.smaDistancePercent,
        "volumeZScore": features.volumeZScore,
    }
    ranked = sorted(values.items(), key=lambda item: abs(item[1]), reverse=True)
    return [
        FeatureEvidence(name=name, value=value, importance=abs(value).quantize(Decimal("0.01")))
        for name, value in ranked[:6]
    ]


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
    # Explicit artifact configuration wins because it is the most local, reviewable operator choice.
    if artifact_path and Path(artifact_path).is_file():
        return MarketRegimeSettings(mode="torch", artifact_path=artifact_path), warnings

    promoted_artifact_path = promoted_artifact_path_from_summary(settings.promotion_path, warnings)
    if promoted_artifact_path and Path(promoted_artifact_path).is_file():
        return MarketRegimeSettings(
            mode="torch",
            artifact_path=promoted_artifact_path,
            promotion_path=settings.promotion_path,
        ), warnings

    warnings.append("auto mode fell back to rules because no loadable torch artifact was configured")
    return MarketRegimeSettings(mode="rules", artifact_path=artifact_path), warnings


def promoted_artifact_path_from_summary(promotion_path: str | None, warnings: list[str]) -> str | None:
    if not promotion_path:
        return None
    path = Path(promotion_path)
    if not path.is_file():
        return None
    try:
        summary = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        warnings.append("promotion summary is not valid JSON")
        return None

    if summary.get("status") != "promoted":
        warnings.append("promotion summary does not contain a promoted artifact")
        return None
    artifact_check = summary.get("artifactCheck")
    if not isinstance(artifact_check, dict) or artifact_check.get("matchesRecordedHash") is not True:
        warnings.append("promotion summary artifact check is not verified")
        return None

    artifact_path = promoted_artifact_path(summary, artifact_check)
    if artifact_path is None:
        warnings.append("promotion summary artifact path is missing")
    return artifact_path


def promoted_artifact_path(summary: dict[str, Any], artifact_check: dict[str, Any]) -> str | None:
    runnable = summary.get("runnableManifest")
    if isinstance(runnable, dict) and isinstance(runnable.get("artifactPath"), str):
        return runnable["artifactPath"]
    artifact_path = artifact_check.get("path")
    return artifact_path if isinstance(artifact_path, str) else None


def inspect_promotion_summary(promotion_path: str | None, warnings: list[str]) -> dict[str, Any]:
    empty = {
        "status": None,
        "runId": None,
        "generatedAt": None,
        "artifactMatches": None,
        "warnings": [],
    }
    if not promotion_path or not Path(promotion_path).is_file():
        return empty
    try:
        summary = json.loads(Path(promotion_path).read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        if "promotion summary is not valid JSON" not in warnings:
            warnings.append("promotion summary is not valid JSON")
        return {**empty, "warnings": ["promotion summary is not valid JSON"]}

    selected = summary.get("selectedRun") if isinstance(summary, dict) else None
    artifact_check = summary.get("artifactCheck") if isinstance(summary, dict) else None
    promotion_warnings: list[str] = []
    if isinstance(artifact_check, dict) and artifact_check.get("failure"):
        promotion_warnings.append(str(artifact_check["failure"]))

    return {
        "status": summary.get("status") if isinstance(summary.get("status"), str) else None,
        "runId": selected.get("runId") if isinstance(selected, dict) and isinstance(selected.get("runId"), str) else None,
        "generatedAt": summary.get("generatedAt") if isinstance(summary.get("generatedAt"), str) else None,
        "artifactMatches": artifact_check.get("matchesRecordedHash") if isinstance(artifact_check, dict) else None,
        "warnings": promotion_warnings,
    }


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
