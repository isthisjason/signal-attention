package com.signalattention.strategies;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StrategyCreateRequest(
        @NotBlank String name,
        @NotBlank String symbol,
        @NotBlank String timeframe,
        @NotNull StrategyType strategyType,
        @Valid @NotNull SmaCrossoverRulesRequest rules
) {
}
