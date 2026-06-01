from decimal import Decimal, ROUND_HALF_UP

from app.schemas.market_regime_schema import MarketRegimeCandle, MarketRegimeFeatures


def build_market_regime_features(candles: list[MarketRegimeCandle]) -> MarketRegimeFeatures:
    closes = [candle.close for candle in candles]
    volumes = [candle.volume for candle in candles]
    # Features are deliberately simple so rule labels and torch inputs stay explainable.
    returns = [
        percent_change(previous, current)
        for previous, current in zip(closes, closes[1:])
    ]

    latest_return = returns[-1]
    average_return = mean(returns)
    volatility = standard_deviation(returns)
    trend_slope = percent_change(closes[0], closes[-1]) / Decimal(len(closes) - 1)
    sma = mean(closes)
    sma_distance = percent_change(sma, closes[-1])
    volume_z_score = z_score(volumes, volumes[-1])

    return MarketRegimeFeatures(
        latestReturnPercent=quantize(latest_return),
        averageReturnPercent=quantize(average_return),
        volatilityPercent=quantize(volatility),
        trendSlopePercent=quantize(trend_slope),
        smaDistancePercent=quantize(sma_distance),
        volumeZScore=quantize(volume_z_score),
    )


def percent_change(previous: Decimal, current: Decimal) -> Decimal:
    if previous == 0:
        return Decimal("0")
    return ((current - previous) / previous) * Decimal("100")


def mean(values: list[Decimal]) -> Decimal:
    if not values:
        return Decimal("0")
    return sum(values, Decimal("0")) / Decimal(len(values))


def standard_deviation(values: list[Decimal]) -> Decimal:
    if not values:
        return Decimal("0")
    average = mean(values)
    variance = mean([(value - average) ** 2 for value in values])
    return variance.sqrt()


def z_score(values: list[Decimal], latest: Decimal) -> Decimal:
    deviation = standard_deviation(values)
    if deviation == 0:
        return Decimal("0")
    return (latest - mean(values)) / deviation


def quantize(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
