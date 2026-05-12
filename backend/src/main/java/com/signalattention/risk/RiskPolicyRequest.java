package com.signalattention.risk;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record RiskPolicyRequest(
        @NotNull @DecimalMin(value = "0.000001") @DecimalMax(value = "100.0") BigDecimal maxPositionSizePercent,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal stopLossPercent,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal takeProfitPercent,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal maxDailyLossPercent,
        @NotNull @PositiveOrZero Integer cooldownMinutes
) {
}
