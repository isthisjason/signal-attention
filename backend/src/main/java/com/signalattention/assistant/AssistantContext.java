package com.signalattention.assistant;

import java.time.Instant;

public record AssistantContext(
        long strategyCount,
        long backtestCount,
        long runningPaperSessionCount,
        Long strategyId,
        Long backtestId,
        Long paperSessionId,
        Instant startDate,
        Instant endDate,
        Long latestRegimeRunId,
        String latestRegimeLabel,
        Integer latestRegimePointCount,
        java.math.BigDecimal latestRegimeAverageConfidence,
        java.math.BigDecimal latestRegimeBaselineDisagreementRate,
        Boolean latestRegimeModeChanged,
        Boolean latestRegimeArtifactChanged,
        String latestRegimeRobustnessLabel,
        Integer modelLabTotalRuns,
        Integer modelLabEligibleRuns,
        String modelLabBestRunId,
        Integer modelLabWarningCount
) {
}
