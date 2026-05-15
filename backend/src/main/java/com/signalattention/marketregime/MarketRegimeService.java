package com.signalattention.marketregime;

import com.signalattention.common.BadRequestException;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlMarketRegimeCandle;
import com.signalattention.ml.MlMarketRegimeRequest;
import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlRiskClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketRegimeService {

    public static final int MIN_CANDLE_LIMIT = 20;
    public static final int DEFAULT_CANDLE_LIMIT = 128;
    public static final int MAX_CANDLE_LIMIT = 500;

    private final MarketCandleRepository marketCandleRepository;
    private final MlRiskClient mlRiskClient;

    public MarketRegimeService(MarketCandleRepository marketCandleRepository, MlRiskClient mlRiskClient) {
        this.marketCandleRepository = marketCandleRepository;
        this.mlRiskClient = mlRiskClient;
    }

    @Transactional(readOnly = true)
    public MlMarketRegimeResponse predictMarketRegime(String symbol, String timeframe, Integer requestedLimit) {
        String normalizedSymbol = requireText(symbol, "symbol");
        String normalizedTimeframe = requireText(timeframe, "timeframe");
        int limit = normalizeLimit(requestedLimit);
        List<MarketCandle> candles = latestCandlesAscending(normalizedSymbol, normalizedTimeframe, limit);
        if (candles.isEmpty()) {
            throw new BadRequestException("No candles found for requested market regime analysis");
        }
        if (candles.size() < MIN_CANDLE_LIMIT) {
            throw new BadRequestException("At least " + MIN_CANDLE_LIMIT + " candles are required for market regime analysis");
        }

        return mlRiskClient.predictMarketRegime(new MlMarketRegimeRequest(
                normalizedSymbol,
                normalizedTimeframe,
                candles.stream().map(this::toMlCandle).toList()
        ));
    }

    private List<MarketCandle> latestCandlesAscending(String symbol, String timeframe, int limit) {
        List<MarketCandle> candles = new ArrayList<>(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc(
                symbol,
                timeframe,
                PageRequest.of(0, limit)
        ));
        Collections.reverse(candles);
        return candles;
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
