package com.signalattention.dashboard;

import com.signalattention.backtesting.BacktestRun;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record DashboardStrategyPerformanceResponse(
        Long strategyId,
        String name,
        String symbol,
        String timeframe,
        StrategyStatus status,
        Long latestBacktestId,
        BigDecimal latestTotalReturn,
        BigDecimal latestMaxDrawdown,
        Integer latestTradeCount,
        BigDecimal latestMlRiskScore,
        String latestMlRiskLabel,
        Instant latestBacktestCreatedAt,
        long paperSessionCount
) {

    public static DashboardStrategyPerformanceResponse from(Strategy strategy, BacktestRun latestBacktest, long paperSessionCount) {
        return new DashboardStrategyPerformanceResponse(
                strategy.getId(),
                strategy.getName(),
                strategy.getSymbol(),
                strategy.getTimeframe(),
                strategy.getStatus(),
                latestBacktest == null ? null : latestBacktest.getId(),
                latestBacktest == null ? null : latestBacktest.getTotalReturn(),
                latestBacktest == null ? null : latestBacktest.getMaxDrawdown(),
                latestBacktest == null ? null : latestBacktest.getTradeCount(),
                latestBacktest == null ? null : latestBacktest.getMlRiskScore(),
                latestBacktest == null ? null : latestBacktest.getMlRiskLabel(),
                latestBacktest == null ? null : latestBacktest.getCreatedAt(),
                paperSessionCount
        );
    }
}
