package com.signalattention.marketregime;

import java.math.BigDecimal;
import java.time.Instant;

public record RegimeEvidenceSnapshotResponse(
        Long id,
        String symbol,
        String timeframe,
        Instant windowStart,
        Instant windowEnd,
        String regimeLabel,
        BigDecimal confidence,
        String baselineRegimeLabel,
        BigDecimal baselineConfidence,
        Boolean disagreesWithBaseline,
        String evidenceSource,
        String classifierSource,
        String modelVersion,
        String featureVersion,
        String artifactIdentifier,
        String reasonsJson,
        String topTimestepsJson,
        String featureEvidenceJson,
        Instant createdAt
) {
    public static RegimeEvidenceSnapshotResponse from(RegimeEvidenceSnapshot snapshot) {
        return new RegimeEvidenceSnapshotResponse(
                snapshot.getId(),
                snapshot.getSymbol(),
                snapshot.getTimeframe(),
                snapshot.getWindowStart(),
                snapshot.getWindowEnd(),
                snapshot.getRegimeLabel(),
                snapshot.getConfidence(),
                snapshot.getBaselineRegimeLabel(),
                snapshot.getBaselineConfidence(),
                snapshot.getDisagreesWithBaseline(),
                snapshot.getEvidenceSource(),
                snapshot.getClassifierSource(),
                snapshot.getModelVersion(),
                snapshot.getFeatureVersion(),
                snapshot.getArtifactIdentifier(),
                snapshot.getReasonsJson(),
                snapshot.getTopTimestepsJson(),
                snapshot.getFeatureEvidenceJson(),
                snapshot.getCreatedAt()
        );
    }
}
