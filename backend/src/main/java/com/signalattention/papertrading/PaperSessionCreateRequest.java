package com.signalattention.papertrading;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaperSessionCreateRequest(
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal initialBalance
) {
}
