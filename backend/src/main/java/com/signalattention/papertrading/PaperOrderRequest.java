package com.signalattention.papertrading;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaperOrderRequest(
        @NotNull PaperOrderSide side,
        @NotBlank String symbol,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal price
) {
}
