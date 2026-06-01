from decimal import Decimal

from app.schemas.market_regime_schema import MarketRegimeCandle
from app.services.market_regime_features import mean, percent_change, z_score


TORCH_MARKET_REGIME_FEATURE_ORDER = [
    "open",
    "high",
    "low",
    "close",
    "volume",
    "returnPercent",
    "rangePercent",
    "bodyPercent",
    "smaDistancePercent",
    "volumeZScore",
]


def build_torch_feature_matrix(candles: list[MarketRegimeCandle]) -> list[list[float]]:
    closes: list[Decimal] = []
    volumes = [candle.volume for candle in candles]
    rows: list[list[float]] = []

    for index, candle in enumerate(candles):
        # Each row describes one candle plus its relationship to prior candles in the window.
        previous_close = candles[index - 1].close if index > 0 else candle.open
        closes.append(candle.close)
        sma = mean(closes)

        rows.append(
            [
                float(candle.open),
                float(candle.high),
                float(candle.low),
                float(candle.close),
                float(candle.volume),
                float(percent_change(previous_close, candle.close)),
                float(percent_change(candle.open, candle.high - candle.low + candle.open)),
                float(percent_change(candle.open, candle.close)),
                float(percent_change(sma, candle.close)),
                float(z_score(volumes[: index + 1], candle.volume)),
            ]
        )

    return rows
