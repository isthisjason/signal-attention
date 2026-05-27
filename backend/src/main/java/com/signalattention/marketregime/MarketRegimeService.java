package com.signalattention.marketregime;

import com.signalattention.common.BadRequestException;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.backtesting.BacktestTrade;
import com.signalattention.backtesting.BacktestTradeRepository;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.CandleResponse;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlMarketRegimeCandle;
import com.signalattention.ml.MlMarketRegimeRequest;
import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlRegimeRunRequest;
import com.signalattention.ml.MlRegimeRunResponse;
import com.signalattention.ml.MlRiskClient;
import java.time.Instant;
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
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestTradeRepository backtestTradeRepository;

    public MarketRegimeService(
            MarketCandleRepository marketCandleRepository,
            MlRiskClient mlRiskClient,
            BacktestRunRepository backtestRunRepository,
            BacktestTradeRepository backtestTradeRepository
    ) {
        this.marketCandleRepository = marketCandleRepository;
        this.mlRiskClient = mlRiskClient;
        this.backtestRunRepository = backtestRunRepository;
        this.backtestTradeRepository = backtestTradeRepository;
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

    @Transactional(readOnly = true)
    public RegimeRunResponse runRegimeReplay(RegimeRunRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BadRequestException("startDate must be before or equal to endDate");
        }
        String symbol = requireText(request.symbol(), "symbol");
        String timeframe = requireText(request.timeframe(), "timeframe");
        int windowSize = request.windowSize() == null ? DEFAULT_CANDLE_LIMIT : request.windowSize();
        int stride = request.stride() == null ? 8 : request.stride();
        boolean includeAnomalies = request.includeAnomalies() == null || request.includeAnomalies();
        List<MarketCandle> candles = marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                symbol, timeframe, request.startDate(), request.endDate()
        );
        if (candles.size() < MIN_CANDLE_LIMIT) {
            throw new BadRequestException("At least " + MIN_CANDLE_LIMIT + " candles are required for regime replay");
        }
        if (windowSize > candles.size()) {
            throw new BadRequestException("windowSize must be less than or equal to candle count");
        }

        MlRegimeRunResponse mlResponse = mlRiskClient.predictRegimeRun(
                new MlRegimeRunRequest(
                        symbol,
                        timeframe,
                        candles.stream().map(this::toMlCandle).toList(),
                        windowSize,
                        stride,
                        includeAnomalies
                )
        );

        return new RegimeRunResponse(
                symbol,
                timeframe,
                windowSize,
                stride,
                includeAnomalies,
                mlResponse.pointCount(),
                candles.stream().map(CandleResponse::from).toList(),
                mlResponse.points().stream().map(point -> new RegimeRunResponse.RegimeRunPoint(
                        point.windowStart(),
                        point.windowEnd(),
                        point.regimeLabel(),
                        point.confidence(),
                        point.reasons(),
                        point.features(),
                        point.anomalyScore(),
                        point.anomalyLabel(),
                        point.anomalyReasons()
                )).toList(),
                loadTradeMarkers(request.backtestId(), symbol, timeframe, request.startDate(), request.endDate())
        );
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

    private List<RegimeRunResponse.RegimeTradeMarker> loadTradeMarkers(
            Long backtestId,
            String symbol,
            String timeframe,
            Instant startDate,
            Instant endDate
    ) {
        if (backtestId == null) {
            return List.of();
        }
        var run = backtestRunRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest run not found: " + backtestId));
        if (!run.getStrategy().getSymbol().equals(symbol) || !run.getStrategy().getTimeframe().equals(timeframe)) {
            throw new BadRequestException("backtestId does not match the requested symbol/timeframe");
        }
        List<BacktestTrade> trades = backtestTradeRepository.findByBacktestRunIdOrderByEntryTimeAsc(backtestId);
        return trades.stream()
                .filter(trade -> !trade.getEntryTime().isBefore(startDate) && !trade.getEntryTime().isAfter(endDate))
                .map(trade -> new RegimeRunResponse.RegimeTradeMarker(
                        trade.getId(),
                        trade.getSide(),
                        trade.getEntryTime(),
                        trade.getEntryPrice(),
                        trade.getExitTime(),
                        trade.getExitPrice(),
                        trade.getNetPnl()
                ))
                .toList();
    }
}
