package com.signalattention.marketregime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.signalattention.common.BadRequestException;
import com.signalattention.common.ExternalServiceException;
import com.signalattention.audit.AuditService;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.backtesting.BacktestTradeRepository;
import com.signalattention.backtesting.BacktestRun;
import com.signalattention.backtesting.BacktestStatus;
import com.signalattention.backtesting.BacktestTrade;
import com.signalattention.backtesting.TradeSide;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlMarketRegimeFeatures;
import com.signalattention.ml.MlAttentionTimestepEvidence;
import com.signalattention.ml.MlFeatureEvidence;
import com.signalattention.ml.MlMarketRegimeDiagnosticsResponse;
import com.signalattention.ml.MlMarketRegimeRequest;
import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlMarketRegimeStatusResponse;
import com.signalattention.ml.MlRiskClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyStatus;
import com.signalattention.strategies.StrategyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MarketRegimeServiceTests {

    @Mock
    private MarketCandleRepository marketCandleRepository;

    @Mock
    private MlRiskClient mlRiskClient;
    @Mock
    private BacktestRunRepository backtestRunRepository;
    @Mock
    private BacktestTradeRepository backtestTradeRepository;
    @Mock
    private RegimeRunRepository regimeRunRepository;
    @Mock
    private RegimePredictionRepository regimePredictionRepository;
    @Mock
    private RegimeEvidenceSnapshotRepository regimeEvidenceSnapshotRepository;
    @Mock
    private AuditService auditService;

    private MarketRegimeService service;

    @BeforeEach
    void setUp() {
        service = new MarketRegimeService(
                marketCandleRepository,
                mlRiskClient,
                backtestRunRepository,
                backtestTradeRepository,
                regimeRunRepository,
                regimePredictionRepository,
                regimeEvidenceSnapshotRepository,
                new ObjectMapper(),
                auditService
        );
    }

    @Test
    void predictMarketRegimeSendsLatestCandlesAscendingToMlService() {
        List<MarketCandle> descendingCandles = candlesDescending(20);
        MlMarketRegimeResponse mlResponse = response();
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(descendingCandles);
        when(mlRiskClient.predictMarketRegime(any(MlMarketRegimeRequest.class))).thenReturn(mlResponse);

        MlMarketRegimeResponse response = service.predictMarketRegime(" BTC-USD ", " 1h ", 20);

        assertThat(response.regimeLabel()).isEqualTo("TRENDING_UP");
        assertThat(response.mode()).isEqualTo("rules");
        assertThat(response.featureVersion()).isEqualTo("torch-market-regime-features/v1");
        assertThat(response.sequenceLength()).isEqualTo(20);
        assertThat(response.artifactIdentifier()).isNull();
        ArgumentCaptor<MlMarketRegimeRequest> requestCaptor = ArgumentCaptor.forClass(MlMarketRegimeRequest.class);
        org.mockito.Mockito.verify(mlRiskClient).predictMarketRegime(requestCaptor.capture());
        MlMarketRegimeRequest request = requestCaptor.getValue();
        assertThat(request.symbol()).isEqualTo("BTC-USD");
        assertThat(request.timeframe()).isEqualTo("1h");
        assertThat(request.candles()).hasSize(20);
        assertThat(request.candles().get(0).openTime()).isBefore(request.candles().get(19).openTime());
    }

    @Test
    void predictMarketRegimeRejectsMissingCandles() {
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.predictMarketRegime("BTC-USD", "1h", 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("No candles found for requested market regime analysis");
    }

    @Test
    void predictMarketRegimeRejectsInsufficientCandles() {
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(candlesDescending(19));

        assertThatThrownBy(() -> service.predictMarketRegime("BTC-USD", "1h", 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("At least 20 candles are required for market regime analysis");
    }

    @Test
    void predictMarketRegimeRejectsInvalidLimit() {
        assertThatThrownBy(() -> service.predictMarketRegime("BTC-USD", "1h", 19))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("limit must be at least 20");
    }

    @Test
    void predictMarketRegimePropagatesMlFailure() {
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(candlesDescending(20));
        when(mlRiskClient.predictMarketRegime(any(MlMarketRegimeRequest.class)))
                .thenThrow(new ExternalServiceException("ML service is unavailable or returned an invalid response", null));

        assertThatThrownBy(() -> service.predictMarketRegime("BTC-USD", "1h", 20))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void diagnoseMarketRegimeSendsWindowToMlAndAuditsResult() {
        List<MarketCandle> descendingCandles = candlesDescending(20);
        MlMarketRegimeDiagnosticsResponse diagnostics = diagnosticsResponse();
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h", PageRequest.of(0, 20)))
                .thenReturn(descendingCandles);
        when(mlRiskClient.diagnoseMarketRegime(any(MlMarketRegimeRequest.class))).thenReturn(diagnostics);

        MlMarketRegimeDiagnosticsResponse response = service.diagnoseMarketRegime("BTC-USD", "1h", 20, null);

        assertThat(response.regimeLabel()).isEqualTo("TRENDING_UP");
        ArgumentCaptor<MlMarketRegimeRequest> requestCaptor = ArgumentCaptor.forClass(MlMarketRegimeRequest.class);
        org.mockito.Mockito.verify(mlRiskClient).diagnoseMarketRegime(requestCaptor.capture());
        assertThat(requestCaptor.getValue().candles()).hasSize(20);
        assertThat(requestCaptor.getValue().candles().getFirst().openTime()).isBefore(requestCaptor.getValue().candles().getLast().openTime());
        org.mockito.Mockito.verify(regimeEvidenceSnapshotRepository).save(any(RegimeEvidenceSnapshot.class));
        org.mockito.Mockito.verify(auditService).record(
                org.mockito.Mockito.eq("MARKET_REGIME"),
                org.mockito.Mockito.eq("BTC-USD:1h"),
                org.mockito.Mockito.eq("MARKET_REGIME_DIAGNOSTIC"),
                org.mockito.Mockito.eq("Ran market regime attention diagnostics"),
                org.mockito.Mockito.contains("TRENDING_UP")
        );
    }

    @Test
    void runRegimeReplayPersistsRunAndPredictions() {
        RegimeRunRequest request = new RegimeRunRequest(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T06:00:00Z"),
                20,
                5,
                false,
                null
        );
        List<MarketCandle> candles = java.util.stream.IntStream.range(0, 30)
                .mapToObj(this::candle)
                .toList();
        when(marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                "BTC-USD", "1h", request.startDate(), request.endDate()
        )).thenReturn(candles);
        when(mlRiskClient.getMarketRegimeStatus()).thenReturn(status());
        when(regimeRunRepository.save(any(RegimeRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mlRiskClient.predictRegimeRun(any())).thenReturn(new com.signalattention.ml.MlRegimeRunResponse(
                "BTC-USD",
                "1h",
                20,
                5,
                false,
                1,
                List.of(new com.signalattention.ml.MlRegimeRunPoint(
                        candles.get(0).getOpenTime(),
                        candles.get(19).getOpenTime(),
                        "TRENDING_UP",
                        new BigDecimal("80.00"),
                        List.of("Price is rising."),
                        response().features(),
                        null,
                        null,
                        null,
                        "TRENDING_UP",
                        new BigDecimal("80.00"),
                        false
                ))
        ));
        when(regimePredictionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegimeRunResponse response = service.runRegimeReplay(request);

        assertThat(response.symbol()).isEqualTo("BTC-USD");
        assertThat(response.effectiveMode()).isEqualTo("rules");
        assertThat(response.pointCount()).isEqualTo(1);
        assertThat(response.points()).hasSize(1);
        assertThat(response.points().getFirst().regimeLabel()).isEqualTo("TRENDING_UP");
        assertThat(response.qualitySummary().averageConfidence()).isEqualByComparingTo("80.000000");
        assertThat(response.qualitySummary().baselineDisagreementRate()).isEqualByComparingTo("0.000000");
    }

    @Test
    void listRegimeRunsIncludesDerivedQualitySummary() {
        RegimeRun run = savedRun(1L, "rules", null, "2024-01-02T00:00:00Z", 3);
        when(regimeRunRepository.findBySymbolAndTimeframeOrderByCreatedAtDesc(
                "BTC-USD", "1h", PageRequest.of(0, 10)
        )).thenReturn(List.of(run));
        when(regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(1L)).thenReturn(List.of(
                prediction(run, "TRENDING_UP", "80.00", true, "NORMAL"),
                prediction(run, "SIDEWAYS", "50.00", false, "ANOMALY"),
                prediction(run, "TRENDING_UP", "70.00", true, null)
        ));

        List<RegimeRunSummaryResponse> response = service.listRegimeRuns("BTC-USD", "1h", 10);

        RegimeRunQualitySummary summary = response.getFirst().qualitySummary();
        assertThat(summary.averageConfidence()).isEqualByComparingTo("66.666667");
        assertThat(summary.lowConfidenceWindowCount()).isEqualTo(1);
        assertThat(summary.baselineDisagreementCount()).isEqualTo(2);
        assertThat(summary.baselineDisagreementRate()).isEqualByComparingTo("66.666667");
        assertThat(summary.anomalyCount()).isEqualTo(1);
        assertThat(summary.dominantRegimeLabel()).isEqualTo("TRENDING_UP");
        assertThat(summary.regimeCounts()).containsEntry("TRENDING_UP", 2);
    }

    @Test
    void compareRegimeRunsReturnsRecentRunsWithDeltas() {
        RegimeRun current = savedRun(2L, "torch", "artifact-b", "2024-01-03T00:00:00Z", 2);
        RegimeRun previous = savedRun(1L, "rules", "artifact-a", "2024-01-02T00:00:00Z", 1);
        when(regimeRunRepository.findBySymbolAndTimeframeOrderByCreatedAtDesc(
                "BTC-USD", "1h", PageRequest.of(0, 10)
        )).thenReturn(List.of(current, previous));
        when(regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(2L)).thenReturn(List.of(
                prediction(current, "TRENDING_UP", "80.00", true, null),
                prediction(current, "TRENDING_UP", "60.00", false, null)
        ));
        when(regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(1L)).thenReturn(List.of(
                prediction(previous, "SIDEWAYS", "50.00", false, null)
        ));

        RegimeRunComparisonResponse response = service.compareRegimeRuns("BTC-USD", "1h", 10);

        assertThat(response.runs()).hasSize(2);
        RegimeRunComparisonResponse.RegimeRunComparisonDelta delta = response.runs().getFirst().deltaFromPrevious();
        assertThat(delta.averageConfidenceDelta()).isEqualByComparingTo("20.000000");
        assertThat(delta.baselineDisagreementRateDelta()).isEqualByComparingTo("50.000000");
        assertThat(delta.pointCountDelta()).isEqualTo(1);
        assertThat(delta.modeChanged()).isTrue();
        assertThat(delta.artifactChanged()).isTrue();
        assertThat(response.runs().get(1).deltaFromPrevious()).isNull();
    }

    @Test
    void analyzeBacktestByRegimeGroupsTradesByEntryWindow() {
        Strategy strategy = new Strategy("SMA", "BTC-USD", "1h", StrategyType.SMA_CROSSOVER, "{}", StrategyStatus.ACTIVE);
        BacktestRun backtest = new BacktestRun(
                strategy,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
                new BigDecimal("10000"),
                BacktestStatus.COMPLETED
        );
        RegimeRun run = new RegimeRun(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
                20,
                5,
                false
        );
        RegimePrediction prediction = new RegimePrediction(
                run,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T20:00:00Z"),
                "TRENDING_UP",
                new BigDecimal("80.00"),
                "[]",
                "{}",
                null,
                null,
                null,
                "SIDEWAYS",
                new BigDecimal("65.00"),
                true
        );
        BacktestTrade winner = trade(backtest, "2024-01-01T05:00:00Z", "25.00", "2.500000");
        BacktestTrade loser = trade(backtest, "2024-01-01T06:00:00Z", "-5.00", "-0.500000");
        when(backtestRunRepository.findById(10L)).thenReturn(java.util.Optional.of(backtest));
        when(regimeRunRepository.findById(20L)).thenReturn(java.util.Optional.of(run));
        when(regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(20L)).thenReturn(List.of(prediction));
        when(backtestTradeRepository.findByBacktestRunIdOrderByEntryTimeAsc(10L)).thenReturn(List.of(winner, loser));

        RegimeBacktestAnalysisResponse response = service.analyzeBacktestByRegime(10L, 20L);

        assertThat(response.regimes()).hasSize(1);
        RegimeBacktestAnalysisResponse.RegimeBacktestBucket bucket = response.regimes().getFirst();
        assertThat(bucket.regimeLabel()).isEqualTo("TRENDING_UP");
        assertThat(bucket.tradeCount()).isEqualTo(2);
        assertThat(bucket.winRate()).isEqualByComparingTo("50.000000");
        assertThat(bucket.totalNetPnl()).isEqualByComparingTo("20.00");
        assertThat(bucket.baselineDisagreementCount()).isEqualTo(2);
    }

    @Test
    void summarizeRobustnessMarksAlignedRunStable() {
        RegimeRun run = savedRun(20L, "torch", "artifact-a", "2024-01-02T00:00:00Z", 2);
        when(regimeRunRepository.findById(20L)).thenReturn(java.util.Optional.of(run));
        when(regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(20L)).thenReturn(List.of(
                prediction(run, "TRENDING_UP", "80.00", false, "NORMAL"),
                prediction(run, "TRENDING_UP", "75.00", false, null)
        ));

        RegimeRobustnessSummaryResponse response = service.summarizeRobustness(20L, null);

        assertThat(response.reviewLabel()).isEqualTo("stable");
        assertThat(response.qualitySummary().baselineDisagreementRate()).isEqualByComparingTo("0.000000");
        assertThat(response.reviewReasons()).contains("Regime windows were high confidence, baseline aligned, and anomaly free.");
        assertThat(response.regimes()).isEmpty();
    }

    @Test
    void summarizeRobustnessMarksLowConfidenceAndAnomaliesForReview() {
        RegimeRun run = savedRun(20L, "torch", "artifact-a", "2024-01-02T00:00:00Z", 2);
        when(regimeRunRepository.findById(20L)).thenReturn(java.util.Optional.of(run));
        when(regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(20L)).thenReturn(List.of(
                prediction(run, "TRENDING_UP", "50.00", true, "ANOMALY"),
                prediction(run, "SIDEWAYS", "75.00", false, null)
        ));

        RegimeRobustnessSummaryResponse response = service.summarizeRobustness(20L, null);

        assertThat(response.reviewLabel()).isEqualTo("needs_review");
        assertThat(response.qualitySummary().lowConfidenceWindowCount()).isEqualTo(1);
        assertThat(response.reviewReasons()).anyMatch(reason -> reason.contains("confidence review threshold"));
        assertThat(response.reviewReasons()).anyMatch(reason -> reason.contains("anomaly warnings"));
    }

    private List<MarketCandle> candlesDescending(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> candle(count - index))
                .toList();
    }

    private MarketCandle candle(int hour) {
        BigDecimal close = new BigDecimal("100").add(new BigDecimal(hour));
        return new MarketCandle(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds(hour * 3600L),
                close,
                close.add(BigDecimal.ONE),
                close.subtract(BigDecimal.ONE),
                close,
                new BigDecimal("1000")
        );
    }

    private BacktestTrade trade(BacktestRun run, String entryTime, String netPnl, String returnPercent) {
        BacktestTrade trade = new BacktestTrade(
                run,
                TradeSide.LONG,
                Instant.parse(entryTime),
                new BigDecimal("100"),
                BigDecimal.ONE
        );
        trade.setNetPnl(new BigDecimal(netPnl));
        trade.setReturnPercent(new BigDecimal(returnPercent));
        return trade;
    }

    private RegimeRun savedRun(Long id, String effectiveMode, String artifactIdentifier, String createdAt, int pointCount) {
        RegimeRun run = new RegimeRun(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
                20,
                5,
                true
        );
        ReflectionTestUtils.setField(run, "id", id);
        ReflectionTestUtils.setField(run, "requestedMode", "auto");
        ReflectionTestUtils.setField(run, "effectiveMode", effectiveMode);
        ReflectionTestUtils.setField(run, "classifierSource", effectiveMode);
        ReflectionTestUtils.setField(run, "featureVersion", "torch-market-regime-features/v1");
        ReflectionTestUtils.setField(run, "artifactIdentifier", artifactIdentifier);
        ReflectionTestUtils.setField(run, "pointCount", pointCount);
        ReflectionTestUtils.setField(run, "createdAt", Instant.parse(createdAt));
        return run;
    }

    private RegimePrediction prediction(
            RegimeRun run,
            String regimeLabel,
            String confidence,
            boolean disagreesWithBaseline,
            String anomalyLabel
    ) {
        return new RegimePrediction(
                run,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T20:00:00Z"),
                regimeLabel,
                new BigDecimal(confidence),
                "[]",
                "{}",
                anomalyLabel == null ? null : BigDecimal.ONE,
                anomalyLabel,
                null,
                "SIDEWAYS",
                new BigDecimal("50.00"),
                disagreesWithBaseline
        );
    }

    private MlMarketRegimeResponse response() {
        return new MlMarketRegimeResponse(
                "TRENDING_UP",
                new BigDecimal("72.50"),
                List.of("Price is rising and remains above its sequence average."),
                new MlMarketRegimeFeatures(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                ),
                "rules",
                "rules",
                null,
                "torch-market-regime-features/v1",
                20,
                null
        );
    }

    private MlMarketRegimeDiagnosticsResponse diagnosticsResponse() {
        return new MlMarketRegimeDiagnosticsResponse(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T19:00:00Z"),
                "TRENDING_UP",
                new BigDecimal("80.00"),
                "TRENDING_UP",
                new BigDecimal("75.00"),
                false,
                "attribution",
                List.of("Price is rising."),
                List.of(new MlAttentionTimestepEvidence(
                        Instant.parse("2024-01-01T19:00:00Z"),
                        new BigDecimal("1.00"),
                        new BigDecimal("119"),
                        new BigDecimal("0.85")
                )),
                List.of(new MlFeatureEvidence("trendSlopePercent", BigDecimal.ONE, BigDecimal.ONE)),
                "rules",
                "rules",
                null,
                "torch-market-regime-features/v1",
                20,
                null
        );
    }

    private MlMarketRegimeStatusResponse status() {
        return new MlMarketRegimeStatusResponse(
                "auto",
                "rules",
                "rules",
                true,
                false,
                false,
                null,
                null,
                "torch-market-regime-features/v1",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
