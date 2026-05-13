package com.signalattention.papertrading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.indicators.CrossoverSignal;
import com.signalattention.indicators.CrossoverSignalType;
import com.signalattention.indicators.SmaCrossoverDetector;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import com.signalattention.strategies.StrategyStatus;
import com.signalattention.strategies.StrategyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaperTradingServiceLifecycleTests {

    @Mock
    private StrategyRepository strategyRepository;
    @Mock
    private PaperSessionRepository sessionRepository;
    @Mock
    private PaperOrderRepository orderRepository;
    @Mock
    private PaperPositionRepository positionRepository;
    @Mock
    private PaperOrderExecutor orderExecutor;
    @Mock
    private MarketCandleRepository marketCandleRepository;
    @Mock
    private SmaCrossoverDetector smaCrossoverDetector;
    @Mock
    private AuditService auditService;

    private PaperTradingService service;

    @BeforeEach
    void setUp() {
        service = new PaperTradingService(
                strategyRepository,
                sessionRepository,
                orderRepository,
                positionRepository,
                orderExecutor,
                marketCandleRepository,
                smaCrossoverDetector,
                auditService,
                new ObjectMapper()
        );
    }

    @Test
    void createSessionPersistsCreatedSession() {
        when(strategyRepository.findById(1L)).thenReturn(Optional.of(strategy()));
        when(sessionRepository.save(any(PaperSession.class))).thenAnswer(invocation -> {
            PaperSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 10L);
            return session;
        });

        PaperSessionResponse response = service.createSession(1L, new PaperSessionCreateRequest(new BigDecimal("10000")));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(PaperSessionStatus.CREATED);
        assertThat(response.cashBalance()).isEqualByComparingTo("10000.00000000");
        verify(auditService).record(eq("PAPER_TRADING"), eq("10"), eq("PAPER_SESSION_CREATED"), eq("Paper session created"), contains("CREATED"));
    }

    @Test
    void createSessionMissingStrategyThrowsNotFound() {
        when(strategyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSession(99L, new PaperSessionCreateRequest(BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Strategy not found: 99");
    }

    @Test
    void startAndStopSessionMoveThroughValidStates() {
        PaperSession session = session(PaperSessionStatus.CREATED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        PaperSessionResponse started = service.startSession(10L);
        PaperSessionResponse stopped = service.stopSession(10L);

        assertThat(started.status()).isEqualTo(PaperSessionStatus.RUNNING);
        assertThat(stopped.status()).isEqualTo(PaperSessionStatus.STOPPED);
        verify(auditService).record(eq("PAPER_TRADING"), eq("10"), eq("PAPER_SESSION_STARTED"), eq("Paper session started"), contains("RUNNING"));
        verify(auditService).record(eq("PAPER_TRADING"), eq("10"), eq("PAPER_SESSION_STOPPED"), eq("Paper session stopped"), contains("STOPPED"));
    }

    @Test
    void startRejectsStoppedSession() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(PaperSessionStatus.STOPPED)));

        assertThatThrownBy(() -> service.startSession(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only created paper sessions can be started");
    }

    @Test
    void getSessionReturnsSession() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(PaperSessionStatus.RUNNING)));

        PaperSessionResponse response = service.getSession(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(PaperSessionStatus.RUNNING);
    }

    @Test
    void getStrategySessionsRequiresExistingStrategy() {
        when(strategyRepository.existsById(1L)).thenReturn(true);
        when(sessionRepository.findByStrategyIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(session(PaperSessionStatus.CREATED)));

        List<PaperSessionResponse> responses = service.getStrategySessions(1L);

        assertThat(responses).hasSize(1);
    }

    @Test
    void getStrategySessionsMissingStrategyThrowsNotFound() {
        when(strategyRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getStrategySessions(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Strategy not found: 99");
    }

    @Test
    void replayProcessesSmaSignalsThroughOrderExecutor() {
        PaperSession session = session(PaperSessionStatus.RUNNING, strategyWithRules());
        List<MarketCandle> candles = List.of(
                candle("2024-01-01T00:00:00Z", "100"),
                candle("2024-01-01T01:00:00Z", "99"),
                candle("2024-01-01T02:00:00Z", "101"),
                candle("2024-01-01T03:00:00Z", "104")
        );
        PaperOrder filled = new PaperOrder(session, PaperOrderSide.BUY, PaperOrderStatus.FILLED, "BTC-USD", new BigDecimal("48.07692308"), new BigDecimal("104"), new BigDecimal("5000"), null);
        ReflectionTestUtils.setField(filled, "id", 50L);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                eq("BTC-USD"),
                eq("1h"),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(candles);
        when(smaCrossoverDetector.detect(List.of(
                new BigDecimal("100"),
                new BigDecimal("99"),
                new BigDecimal("101"),
                new BigDecimal("104")
        ), 2, 3)).thenReturn(List.of(new CrossoverSignal(3, CrossoverSignalType.BULLISH_CROSSOVER)));
        when(positionRepository.findFirstByPaperSessionIdAndSymbolAndStatusOrderByOpenedAtAsc(10L, "BTC-USD", PaperPositionStatus.OPEN))
                .thenReturn(Optional.empty());
        when(orderExecutor.execute(eq(session), any(PaperOrderRequest.class))).thenReturn(filled);

        PaperSessionReplayResponse response = service.replay(10L, new PaperSessionReplayRequest(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T03:00:00Z"),
                null
        ));

        assertThat(response.candlesRead()).isEqualTo(4);
        assertThat(response.signalsProcessed()).isEqualTo(1);
        assertThat(response.filledOrders()).isEqualTo(1);
        assertThat(response.rejectedOrders()).isZero();
    }

    @Test
    void replayRejectsStoppedSession() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(PaperSessionStatus.STOPPED, strategyWithRules())));

        assertThatThrownBy(() -> service.replay(10L, new PaperSessionReplayRequest(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T03:00:00Z"),
                null
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Paper session must be running to replay candles");
    }

    @Test
    void summaryPricesOpenPositionsAndCalculatesEquity() {
        PaperSession session = session(PaperSessionStatus.RUNNING);
        PaperPosition open = position(session, PaperPositionStatus.OPEN, "2", "100");
        PaperPosition closed = position(session, PaperPositionStatus.CLOSED, "1.5", "80");
        closed.setExitPrice(new BigDecimal("100"));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(positionRepository.findByPaperSessionIdAndStatusOrderByOpenedAtAsc(10L, PaperPositionStatus.OPEN)).thenReturn(List.of(open));
        when(positionRepository.findByPaperSessionIdOrderByOpenedAtAsc(10L)).thenReturn(List.of(open, closed));
        when(marketCandleRepository.findFirstBySymbolAndTimeframeOrderByOpenTimeDesc("BTC-USD", "1h"))
                .thenReturn(candle("2024-01-01T03:00:00Z", "125"));

        PaperSessionSummaryResponse response = service.getSummary(10L);

        assertThat(response.openPositionValue()).isEqualByComparingTo("250.00000000");
        assertThat(response.unrealizedPnl()).isEqualByComparingTo("50.00000000");
        assertThat(response.realizedPnl()).isEqualByComparingTo("30.00000000");
        assertThat(response.totalEquity()).isEqualByComparingTo("10250.00000000");
        assertThat(response.hasUnpricedPositions()).isFalse();
    }

    @Test
    void summaryMarksMissingCandlePricesAsUnpriced() {
        PaperSession session = session(PaperSessionStatus.RUNNING);
        PaperPosition open = position(session, PaperPositionStatus.OPEN, "2", "100");
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(positionRepository.findByPaperSessionIdAndStatusOrderByOpenedAtAsc(10L, PaperPositionStatus.OPEN)).thenReturn(List.of(open));
        when(positionRepository.findByPaperSessionIdOrderByOpenedAtAsc(10L)).thenReturn(List.of(open));

        PaperSessionSummaryResponse response = service.getSummary(10L);

        assertThat(response.hasUnpricedPositions()).isTrue();
        assertThat(response.openPositions()).singleElement().satisfies(mark -> {
            assertThat(mark.priced()).isFalse();
            assertThat(mark.markPrice()).isNull();
        });
    }

    @Test
    void replayHonorsMaxCandlesAndAllowsNoGeneratedSignals() {
        PaperSession session = session(PaperSessionStatus.RUNNING, strategyWithRules());
        List<MarketCandle> candles = List.of(
                candle("2024-01-01T00:00:00Z", "100"),
                candle("2024-01-01T01:00:00Z", "101"),
                candle("2024-01-01T02:00:00Z", "102"),
                candle("2024-01-01T03:00:00Z", "103")
        );
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                eq("BTC-USD"),
                eq("1h"),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(candles);
        when(smaCrossoverDetector.detect(List.of(
                new BigDecimal("100"),
                new BigDecimal("101"),
                new BigDecimal("102")
        ), 2, 3)).thenReturn(List.of());

        PaperSessionReplayResponse response = service.replay(10L, new PaperSessionReplayRequest(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T03:00:00Z"),
                3
        ));

        assertThat(response.candlesRead()).isEqualTo(3);
        assertThat(response.signalsProcessed()).isZero();
        assertThat(response.filledOrders()).isZero();
    }

    private PaperSession session(PaperSessionStatus status) {
        return session(status, strategy());
    }

    private PaperSession session(PaperSessionStatus status, Strategy strategy) {
        PaperSession session = new PaperSession(strategy, new BigDecimal("10000"));
        ReflectionTestUtils.setField(session, "id", 10L);
        session.setStatus(status);
        return session;
    }

    private Strategy strategy() {
        Strategy strategy = new Strategy("BTC SMA", "BTC-USD", "1h", StrategyType.SMA_CROSSOVER, "{}", StrategyStatus.ACTIVE);
        ReflectionTestUtils.setField(strategy, "id", 1L);
        return strategy;
    }

    private Strategy strategyWithRules() {
        Strategy strategy = new Strategy(
                "BTC SMA",
                "BTC-USD",
                "1h",
                StrategyType.SMA_CROSSOVER,
                "{\"shortWindow\":2,\"longWindow\":3,\"initialBalance\":10000,\"feePercent\":0.1,\"positionSizePercent\":50}",
                StrategyStatus.ACTIVE
        );
        ReflectionTestUtils.setField(strategy, "id", 1L);
        return strategy;
    }

    private MarketCandle candle(String openTime, String close) {
        return new MarketCandle(
                "BTC-USD",
                "1h",
                Instant.parse(openTime),
                new BigDecimal(close),
                new BigDecimal(close),
                new BigDecimal(close),
                new BigDecimal(close),
                BigDecimal.ONE
        );
    }

    private PaperPosition position(PaperSession session, PaperPositionStatus status, String quantity, String entryPrice) {
        PaperPosition position = new PaperPosition(session, "BTC-USD", new BigDecimal(quantity), new BigDecimal(entryPrice));
        ReflectionTestUtils.setField(position, "id", 30L);
        position.setStatus(status);
        return position;
    }
}
