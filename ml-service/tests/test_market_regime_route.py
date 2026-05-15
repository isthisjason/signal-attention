from datetime import UTC, datetime, timedelta
from decimal import Decimal

from app.main import app
from app.routes.market_regime import MarketRegimeRequest, predict_market_regime


def candle(index: int, close: Decimal) -> dict[str, object]:
    return {
        "openTime": datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
        "open": close,
        "high": close + Decimal("1"),
        "low": close - Decimal("1"),
        "close": close,
        "volume": Decimal("1000"),
    }


def test_market_regime_route_is_registered() -> None:
    paths = {route.path for route in app.routes}

    assert "/predict/market-regime" in paths


def test_predict_market_regime_route_function() -> None:
    request = MarketRegimeRequest(
        symbol="BTC-USD",
        timeframe="1h",
        candles=[candle(index, Decimal("100") + Decimal(index)) for index in range(20)],
    )

    response = predict_market_regime(request)

    assert response.regimeLabel == "TRENDING_UP"
    assert response.confidence > Decimal("0")
    assert response.reasons
