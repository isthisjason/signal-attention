package com.signalattention.ml;

import java.util.List;

public record MlMarketRegimeRequest(
        String symbol,
        String timeframe,
        List<MlMarketRegimeCandle> candles
) {
}
