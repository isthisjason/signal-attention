package com.signalattention.papertrading;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperSessionResponse(
        Long id,
        Long strategyId,
        PaperSessionStatus status,
        BigDecimal initialBalance,
        BigDecimal cashBalance,
        Instant createdAt,
        Instant startedAt,
        Instant stoppedAt
) {

    public static PaperSessionResponse from(PaperSession session) {
        return new PaperSessionResponse(
                session.getId(),
                session.getStrategy().getId(),
                session.getStatus(),
                session.getInitialBalance(),
                session.getCashBalance(),
                session.getCreatedAt(),
                session.getStartedAt(),
                session.getStoppedAt()
        );
    }
}
