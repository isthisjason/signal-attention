package com.signalattention.ml;

import java.util.List;
import java.util.Map;

public record MlMarketRegimeExperimentDiagnosticsResponse(
        Map<String, Object> summary,
        List<Map<String, Object>> runs,
        List<Map<String, Object>> incompleteRuns,
        Map<String, Object> promotion,
        List<String> warnings
) {
}
