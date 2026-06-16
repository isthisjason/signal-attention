package com.signalattention.ml;

import java.util.List;
import java.util.Map;

public record MlMarketRegimeStatusResponse(
        String mode,
        String effectiveMode,
        String classifierSource,
        Boolean ready,
        Boolean artifactConfigured,
        Boolean artifactExists,
        String artifactIdentifier,
        String modelVersion,
        String featureVersion,
        Integer sequenceLength,
        String runId,
        String artifactName,
        String artifactPath,
        String architecture,
        List<String> labels,
        Map<String, Object> modelConfig,
        List<String> warnings
) {
}
