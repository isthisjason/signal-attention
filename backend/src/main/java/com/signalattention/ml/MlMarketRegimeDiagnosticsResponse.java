package com.signalattention.ml;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MlMarketRegimeDiagnosticsResponse(
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
        List<String> reasons,
        List<MlAttentionTimestepEvidence> topTimesteps,
        List<MlFeatureEvidence> featureEvidence,
        String classifierSource,
        String mode,
        String modelVersion,
        String featureVersion,
        Integer sequenceLength,
        String artifactIdentifier
) {
}
