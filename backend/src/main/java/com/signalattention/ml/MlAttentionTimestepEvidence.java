package com.signalattention.ml;

import java.math.BigDecimal;
import java.time.Instant;

public record MlAttentionTimestepEvidence(
        Instant openTime,
        BigDecimal attentionScore,
        BigDecimal close,
        BigDecimal returnPercent
) {
}
