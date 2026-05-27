package com.signalattention.ml;

import java.util.List;

public record MlRegimeRunResponse(
        String symbol,
        String timeframe,
        Integer windowSize,
        Integer stride,
        Boolean includeAnomalies,
        Integer pointCount,
        List<MlRegimeRunPoint> points
) {
}
