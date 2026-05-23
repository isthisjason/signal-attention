from decimal import Decimal, ROUND_HALF_UP

from app.schemas.market_regime_schema import MarketRegimeRequest, MarketRegimeResponse
from app.services.market_regime_classifier import MarketRegimeClassifier
from app.services.market_regime_config import MarketRegimeSettings, get_market_regime_settings
from app.services.market_regime_experiment import MARKET_REGIME_FEATURE_VERSION
from app.services.market_regime_features import build_market_regime_features
from app.services.market_regime_torch_adapter import TorchMarketRegimeClassifier


def classify_market_regime(request: MarketRegimeRequest) -> MarketRegimeResponse:
    return get_market_regime_classifier().classify(request)


def get_market_regime_classifier(settings: MarketRegimeSettings | None = None) -> MarketRegimeClassifier:
    selected_settings = settings or get_market_regime_settings()
    if selected_settings.mode == "rules":
        return RuleBasedMarketRegimeClassifier()
    if selected_settings.mode == "torch":
        return TorchMarketRegimeClassifier(selected_settings)
    raise ValueError(f"Unsupported market regime mode: {selected_settings.mode}")


class RuleBasedMarketRegimeClassifier:
    def classify(self, request: MarketRegimeRequest) -> MarketRegimeResponse:
        features = build_market_regime_features(request.candles)
        reasons: list[str] = []

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
