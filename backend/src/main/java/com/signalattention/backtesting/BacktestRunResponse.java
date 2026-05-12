package com.signalattention.backtesting;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestRunResponse(
        Long id,
        Long strategyId,
        Instant startDate,
        Instant endDate,
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
        String mlRiskLabel,
        BacktestStatus status,
        Instant createdAt,
        Instant completedAt
) {

    public static BacktestRunResponse from(BacktestRun run) {
        return new BacktestRunResponse(
                run.getId(),
                run.getStrategy().getId(),
                run.getStartDate(),
                run.getEndDate(),
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
                run.getMlRiskLabel(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getCompletedAt()
        );
    }
}
