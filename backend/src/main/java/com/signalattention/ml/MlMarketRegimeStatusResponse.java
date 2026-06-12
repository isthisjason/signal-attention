package com.signalattention.ml;

import java.util.List;

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
        List<String> warnings
) {
}
