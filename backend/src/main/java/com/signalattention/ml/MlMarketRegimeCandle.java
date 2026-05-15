package com.signalattention.ml;

import java.math.BigDecimal;
import java.time.Instant;

public record MlMarketRegimeCandle(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {
}
