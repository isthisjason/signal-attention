package com.signalattention.papertrading;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperPositionResponse(
        Long id,
        Long paperSessionId,
        PaperPositionStatus status,
        String symbol,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        Instant openedAt,
        Instant closedAt
) {

    public static PaperPositionResponse from(PaperPosition position) {
        return new PaperPositionResponse(
                position.getId(),
                position.getPaperSession().getId(),
                position.getStatus(),
                position.getSymbol(),
                position.getQuantity(),
                position.getEntryPrice(),
                position.getExitPrice(),
                position.getOpenedAt(),
                position.getClosedAt()
        );
    }
}
