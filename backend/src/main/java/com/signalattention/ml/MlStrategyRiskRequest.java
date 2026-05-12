package com.signalattention.ml;

import java.math.BigDecimal;

public record MlStrategyRiskRequest(
        BigDecimal totalReturn,
        BigDecimal maxDrawdown,
        BigDecimal winRate,
        BigDecimal profitFactor,
        Integer tradeCount,
        BigDecimal averageTradeReturn,
        BigDecimal feeDrag,
        BigDecimal volatility
) {
}
