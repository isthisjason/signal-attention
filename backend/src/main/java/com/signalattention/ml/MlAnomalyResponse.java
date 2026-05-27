package com.signalattention.ml;

import java.math.BigDecimal;
import java.util.List;

public record MlAnomalyResponse(
        BigDecimal anomalyScore,
        String anomalyLabel,
        List<String> reasons,
        MlMarketRegimeFeatures features,
        String classifierSource
) {
}
