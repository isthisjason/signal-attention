package com.signalattention.marketregime;

import com.signalattention.common.BadRequestException;
import com.signalattention.common.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.backtesting.BacktestRun;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.backtesting.BacktestTrade;
import com.signalattention.backtesting.BacktestTradeRepository;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.CandleResponse;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlMarketRegimeCandle;
import com.signalattention.ml.MlMarketRegimeFeatures;
import com.signalattention.ml.MlMarketRegimeRequest;
import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlMarketRegimeStatusResponse;
import com.signalattention.ml.MlRegimeRunRequest;
import com.signalattention.ml.MlRegimeRunResponse;
import com.signalattention.ml.MlRiskClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final RegimeRunRepository regimeRunRepository;
    private final RegimePredictionRepository regimePredictionRepository;
    private final ObjectMapper objectMapper;

    public MarketRegimeService(
            MarketCandleRepository marketCandleRepository,
            MlRiskClient mlRiskClient,
            BacktestRunRepository backtestRunRepository,
            BacktestTradeRepository backtestTradeRepository,
            RegimeRunRepository regimeRunRepository,
            RegimePredictionRepository regimePredictionRepository,
            ObjectMapper objectMapper
    ) {
        this.marketCandleRepository = marketCandleRepository;
        this.mlRiskClient = mlRiskClient;
        this.backtestRunRepository = backtestRunRepository;
        this.backtestTradeRepository = backtestTradeRepository;
        this.regimeRunRepository = regimeRunRepository;
        this.regimePredictionRepository = regimePredictionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MlMarketRegimeStatusResponse getModelStatus() {
        return mlRiskClient.getMarketRegimeStatus();
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

        // The backend owns candle lookup; the ML service only receives the sequence to classify.
        return mlRiskClient.predictMarketRegime(new MlMarketRegimeRequest(
                normalizedSymbol,
                normalizedTimeframe,
                candles.stream().map(this::toMlCandle).toList()
        ));
    }

    @Transactional
    public RegimeRunResponse runRegimeReplay(RegimeRunRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BadRequestException("startDate must be before or equal to endDate");
        }
        String symbol = requireText(request.symbol(), "symbol");
        String timeframe = requireText(request.timeframe(), "timeframe");
        int windowSize = request.windowSize() == null ? DEFAULT_CANDLE_LIMIT : request.windowSize();
        int stride = request.stride() == null ? 8 : request.stride();
        // Anomalies default on so the replay view can correlate regime shifts with unusual candles.
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

        var modelStatus = mlRiskClient.getMarketRegimeStatus();
        RegimeRun run = regimeRunRepository.save(new RegimeRun(
                symbol,
                timeframe,
                request.startDate(),
                request.endDate(),
                windowSize,
                stride,
                includeAnomalies
        ));

        // ML returns rolling regime points; the backend persists those points with model provenance.
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
        run.completeFromStatus(modelStatus, mlResponse.pointCount());
        regimeRunRepository.save(run);
        List<RegimePrediction> predictions = mlResponse.points().stream()
                .map(point -> new RegimePrediction(
                        run,
                        point.windowStart(),
                        point.windowEnd(),
                        point.regimeLabel(),
                        point.confidence(),
                        toJson(point.reasons()),
                        toJson(point.features()),
                        point.anomalyScore(),
                        point.anomalyLabel(),
                        toJson(point.anomalyReasons()),
                        point.baselineRegimeLabel(),
                        point.baselineConfidence(),
                        point.disagreesWithBaseline()
                ))
                .toList();
        regimePredictionRepository.saveAll(predictions);

        return toResponse(run, predictions, candles, request.backtestId());
    }

    @Transactional(readOnly = true)
    public RegimeRunResponse getRegimeRun(Long id) {
        RegimeRun run = regimeRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regime run not found: " + id));
        List<MarketCandle> candles = marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                run.getSymbol(), run.getTimeframe(), run.getStartDate(), run.getEndDate()
        );
        return toResponse(
                run,
                regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(id),
                candles,
                null
        );
    }

    @Transactional(readOnly = true)
    public List<RegimeRunSummaryResponse> listRegimeRuns(String symbol, String timeframe, Integer requestedLimit) {
        String normalizedSymbol = requireText(symbol, "symbol");
        String normalizedTimeframe = requireText(timeframe, "timeframe");
        int limit = requestedLimit == null ? 10 : requestedLimit;
        if (limit < 1 || limit > 50) {
            throw new BadRequestException("limit must be between 1 and 50");
        }
        return regimeRunRepository.findBySymbolAndTimeframeOrderByCreatedAtDesc(
                        normalizedSymbol,
                        normalizedTimeframe,
                        PageRequest.of(0, limit)
                ).stream()
                .map(RegimeRunSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RegimeBacktestAnalysisResponse analyzeBacktestByRegime(Long backtestId, Long regimeRunId) {
        BacktestRun backtest = backtestRunRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest run not found: " + backtestId));
        RegimeRun regimeRun = regimeRunRepository.findById(regimeRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Regime run not found: " + regimeRunId));
        String symbol = backtest.getStrategy().getSymbol();
        String timeframe = backtest.getStrategy().getTimeframe();
        if (!symbol.equals(regimeRun.getSymbol()) || !timeframe.equals(regimeRun.getTimeframe())) {
            throw new BadRequestException("regimeRunId does not match the backtest symbol/timeframe");
        }

        List<RegimePrediction> predictions = regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(regimeRunId);
        List<BacktestTrade> trades = backtestTradeRepository.findByBacktestRunIdOrderByEntryTimeAsc(backtestId);
        return new RegimeBacktestAnalysisResponse(
                backtestId,
                regimeRunId,
                symbol,
                timeframe,
                trades.stream()
                        .filter(trade -> trade.getNetPnl() != null)
                        .collect(java.util.stream.Collectors.groupingBy(trade -> labelForTrade(trade, predictions)))
                        .entrySet()
                        .stream()
                        .map(entry -> toBucket(entry.getKey(), entry.getValue(), predictions))
                        .sorted(Comparator.comparing(RegimeBacktestAnalysisResponse.RegimeBacktestBucket::regimeLabel))
                        .toList()
        );
    }

    private List<MarketCandle> latestCandlesAscending(String symbol, String timeframe, int limit) {
        // Repository query is newest-first for limiting, then reversed for time-series processing.
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
        // Bound request size so local ML calls stay fast and predictable on CPU.
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
        // Trade markers are optional chart context and must match the requested market.
        var run = backtestRunRepository.findById(backtestId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest run not found: " + backtestId));
        if (!run.getStrategy().getSymbol().equals(symbol) || !run.getStrategy().getTimeframe().equals(timeframe)) {
            throw new BadRequestException("backtestId does not match the requested symbol/timeframe");
        }
        List<BacktestTrade> trades = backtestTradeRepository.findByBacktestRunIdOrderByEntryTimeAsc(backtestId);
        return trades.stream()
                // Filter by entry time because markers are placed where trades begin on the candle chart.
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

    private RegimeRunResponse toResponse(RegimeRun run, List<RegimePrediction> predictions, List<MarketCandle> candles, Long backtestId) {
        return new RegimeRunResponse(
                run.getId(),
                run.getSymbol(),
                run.getTimeframe(),
                run.getStartDate(),
                run.getEndDate(),
                run.getWindowSize(),
                run.getStride(),
                run.getIncludeAnomalies(),
                run.getRequestedMode(),
                run.getEffectiveMode(),
                run.getClassifierSource(),
                run.getModelVersion(),
                run.getFeatureVersion(),
                run.getArtifactIdentifier(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getCompletedAt(),
                run.getPointCount(),
                candles.stream().map(CandleResponse::from).toList(),
                predictions.stream().map(this::toPointResponse).toList(),
                loadTradeMarkers(backtestId, run.getSymbol(), run.getTimeframe(), run.getStartDate(), run.getEndDate())
        );
    }

    private String labelForTrade(BacktestTrade trade, List<RegimePrediction> predictions) {
        return predictionForTrade(trade, predictions)
                .map(RegimePrediction::getRegimeLabel)
                .orElse("UNCLASSIFIED");
    }

    private RegimeBacktestAnalysisResponse.RegimeBacktestBucket toBucket(
            String regimeLabel,
            List<BacktestTrade> trades,
            List<RegimePrediction> predictions
    ) {
        BigDecimal totalPnl = trades.stream()
                .map(BacktestTrade::getNetPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long wins = trades.stream()
                .filter(trade -> trade.getNetPnl().compareTo(BigDecimal.ZERO) > 0)
                .count();
        BigDecimal winRate = BigDecimal.valueOf(wins)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(trades.size()), 6, RoundingMode.HALF_UP);
        BigDecimal averageReturn = trades.stream()
                .map(BacktestTrade::getReturnPercent)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(trades.size()), 6, RoundingMode.HALF_UP);
        BigDecimal bestTrade = trades.stream().map(BacktestTrade::getNetPnl).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal worstTrade = trades.stream().map(BacktestTrade::getNetPnl).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        long disagreements = trades.stream()
                .map(trade -> predictionForTrade(trade, predictions))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(prediction -> Boolean.TRUE.equals(prediction.getDisagreesWithBaseline()))
                .count();
        return new RegimeBacktestAnalysisResponse.RegimeBacktestBucket(
                regimeLabel,
                trades.size(),
                winRate,
                totalPnl,
                averageReturn,
                bestTrade,
                worstTrade,
                disagreements
        );
    }

    private java.util.Optional<RegimePrediction> predictionForTrade(BacktestTrade trade, List<RegimePrediction> predictions) {
        // Entry time is the cleanest join point because trades are opened against a specific candle context.
        return predictions.stream()
                .filter(prediction -> !trade.getEntryTime().isBefore(prediction.getWindowStart())
                        && !trade.getEntryTime().isAfter(prediction.getWindowEnd()))
                .findFirst();
    }

    private RegimeRunResponse.RegimeRunPoint toPointResponse(RegimePrediction prediction) {
        return new RegimeRunResponse.RegimeRunPoint(
                prediction.getWindowStart(),
                prediction.getWindowEnd(),
                prediction.getRegimeLabel(),
                prediction.getConfidence(),
                fromJsonList(prediction.getReasonsJson()),
                fromJsonFeatures(prediction.getFeaturesJson()),
                prediction.getAnomalyScore(),
                prediction.getAnomalyLabel(),
                fromJsonList(prediction.getAnomalyReasonsJson()),
                prediction.getBaselineRegimeLabel(),
                prediction.getBaselineConfidence(),
                prediction.getDisagreesWithBaseline()
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to serialize regime prediction details");
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private MlMarketRegimeFeatures fromJsonFeatures(String json) {
        try {
            return objectMapper.readValue(json, MlMarketRegimeFeatures.class);
        } catch (JsonProcessingException exception) {
            return new MlMarketRegimeFeatures(null, null, null, null, null, null);
        }
    }
}
