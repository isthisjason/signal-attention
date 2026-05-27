package com.signalattention.marketregime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RegimeRunRequest(
        @NotBlank String symbol,
        @NotBlank String timeframe,
        @NotNull Instant startDate,
        @NotNull Instant endDate,
        @Min(20) @Max(500) Integer windowSize,
        @Min(1) @Max(100) Integer stride,
        Boolean includeAnomalies,
        Long backtestId
) {
}
