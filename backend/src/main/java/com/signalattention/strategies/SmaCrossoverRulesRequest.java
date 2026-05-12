package com.signalattention.strategies;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SmaCrossoverRulesRequest(
        @NotNull @Positive Integer shortWindow,
        @NotNull @Positive Integer longWindow,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal initialBalance,
        @NotNull @DecimalMin(value = "0.0") BigDecimal feePercent,
        @NotNull @DecimalMin(value = "0.00000001") @DecimalMax(value = "100.0") BigDecimal positionSizePercent
) {

    @AssertTrue(message = "shortWindow must be less than longWindow")
    public boolean isWindowOrderValid() {
        if (shortWindow == null || longWindow == null) {
            return true;
        }
        return shortWindow < longWindow;
    }
}
