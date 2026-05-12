package com.signalattention.strategies;

import java.time.Instant;

public record StrategyResponse(
        Long id,
        String name,
        String symbol,
        String timeframe,
        StrategyType strategyType,
        SmaCrossoverRulesRequest rules,
        StrategyStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
