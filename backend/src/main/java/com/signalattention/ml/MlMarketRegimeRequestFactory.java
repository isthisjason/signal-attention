package com.signalattention.ml;

import com.signalattention.common.BadRequestException;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class MlMarketRegimeRequestFactory {

    public static final int MIN_CANDLE_LIMIT = 20;
    public static final int DEFAULT_CANDLE_LIMIT = 128;
    public static final int MAX_CANDLE_LIMIT = 500;

    private final MarketCandleRepository marketCandleRepository;

    public MlMarketRegimeRequestFactory(MarketCandleRepository marketCandleRepository) {
        this.marketCandleRepository = marketCandleRepository;
    }

    public MlMarketRegimeRequest forLatestCandles(String symbol, String timeframe, Integer requestedLimit) {
        String normalizedSymbol = requireText(symbol, "symbol");
        String normalizedTimeframe = requireText(timeframe, "timeframe");
        int limit = normalizeLimit(requestedLimit);
        List<MarketCandle> candles = new ArrayList<>(
                marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc(
                        normalizedSymbol,
                        normalizedTimeframe,
                        PageRequest.of(0, limit)
                )
        );
        // Repository limiting is newest-first; ML sequence inputs must be chronological.
        Collections.reverse(candles);
        return toRequest(normalizedSymbol, normalizedTimeframe, candles);
    }

    public MlMarketRegimeRequest forCandlesEndingAt(
            String symbol,
            String timeframe,
            Integer requestedLimit,
            Instant windowEnd
    ) {
        String normalizedSymbol = requireText(symbol, "symbol");
        String normalizedTimeframe = requireText(timeframe, "timeframe");
        int limit = normalizeLimit(requestedLimit);
        List<MarketCandle> candles = marketCandleRepository
                .findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                        normalizedSymbol,
                        normalizedTimeframe,
                        Instant.EPOCH,
                        windowEnd
                );
        // Keep the diagnostic request aligned to its selected endpoint while bounding the ML payload.
        List<MarketCandle> boundedCandles = candles.size() <= limit
                ? candles
                : candles.subList(candles.size() - limit, candles.size());
        return toRequest(normalizedSymbol, normalizedTimeframe, boundedCandles);
    }

    public MlMarketRegimeRequest forCandles(
            String symbol,
            String timeframe,
            List<MarketCandle> candles
    ) {
        return toRequest(
                requireText(symbol, "symbol"),
                requireText(timeframe, "timeframe"),
                candles
        );
    }

    private MlMarketRegimeRequest toRequest(
            String symbol,
            String timeframe,
            List<MarketCandle> candles
    ) {
        return new MlMarketRegimeRequest(
                symbol,
                timeframe,
                candles.stream().map(this::toMlCandle).toList()
        );
    }

    private MlMarketRegimeCandle toMlCandle(MarketCandle candle) {
        return new MlMarketRegimeCandle(
                candle.getOpenTime(),
                candle.getOpenPrice(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume()
        );
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private int normalizeLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_CANDLE_LIMIT : requestedLimit;
        if (limit < MIN_CANDLE_LIMIT) {
            throw new BadRequestException("limit must be at least " + MIN_CANDLE_LIMIT);
        }
        if (limit > MAX_CANDLE_LIMIT) {
            throw new BadRequestException("limit must be less than or equal to " + MAX_CANDLE_LIMIT);
        }
        return limit;
    }
}
