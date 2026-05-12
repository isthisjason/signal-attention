package com.signalattention.backtesting;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record BacktestRequest(
        @NotNull Instant startDate,
        @NotNull Instant endDate,
        @DecimalMin(value = "0.00000001") BigDecimal initialBalance,
        @DecimalMin(value = "0.0") BigDecimal feePercent,
        @DecimalMin(value = "0.00000001") @DecimalMax(value = "100.0") BigDecimal positionSizePercent
) {
}
