package com.signalattention.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

public record ParsedMarketCandle(
        int rowNumber,
        String symbol,
        String timeframe,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {

    public MarketCandle toEntity() {
        return new MarketCandle(symbol, timeframe, openTime, open, high, low, close, volume);
    }
}
