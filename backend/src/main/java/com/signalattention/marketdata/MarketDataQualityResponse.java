package com.signalattention.marketdata;

import java.time.Instant;
import java.util.List;

public record MarketDataQualityResponse(
        String symbol,
        String timeframe,
        int candleCount,
        Instant firstOpenTime,
        Instant lastOpenTime,
        long expectedIntervalMinutes,
        int duplicateTimestampCount,
        int gapCount,
        int invalidOhlcCount,
        int zeroOrNegativeVolumeCount,
        List<String> warnings
) {
}
