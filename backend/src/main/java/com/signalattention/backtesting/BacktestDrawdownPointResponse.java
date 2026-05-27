package com.signalattention.backtesting;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestDrawdownPointResponse(
        Instant timestamp,
        BigDecimal drawdownPercent
) {
}
