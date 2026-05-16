from datetime import UTC, datetime, timedelta
from decimal import Decimal

from app.schemas.market_regime_schema import MarketRegimeCandle, MarketRegimeRequest
from app.services.market_regime_service import classify_market_regime


def candle(index: int, close: Decimal, volume: Decimal = Decimal("1000")) -> MarketRegimeCandle:
    return MarketRegimeCandle(
        openTime=datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
        open=close,
        high=close + Decimal("1"),
        low=close - Decimal("1"),
        close=close,
        volume=volume,
    )


def request_for(closes: list[Decimal], volumes: list[Decimal] | None = None) -> MarketRegimeRequest:
    candles = [
        candle(index, close, volumes[index] if volumes is not None else Decimal("1000"))
        for index, close in enumerate(closes)
    ]
    return MarketRegimeRequest(symbol="BTC-USD", timeframe="1h", candles=candles)


def test_classifies_trending_up() -> None:
    closes = [Decimal("100") + Decimal(index) for index in range(20)]

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "TRENDING_UP"
    assert response.confidence >= Decimal("60")
    assert response.reasons


def test_rule_classifier_output_is_stable() -> None:
    closes = [Decimal("100") + Decimal(index) for index in range(20)]

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "TRENDING_UP"
    assert response.confidence == Decimal("92.00")
    assert response.reasons == ["Price is rising and remains above its sequence average."]
    assert response.features.latestReturnPercent == Decimal("0.85")
    assert response.features.averageReturnPercent == Decimal("0.92")
    assert response.features.volatilityPercent == Decimal("0.05")
    assert response.features.trendSlopePercent == Decimal("1.00")
    assert response.features.smaDistancePercent == Decimal("8.68")
    assert response.features.volumeZScore == Decimal("0.00")


def test_classifies_trending_down() -> None:
    closes = [Decimal("120") - Decimal(index) for index in range(20)]

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "TRENDING_DOWN"
    assert response.confidence >= Decimal("60")


def test_classifies_sideways() -> None:
    closes = [Decimal("100"), Decimal("101")] * 10

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "SIDEWAYS"


def test_classifies_high_volatility() -> None:
    closes = [Decimal("100"), Decimal("110"), Decimal("96"), Decimal("115")] * 5

    response = classify_market_regime(request_for(closes))

    assert response.regimeLabel == "HIGH_VOLATILITY"
    assert response.confidence >= Decimal("70")


def test_mentions_unusual_volume() -> None:
    closes = [Decimal("100") + Decimal(index) for index in range(20)]
    volumes = [Decimal("1000") for _ in range(19)] + [Decimal("1500")]

    response = classify_market_regime(request_for(closes, volumes))

    assert any("volume" in reason for reason in response.reasons)
