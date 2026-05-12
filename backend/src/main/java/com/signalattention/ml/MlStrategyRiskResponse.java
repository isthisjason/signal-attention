package com.signalattention.ml;

import java.math.BigDecimal;
import java.util.List;

public record MlStrategyRiskResponse(
        BigDecimal riskScore,
        String riskLabel,
        List<String> reasons
) {
}
