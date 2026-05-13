package com.signalattention.papertrading;

import java.math.BigDecimal;
import java.util.List;

public record PaperSessionSummaryResponse(
        Long paperSessionId,
        Long strategyId,
        PaperSessionStatus status,
        BigDecimal initialBalance,
        BigDecimal cashBalance,
        BigDecimal openPositionValue,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalEquity,
        boolean hasUnpricedPositions,
        List<PaperPositionMarkResponse> openPositions
) {
}
