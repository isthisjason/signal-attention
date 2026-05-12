package com.signalattention.backtesting;

import java.math.BigDecimal;

public record BacktestMetricsResponse(
        Long backtestRunId,
        BigDecimal initialBalance,
        BigDecimal finalBalance,
        BigDecimal totalReturn,
        BigDecimal maxDrawdown,
        BigDecimal winRate,
        BigDecimal profitFactor,
        Integer tradeCount,
        BigDecimal averageTradeReturn,
        BigDecimal feeDrag,
        BigDecimal volatility,
        BigDecimal mlRiskScore,
        String mlRiskLabel
) {

    public static BacktestMetricsResponse from(BacktestRun run) {
        return new BacktestMetricsResponse(
                run.getId(),
                run.getInitialBalance(),
                run.getFinalBalance(),
                run.getTotalReturn(),
                run.getMaxDrawdown(),
                run.getWinRate(),
                run.getProfitFactor(),
                run.getTradeCount(),
                run.getAverageTradeReturn(),
                run.getFeeDrag(),
                run.getVolatility(),
                run.getMlRiskScore(),
                run.getMlRiskLabel()
        );
    }
}
