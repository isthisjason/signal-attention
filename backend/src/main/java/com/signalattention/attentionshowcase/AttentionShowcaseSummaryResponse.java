package com.signalattention.attentionshowcase;

import com.signalattention.marketregime.RegimeRunQualitySummary;
import com.signalattention.marketregime.RegimeRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AttentionShowcaseSummaryResponse(
        Boolean modelReady,
        String requestedMode,
        String effectiveMode,
        String classifierSource,
        String promotionStatus,
        String promotedRunId,
        Boolean promotionArtifactMatches,
        LatestRun latestRun,
        String robustnessLabel,
        List<String> reviewReasons,
        Long evidenceSnapshotCount,
        DisagreementSummary disagreementSummary,
        String nextAction,
        List<String> warnings
) {
    public record LatestRun(
            Long id,
            String symbol,
            String timeframe,
            Instant startDate,
            Instant endDate,
            Integer pointCount,
            RegimeRunStatus status,
            Instant createdAt,
            RegimeRunQualitySummary qualitySummary
    ) {
    }

    public record DisagreementSummary(
            Integer totalWindows,
            Integer disagreementCount,
            BigDecimal disagreementRate,
            String dominantRegimeLabel,
            String dominantBaselineLabel,
            Integer anomalyOverlapCount,
            List<DisagreementWindow> lowestConfidenceWindows
    ) {
    }

    public record DisagreementWindow(
            Instant windowEnd,
            String regimeLabel,
            String baselineRegimeLabel,
            BigDecimal confidence,
            String anomalyLabel
    ) {
    }
}
