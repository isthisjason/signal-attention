from decimal import Decimal, ROUND_HALF_UP

from app.schemas.anomaly_schema import AnomalyRequest, AnomalyResponse
from app.services.market_regime_features import build_market_regime_features


def detect_anomaly(request: AnomalyRequest) -> AnomalyResponse:
    features = build_market_regime_features(request.candles)
    # Anomaly score is additive so the reasons explain exactly what looked unusual.
    score = Decimal("0")
    reasons: list[str] = []

    # Latest return catches sudden candle moves even when the whole window is not volatile yet.
    latest_return = abs(features.latestReturnPercent)
    if latest_return >= Decimal("3.00"):
        score += Decimal("35")
        reasons.append("Latest return is much larger than the recent sequence usually shows.")
    elif latest_return >= Decimal("1.50"):
        score += Decimal("18")
        reasons.append("Latest return is outside the quiet range.")

    if features.volatilityPercent >= Decimal("4.00"):
        score += Decimal("30")
        reasons.append("Recent volatility is elevated.")
    elif features.volatilityPercent >= Decimal("2.00"):
        score += Decimal("15")
        reasons.append("Recent volatility is rising.")

    volume_z_score = abs(features.volumeZScore)
    if volume_z_score >= Decimal("3.00"):
        score += Decimal("25")
        reasons.append("Latest volume is far away from the recent average.")
    elif volume_z_score >= Decimal("2.00"):
        score += Decimal("12")
        reasons.append("Latest volume is noticeably away from the recent average.")

    if abs(features.smaDistancePercent) >= Decimal("4.00"):
        score += Decimal("10")
        reasons.append("Price is stretched away from its sequence average.")

    score = min(score, Decimal("100")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    label = anomaly_label(score)
    if not reasons:
        # Normal still gets a reason so API consumers are not left with an empty explanation.
        reasons.append("Recent price, volatility, and volume look normal for this sequence.")

    return AnomalyResponse(
        anomalyScore=score,
        anomalyLabel=label,
        reasons=reasons,
        features=features,
    )


def anomaly_label(score: Decimal) -> str:
    # Labels are coarse on purpose; the numeric score and reasons carry the detail.
    if score >= Decimal("70"):
        return "ANOMALY"
    if score >= Decimal("35"):
        return "WATCH"
    return "NORMAL"
