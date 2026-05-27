package com.signalattention.backtesting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
import com.signalattention.indicators.SmaCalculator;
import com.signalattention.indicators.SmaCrossoverDetector;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.ml.MlRiskClient;
import com.signalattention.ml.MlStrategyRiskRequest;
import com.signalattention.ml.MlStrategyRiskResponse;
import com.signalattention.strategies.SmaCrossoverRulesRequest;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import com.signalattention.strategies.StrategyStatus;
import com.signalattention.strategies.StrategyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BacktestServiceTests {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T06:00:00Z");

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private MarketCandleRepository marketCandleRepository;

    @Mock
    private BacktestRunRepository backtestRunRepository;

    @Mock
    private BacktestTradeRepository backtestTradeRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private MlRiskClient mlRiskClient;

    private BacktestService backtestService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        backtestService = new BacktestService(
                strategyRepository,
                marketCandleRepository,
                backtestRunRepository,
                backtestTradeRepository,
                new SmaCrossoverDetector(new SmaCalculator()),
                auditService,
                objectMapper,
                mlRiskClient
        );
    }

    @Test
    void runBacktestPersistsRunTradesAndMetrics() throws Exception {
        Strategy strategy = strategy();
        when(strategyRepository.findById(1L)).thenReturn(Optional.of(strategy));
        when(marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc("BTC-USD", "1h", START, END))
                .thenReturn(candles("10", "9", "8", "11", "12", "7", "6"));
        when(backtestRunRepository.save(any(BacktestRun.class))).thenAnswer(invocation -> {
            BacktestRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                ReflectionTestUtils.setField(run, "id", 10L);
            }
            return run;
        });
        when(backtestTradeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BacktestRunResponse response = backtestService.runBacktest(1L, new BacktestRequest(START, END, null, null, null));

        assertThat(response.status()).isEqualTo(BacktestStatus.COMPLETED);
        assertThat(response.tradeCount()).isEqualTo(1);
        assertThat(response.finalBalance()).isEqualByComparingTo("636.36363637");
        assertThat(response.totalReturn()).isEqualByComparingTo("-36.363636");
        assertThat(response.maxDrawdown()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.volatility()).isGreaterThan(BigDecimal.ZERO);

        ArgumentCaptor<Iterable<BacktestTrade>> tradesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(backtestTradeRepository).saveAll(tradesCaptor.capture());
        List<BacktestTrade> trades = (List<BacktestTrade>) tradesCaptor.getValue();
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).getEntryPrice()).isEqualByComparingTo("11.00000000");
        assertThat(trades.get(0).getExitPrice()).isEqualByComparingTo("7.00000000");
        assertThat(trades.get(0).getNetPnl()).isNegative();
        verify(auditService).record(eq("BACKTEST"), eq("10"), eq("BACKTEST_COMPLETED"), eq("Backtest completed"), any());
    }

    @Test
    void runBacktestRejectsInvalidDateRange() {
        BacktestRequest request = new BacktestRequest(END, START, null, null, null);

        assertThatThrownBy(() -> backtestService.runBacktest(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("startDate must be before or equal to endDate");
    }

    @Test
    void runBacktestRejectsEmptyCandles() throws Exception {
        Strategy strategy = strategy();
        when(strategyRepository.findById(1L)).thenReturn(Optional.of(strategy));
        when(marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc("BTC-USD", "1h", START, END))
                .thenReturn(List.of());

        assertThatThrownBy(() -> backtestService.runBacktest(1L, new BacktestRequest(START, END, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("No candles found for requested backtest range");
        verify(auditService).record(eq("BACKTEST"), eq("1"), eq("BACKTEST_FAILED"), any(), any());
    }

    @Test
    void runBacktestRejectsInsufficientCandles() throws Exception {
        Strategy strategy = strategy();
        when(strategyRepository.findById(1L)).thenReturn(Optional.of(strategy));
        when(marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc("BTC-USD", "1h", START, END))
                .thenReturn(candles("10", "11"));

        assertThatThrownBy(() -> backtestService.runBacktest(1L, new BacktestRequest(START, END, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Not enough candles for long SMA window");
    }

    @Test
    void scoreMlRiskPersistsRiskFields() throws Exception {
        BacktestRun run = completedRun();
        MlStrategyRiskResponse mlResponse = new MlStrategyRiskResponse(
                new BigDecimal("42.50"),
                "MEDIUM_RISK",
                List.of("Limited trade count weakens confidence.")
        );
        when(backtestRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(mlRiskClient.scoreStrategyRisk(any(MlStrategyRiskRequest.class))).thenReturn(mlResponse);
        when(backtestRunRepository.save(any(BacktestRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MlStrategyRiskResponse response = backtestService.scoreMlRisk(10L);

        assertThat(response.riskLabel()).isEqualTo("MEDIUM_RISK");
        assertThat(run.getMlRiskScore()).isEqualByComparingTo("42.50");
        assertThat(run.getMlRiskLabel()).isEqualTo("MEDIUM_RISK");
        verify(backtestRunRepository).save(run);
        verify(auditService).record(eq("BACKTEST"), eq("10"), eq("ML_RISK_SCORE_COMPLETED"), eq("ML risk score completed"), any());
    }

    @Test
    void getEquitySeriesBuildsPointsFromClosedTrades() throws Exception {
        BacktestRun run = completedRun();
        BacktestTrade firstTrade = closedTrade(run, START.plusSeconds(3600), START.plusSeconds(7200), "100");
        BacktestTrade secondTrade = closedTrade(run, START.plusSeconds(10800), START.plusSeconds(14400), "-50");
        when(backtestRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(backtestTradeRepository.findByBacktestRunIdOrderByEntryTimeAsc(10L)).thenReturn(List.of(secondTrade, firstTrade));

        List<BacktestEquityPointResponse> series = backtestService.getEquitySeries(10L);

        assertThat(series).extracting(BacktestEquityPointResponse::timestamp)
                .containsExactly(START, START.plusSeconds(7200), START.plusSeconds(14400), END);
        assertThat(series).extracting(BacktestEquityPointResponse::equity)
                .containsExactly(
                        new BigDecimal("1000.00000000"),
                        new BigDecimal("1100.00000000"),
                        new BigDecimal("1050.00000000"),
                        new BigDecimal("1100.00000000")
                );
    }

    @Test
    void getDrawdownSeriesBuildsPercentagesFromEquityPeaks() throws Exception {
        BacktestRun run = completedRun();
        run.setFinalBalance(new BigDecimal("1050"));
        BacktestTrade firstTrade = closedTrade(run, START.plusSeconds(3600), START.plusSeconds(7200), "100");
        BacktestTrade secondTrade = closedTrade(run, START.plusSeconds(10800), START.plusSeconds(14400), "-50");
        when(backtestRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(backtestTradeRepository.findByBacktestRunIdOrderByEntryTimeAsc(10L)).thenReturn(List.of(firstTrade, secondTrade));

        List<BacktestDrawdownPointResponse> series = backtestService.getDrawdownSeries(10L);

        assertThat(series).extracting(BacktestDrawdownPointResponse::timestamp)
                .containsExactly(START, START.plusSeconds(7200), START.plusSeconds(14400), END);
        assertThat(series).extracting(BacktestDrawdownPointResponse::drawdownPercent)
                .containsExactly(
                        new BigDecimal("0.000000"),
                        new BigDecimal("0.000000"),
                        new BigDecimal("4.545455"),
                        new BigDecimal("4.545455")
                );
    }

    @Test
    void scoreMlRiskAuditsFailures() throws Exception {
        BacktestRun run = completedRun();
        RuntimeException failure = new RuntimeException("ML service unavailable");
        when(backtestRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(mlRiskClient.scoreStrategyRisk(any(MlStrategyRiskRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> backtestService.scoreMlRisk(10L))
                .isSameAs(failure);
        verify(auditService).record(eq("BACKTEST"), eq("10"), eq("ML_RISK_SCORE_FAILED"), eq("ML service unavailable"), any());
    }

    private Strategy strategy() throws Exception {
        SmaCrossoverRulesRequest rules = new SmaCrossoverRulesRequest(
                2,
                3,
                new BigDecimal("1000"),
                new BigDecimal("0"),
                new BigDecimal("100")
        );
        Strategy strategy = new Strategy(
                "BTC SMA",
                "BTC-USD",
                "1h",
                StrategyType.SMA_CROSSOVER,
                objectMapper.writeValueAsString(rules),
                StrategyStatus.ACTIVE
        );
        ReflectionTestUtils.setField(strategy, "id", 1L);
        return strategy;
    }

    private BacktestRun completedRun() throws Exception {
        BacktestRun run = new BacktestRun(strategy(), START, END, new BigDecimal("1000"), BacktestStatus.COMPLETED);
        ReflectionTestUtils.setField(run, "id", 10L);
        run.setFinalBalance(new BigDecimal("1100"));
        run.setTotalReturn(new BigDecimal("10"));
        run.setMaxDrawdown(new BigDecimal("5"));
        run.setWinRate(new BigDecimal("50"));
        run.setProfitFactor(new BigDecimal("1.4"));
        run.setTradeCount(8);
        run.setAverageTradeReturn(new BigDecimal("1.2"));
        run.setFeeDrag(new BigDecimal("4"));
        run.setVolatility(new BigDecimal("2.5"));
        return run;
    }

    private BacktestTrade closedTrade(BacktestRun run, Instant entryTime, Instant exitTime, String netPnl) {
        BacktestTrade trade = new BacktestTrade(run, TradeSide.LONG, entryTime, new BigDecimal("10"), BigDecimal.ONE);
        trade.setExitTime(exitTime);
        trade.setExitPrice(new BigDecimal("10"));
        trade.setGrossPnl(new BigDecimal(netPnl));
        trade.setFees(BigDecimal.ZERO);
        trade.setNetPnl(new BigDecimal(netPnl));
        trade.setReturnPercent(BigDecimal.ZERO);
        return trade;
    }

    private List<MarketCandle> candles(String... closes) {
        return java.util.stream.IntStream.range(0, closes.length)
                .mapToObj(index -> new MarketCandle(
                        "BTC-USD",
                        "1h",
                        START.plusSeconds(3600L * index),
                        new BigDecimal(closes[index]),
                        new BigDecimal(closes[index]),
                        new BigDecimal(closes[index]),
                        new BigDecimal(closes[index]),
                        BigDecimal.ONE
                ))
                .toList();
    }
}
