package com.signalattention.marketregime;

import java.math.BigDecimal;
import java.util.List;

public record RegimeRunComparisonResponse(
        String symbol,
        String timeframe,
        List<RegimeRunComparisonItem> runs
) {
    public record RegimeRunComparisonItem(
            RegimeRunSummaryResponse run,
            RegimeRunComparisonDelta deltaFromPrevious
    ) {
    }

    public record RegimeRunComparisonDelta(
            BigDecimal averageConfidenceDelta,
            BigDecimal baselineDisagreementRateDelta,
            Integer pointCountDelta,
            Boolean modeChanged,
            Boolean modelChanged,
            Boolean artifactChanged
    ) {
    }
}
