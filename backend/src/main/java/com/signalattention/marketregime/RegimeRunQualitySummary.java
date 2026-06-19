package com.signalattention.marketregime;

import java.math.BigDecimal;
import java.util.Map;

public record RegimeRunQualitySummary(
        BigDecimal averageConfidence,
        Integer lowConfidenceWindowCount,
        Integer baselineDisagreementCount,
        BigDecimal baselineDisagreementRate,
        Integer anomalyCount,
        String dominantRegimeLabel,
        Map<String, Integer> regimeCounts
) {
}
