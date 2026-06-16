package com.signalattention.ml;

import java.math.BigDecimal;

public record MlFeatureEvidence(
        String name,
        BigDecimal value,
        BigDecimal importance
) {
}
