package com.signalattention.anomaly;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AnomalyCheckRequest(
        @NotBlank String symbol,
        @NotBlank String timeframe,
        @Min(20) @Max(500) Integer limit
) {
}
