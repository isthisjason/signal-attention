package com.signalattention.marketregime;

import java.util.List;

public record RegimeRobustnessSummaryResponse(
        Long regimeRunId,
        Long backtestId,
        String symbol,
        String timeframe,
        String reviewLabel,
        RegimeRunQualitySummary qualitySummary,
        List<String> reviewReasons,
        List<RegimeBacktestAnalysisResponse.RegimeBacktestBucket> regimes
) {
}
