package com.signalattention.marketregime;

import java.math.BigDecimal;
import java.util.List;

public record RegimeBacktestAnalysisResponse(
        Long backtestId,
        Long regimeRunId,
        String symbol,
        String timeframe,
        List<RegimeBacktestBucket> regimes
) {
    public record RegimeBacktestBucket(
            String regimeLabel,
            long tradeCount,
            BigDecimal winRate,
            BigDecimal totalNetPnl,
            BigDecimal averageReturn,
            BigDecimal bestTrade,
            BigDecimal worstTrade,
            long baselineDisagreementCount
    ) {
    }
}
