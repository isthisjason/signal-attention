package com.signalattention.ml;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MlRegimeRunPoint(
        Instant windowStart,
        Instant windowEnd,
        String regimeLabel,
        BigDecimal confidence,
        List<String> reasons,
        MlMarketRegimeFeatures features,
        BigDecimal anomalyScore,
        String anomalyLabel,
        List<String> anomalyReasons
) {
}
