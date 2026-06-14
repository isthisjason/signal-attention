package com.signalattention.assistant;

import java.time.Instant;

public record AssistantContext(
        long strategyCount,
        long backtestCount,
        long runningPaperSessionCount,
        Long strategyId,
        Long backtestId,
        Long paperSessionId,
        Instant startDate,
        Instant endDate,
        String latestRegimeLabel,
        Integer latestRegimePointCount
) {
}
