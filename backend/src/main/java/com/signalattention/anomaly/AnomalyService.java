package com.signalattention.anomaly;

import com.signalattention.common.BadRequestException;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlAnomalyResponse;
import com.signalattention.ml.MlMarketRegimeCandle;
import com.signalattention.ml.MlMarketRegimeRequest;
import com.signalattention.ml.MlRiskClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnomalyService {

    private static final int DEFAULT_CANDLE_LIMIT = 128;
    private static final int MIN_CANDLE_LIMIT = 20;
    private static final int MAX_CANDLE_LIMIT = 500;

    private final MarketCandleRepository marketCandleRepository;
    private final MlRiskClient mlRiskClient;

    public AnomalyService(MarketCandleRepository marketCandleRepository, MlRiskClient mlRiskClient) {
        this.marketCandleRepository = marketCandleRepository;
        this.mlRiskClient = mlRiskClient;
    }

    @Transactional(readOnly = true)
    public MlAnomalyResponse check(AnomalyCheckRequest request) {
        String symbol = requireText(request.symbol(), "symbol");
        String timeframe = requireText(request.timeframe(), "timeframe");
        int limit = normalizeLimit(request.limit());
        List<MarketCandle> candles = latestCandlesAscending(symbol, timeframe, limit);
        if (candles.isEmpty()) {
            throw new BadRequestException("No candles found for requested anomaly analysis");
        }
        if (candles.size() < MIN_CANDLE_LIMIT) {
            throw new BadRequestException("At least " + MIN_CANDLE_LIMIT + " candles are required for anomaly analysis");
        }
        // Anomaly scoring uses the same recent candle window as market regime classification.
        return mlRiskClient.predictAnomaly(new MlMarketRegimeRequest(
                symbol,
                timeframe,
                candles.stream().map(this::toMlCandle).toList()
        ));
    }

    private List<MarketCandle> latestCandlesAscending(String symbol, String timeframe, int limit) {
        // Pull newest candles for the limit, then restore chronological order before sending to ML.
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
