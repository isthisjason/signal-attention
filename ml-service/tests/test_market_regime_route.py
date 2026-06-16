from datetime import UTC, datetime, timedelta
from decimal import Decimal

from app.main import app
from app.routes.market_regime import (
    get_market_regime_status,
    diagnose_market_regime_window,
    MarketRegimeRequest,
    RegimeRunRequest,
    predict_market_regime,
    predict_market_regime_run,
)


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
    assert "/predict/market-regime/diagnostics" in paths
    assert "/predict/market-regime/status" in paths


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


def test_market_regime_status_route_function() -> None:
    response = get_market_regime_status()

    assert response.ready is True
    assert response.effectiveMode in {"rules", "torch"}


def test_market_regime_diagnostics_route_function() -> None:
    request = MarketRegimeRequest(
        symbol="BTC-USD",
        timeframe="1h",
        candles=[candle(index, Decimal("100") + Decimal(index)) for index in range(20)],
    )

    response = diagnose_market_regime_window(request)

    assert response.regimeLabel == "TRENDING_UP"
    assert response.baselineRegimeLabel == "TRENDING_UP"
    assert response.topTimesteps
    assert response.featureEvidence


def test_predict_market_regime_run_route_function() -> None:
    candles = [candle(index, Decimal("100") + Decimal(index)) for index in range(30)]
    request = RegimeRunRequest(
        symbol="BTC-USD",
        timeframe="1h",
        candles=candles,
        windowSize=20,
        stride=5,
        includeAnomalies=True,
    )

    response = predict_market_regime_run(request)

    assert response.pointCount == 3
    assert response.points[0].regimeLabel == "TRENDING_UP"
    assert response.points[0].baselineRegimeLabel == "TRENDING_UP"
    assert response.points[0].anomalyLabel is not None
