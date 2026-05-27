package com.signalattention.backtesting;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestEquityPointResponse(
        Instant timestamp,
        BigDecimal equity
) {
}
