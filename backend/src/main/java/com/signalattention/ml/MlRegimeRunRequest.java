package com.signalattention.ml;

import java.util.List;

public record MlRegimeRunRequest(
        String symbol,
        String timeframe,
        List<MlMarketRegimeCandle> candles,
        Integer windowSize,
        Integer stride,
        Boolean includeAnomalies
) {
}
