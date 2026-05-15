package com.signalattention.ml;

import java.math.BigDecimal;

public record MlMarketRegimeFeatures(
        BigDecimal latestReturnPercent,
        BigDecimal averageReturnPercent,
        BigDecimal volatilityPercent,
        BigDecimal trendSlopePercent,
        BigDecimal smaDistancePercent,
        BigDecimal volumeZScore
) {
}
