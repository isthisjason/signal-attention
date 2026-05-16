package com.signalattention.ml;

import java.math.BigDecimal;
import java.util.List;

public record MlMarketRegimeResponse(
        String regimeLabel,
        BigDecimal confidence,
        List<String> reasons,
        MlMarketRegimeFeatures features,
        String classifierSource
) {
}
