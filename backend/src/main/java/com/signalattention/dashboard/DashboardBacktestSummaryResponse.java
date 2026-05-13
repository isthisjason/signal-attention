package com.signalattention.dashboard;

import com.signalattention.backtesting.BacktestRun;
import com.signalattention.backtesting.BacktestStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record DashboardBacktestSummaryResponse(
        Long id,
        Long strategyId,
        BacktestStatus status,
        BigDecimal totalReturn,
        BigDecimal maxDrawdown,
        Integer tradeCount,
        BigDecimal mlRiskScore,
        String mlRiskLabel,
        Instant createdAt
) {

    public static DashboardBacktestSummaryResponse from(BacktestRun run) {
        return new DashboardBacktestSummaryResponse(
                run.getId(),
                run.getStrategy().getId(),
                run.getStatus(),
                run.getTotalReturn(),
                run.getMaxDrawdown(),
                run.getTradeCount(),
                run.getMlRiskScore(),
                run.getMlRiskLabel(),
                run.getCreatedAt()
        );
    }
}
