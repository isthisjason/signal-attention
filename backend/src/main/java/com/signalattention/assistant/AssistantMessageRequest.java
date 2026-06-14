package com.signalattention.assistant;

import java.time.Instant;

public record AssistantMessageRequest(
        String prompt,
        Long strategyId,
        Long backtestId,
        Long paperSessionId,
        Instant startDate,
        Instant endDate
) {
}
