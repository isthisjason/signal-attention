package com.signalattention.papertrading;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperOrderResponse(
        Long id,
        Long paperSessionId,
        PaperOrderSide side,
        PaperOrderStatus status,
        String symbol,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal notional,
        String rejectionReason,
        Instant createdAt
) {

    public static PaperOrderResponse from(PaperOrder order) {
        return new PaperOrderResponse(
                order.getId(),
                order.getPaperSession().getId(),
                order.getSide(),
                order.getStatus(),
                order.getSymbol(),
                order.getQuantity(),
                order.getPrice(),
                order.getNotional(),
                order.getRejectionReason(),
                order.getCreatedAt()
        );
    }
}
