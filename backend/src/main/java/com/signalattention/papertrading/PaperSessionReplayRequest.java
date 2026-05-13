package com.signalattention.papertrading;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PaperSessionReplayRequest(
        @NotNull Instant startDate,
        @NotNull Instant endDate,
        @Min(1) @Max(1000) Integer maxCandles
) {
}
