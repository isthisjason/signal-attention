from datetime import UTC, datetime, timedelta
from decimal import Decimal

from app.main import app
from app.routes.anomaly import predict_anomaly
from app.schemas.anomaly_schema import AnomalyRequest
from app.schemas.market_regime_schema import MarketRegimeCandle
from app.services.anomaly_service import anomaly_label, detect_anomaly


def test_anomaly_label_thresholds() -> None:
    assert anomaly_label(Decimal("0")) == "NORMAL"
    assert anomaly_label(Decimal("35")) == "WATCH"
    assert anomaly_label(Decimal("70")) == "ANOMALY"


def test_detect_anomaly_returns_normal_for_quiet_sequence() -> None:
    response = detect_anomaly(
        AnomalyRequest(symbol="BTC-USD", timeframe="1h", candles=candles([Decimal("100") for _ in range(20)]))
    )

    assert response.anomalyLabel == "NORMAL"
    assert response.anomalyScore == Decimal("0.00")


def test_detect_anomaly_flags_large_price_and_volume_move() -> None:
    closes = [Decimal("100") for _ in range(19)] + [Decimal("110")]
    volumes = [Decimal("1000") for _ in range(19)] + [Decimal("10000")]

    response = detect_anomaly(
        AnomalyRequest(symbol="BTC-USD", timeframe="1h", candles=candles(closes, volumes))
    )

    assert response.anomalyLabel == "ANOMALY"
    assert response.anomalyScore >= Decimal("70")
    assert response.reasons


def test_anomaly_route_is_registered() -> None:
    paths = {route.path for route in app.routes}

    assert "/predict/anomaly" in paths


def test_predict_anomaly_route_function() -> None:
    response = predict_anomaly(
        AnomalyRequest(symbol="BTC-USD", timeframe="1h", candles=candles([Decimal("100") for _ in range(20)]))
    )

    assert response.anomalyLabel == "NORMAL"


def candles(closes: list[Decimal], volumes: list[Decimal] | None = None) -> list[MarketRegimeCandle]:
    selected_volumes = volumes or [Decimal("1000") for _ in closes]
    return [
        MarketRegimeCandle(
            openTime=datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
            open=close,
            high=max(close, Decimal("100")) + Decimal("1"),
            low=min(close, Decimal("100")) - Decimal("1"),
            close=close,
            volume=selected_volumes[index],
        )
        for index, close in enumerate(closes)
    ]
