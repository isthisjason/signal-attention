package com.signalattention.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

public record CandleResponse(
        String symbol,
        String timeframe,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {

    public static CandleResponse from(MarketCandle candle) {
        return new CandleResponse(
                candle.getSymbol(),
                candle.getTimeframe(),
                candle.getOpenTime(),
                candle.getOpenPrice(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume()
        );
    }
}
