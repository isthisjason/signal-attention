from datetime import UTC, datetime, timedelta
from decimal import Decimal

import pytest
from pydantic import ValidationError

from app.schemas.market_regime_schema import MarketRegimeRequest


def candle(index: int, close: Decimal = Decimal("100")) -> dict[str, object]:
    return {
        "openTime": datetime(2024, 1, 1, tzinfo=UTC) + timedelta(hours=index),
        "open": close,
        "high": close + Decimal("1"),
        "low": close - Decimal("1"),
        "close": close,
        "volume": Decimal("1000"),
    }


def request_payload(**overrides: object) -> dict[str, object]:
    payload = {
        "symbol": "BTC-USD",
        "timeframe": "1h",
        "candles": [candle(index) for index in range(20)],
    }
    payload.update(overrides)
    return payload


def test_market_regime_request_accepts_ordered_candles() -> None:
    request = MarketRegimeRequest(**request_payload())

    assert request.symbol == "BTC-USD"
    assert len(request.candles) == 20


def test_market_regime_request_rejects_short_sequence() -> None:
    with pytest.raises(ValidationError):
        MarketRegimeRequest(**request_payload(candles=[candle(index) for index in range(19)]))


def test_market_regime_request_rejects_unordered_candles() -> None:
    candles = [candle(index) for index in range(20)]
    candles[10], candles[11] = candles[11], candles[10]

    with pytest.raises(ValidationError):
        MarketRegimeRequest(**request_payload(candles=candles))


def test_market_regime_request_rejects_invalid_ohlc_bounds() -> None:
    invalid = candle(0)
    invalid["high"] = Decimal("99")
    candles = [invalid] + [candle(index) for index in range(1, 20)]

    with pytest.raises(ValidationError):
        MarketRegimeRequest(**request_payload(candles=candles))
