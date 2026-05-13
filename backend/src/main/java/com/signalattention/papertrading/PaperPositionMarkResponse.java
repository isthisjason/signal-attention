package com.signalattention.papertrading;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperPositionMarkResponse(
        Long positionId,
        String symbol,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal markPrice,
        Instant markTime,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        boolean priced
) {
}
