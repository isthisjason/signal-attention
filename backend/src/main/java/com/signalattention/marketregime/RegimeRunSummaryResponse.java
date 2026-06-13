package com.signalattention.marketregime;

import java.time.Instant;

public record RegimeRunSummaryResponse(
        Long id,
        String symbol,
        String timeframe,
        Instant startDate,
        Instant endDate,
        Integer windowSize,
        Integer stride,
        Boolean includeAnomalies,
        String requestedMode,
        String effectiveMode,
        String classifierSource,
        String modelVersion,
        String featureVersion,
        String artifactIdentifier,
        Integer pointCount,
        RegimeRunStatus status,
        Instant createdAt,
        Instant completedAt
) {
    public static RegimeRunSummaryResponse from(RegimeRun run) {
        return new RegimeRunSummaryResponse(
                run.getId(),
                run.getSymbol(),
                run.getTimeframe(),
                run.getStartDate(),
                run.getEndDate(),
                run.getWindowSize(),
                run.getStride(),
                run.getIncludeAnomalies(),
                run.getRequestedMode(),
                run.getEffectiveMode(),
                run.getClassifierSource(),
                run.getModelVersion(),
                run.getFeatureVersion(),
                run.getArtifactIdentifier(),
                run.getPointCount(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getCompletedAt()
        );
    }
}
