from datetime import UTC, datetime, timedelta
from decimal import Decimal

from app.schemas.market_regime_schema import MarketRegimeCandle
from app.services.market_regime_features import build_market_regime_features


def candle(index: int, close: Decimal, volume: Decimal = Decimal("1000")) -> MarketRegimeCandle:
    return MarketRegimeCandle(
        openTime=datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
        open=close,
        high=close + Decimal("1"),
        low=close - Decimal("1"),
        close=close,
        volume=volume,
    )


def test_build_market_regime_features_for_uptrend() -> None:
    candles = [candle(index, Decimal("100") + Decimal(index)) for index in range(20)]

    features = build_market_regime_features(candles)

    assert features.latestReturnPercent > Decimal("0")
    assert features.averageReturnPercent > Decimal("0")
    assert features.trendSlopePercent > Decimal("0")
    assert features.smaDistancePercent > Decimal("0")


def test_build_market_regime_features_for_downtrend() -> None:
    candles = [candle(index, Decimal("120") - Decimal(index)) for index in range(20)]

    features = build_market_regime_features(candles)

    assert features.latestReturnPercent < Decimal("0")
    assert features.averageReturnPercent < Decimal("0")
    assert features.trendSlopePercent < Decimal("0")
    assert features.smaDistancePercent < Decimal("0")


def test_build_market_regime_features_handles_flat_volume() -> None:
    candles = [candle(index, Decimal("100")) for index in range(20)]

    features = build_market_regime_features(candles)

    assert features.volumeZScore == Decimal("0.00")
    assert features.volatilityPercent == Decimal("0.00")


def test_build_market_regime_features_calculates_volume_z_score() -> None:
    candles = [
        candle(index, Decimal("100") + Decimal(index), Decimal("1000"))
        for index in range(19)
    ] + [candle(19, Decimal("119"), Decimal("1500"))]

    features = build_market_regime_features(candles)

    assert features.volumeZScore > Decimal("0")
