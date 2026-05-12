package com.signalattention.risk;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record RiskEvaluationRequest(
        @NotNull Long strategyId,
        @NotBlank String symbol,
        @NotNull RiskOrderSide side,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal price,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal accountEquity,
        @DecimalMin(value = "0.0") BigDecimal currentDailyLoss,
        @DecimalMin(value = "0.0") BigDecimal openPositionEntryPrice,
        Instant lastRiskRejectionAt,
        Instant evaluatedAt
) {
}
