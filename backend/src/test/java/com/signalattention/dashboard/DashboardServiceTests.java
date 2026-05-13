package com.signalattention.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.signalattention.audit.AuditEvent;
import com.signalattention.audit.AuditEventRepository;
import com.signalattention.backtesting.BacktestRun;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.backtesting.BacktestStatus;
import com.signalattention.papertrading.PaperSessionRepository;
import com.signalattention.papertrading.PaperSessionStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

    @Mock
    private StrategyRepository strategyRepository;
    @Mock
    private BacktestRunRepository backtestRunRepository;
    @Mock
    private PaperSessionRepository paperSessionRepository;
    @Mock
    private AuditEventRepository auditEventRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(strategyRepository, backtestRunRepository, paperSessionRepository, auditEventRepository);
    }

    @Test
    void getSummaryReturnsCountsLatestBacktestAndRecentAuditEvents() {
        when(strategyRepository.count()).thenReturn(3L);
        when(backtestRunRepository.count()).thenReturn(7L);
        when(paperSessionRepository.countByStatus(PaperSessionStatus.RUNNING)).thenReturn(2L);
        when(backtestRunRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(backtestRun()));
        when(auditEventRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(auditEvent()));

        DashboardSummaryResponse response = service.getSummary();

        assertThat(response.strategyCount()).isEqualTo(3);
        assertThat(response.backtestCount()).isEqualTo(7);
        assertThat(response.activePaperSessionCount()).isEqualTo(2);
        assertThat(response.latestBacktest().mlRiskLabel()).isEqualTo("LOW_RISK");
        assertThat(response.recentAuditEvents()).extracting(DashboardAuditEventResponse::action).containsExactly("BACKTEST_COMPLETED");
    }

    @Test
    void getSummaryAllowsEmptyLatestBacktestAndAuditEvents() {
        when(backtestRunRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        when(auditEventRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of());

        DashboardSummaryResponse response = service.getSummary();

        assertThat(response.latestBacktest()).isNull();
        assertThat(response.recentAuditEvents()).isEmpty();
    }

    @Test
    void getStrategyPerformanceReturnsEmptyListWhenNoStrategiesExist() {
        when(strategyRepository.findAll()).thenReturn(List.of());

        List<DashboardStrategyPerformanceResponse> responses = service.getStrategyPerformance();

        assertThat(responses).isEmpty();
    }

    @Test
    void getStrategyPerformanceAllowsStrategiesWithoutBacktests() {
        Strategy strategy = strategy();
        when(strategyRepository.findAll()).thenReturn(List.of(strategy));
        when(backtestRunRepository.findFirstByStrategyIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        when(paperSessionRepository.countByStrategyId(1L)).thenReturn(2L);

        DashboardStrategyPerformanceResponse response = service.getStrategyPerformance().get(0);

        assertThat(response.strategyId()).isEqualTo(1L);
        assertThat(response.latestBacktestId()).isNull();
        assertThat(response.paperSessionCount()).isEqualTo(2);
    }

    @Test
    void getStrategyPerformanceIncludesLatestScoredBacktest() {
        Strategy strategy = strategy();
        when(strategyRepository.findAll()).thenReturn(List.of(strategy));
        when(backtestRunRepository.findFirstByStrategyIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(backtestRun()));

        DashboardStrategyPerformanceResponse response = service.getStrategyPerformance().get(0);

        assertThat(response.latestBacktestId()).isEqualTo(10L);
        assertThat(response.latestTotalReturn()).isEqualByComparingTo("5.5");
        assertThat(response.latestMlRiskLabel()).isEqualTo("LOW_RISK");
    }

    private BacktestRun backtestRun() {
        BacktestRun run = new BacktestRun(strategy(), Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"), new BigDecimal("10000"), BacktestStatus.COMPLETED);
        ReflectionTestUtils.setField(run, "id", 10L);
        ReflectionTestUtils.setField(run, "createdAt", Instant.parse("2024-01-02T00:00:00Z"));
        run.setTotalReturn(new BigDecimal("5.5"));
        run.setMaxDrawdown(new BigDecimal("1.2"));
        run.setTradeCount(4);
        run.setMlRiskScore(new BigDecimal("20"));
        run.setMlRiskLabel("LOW_RISK");
        return run;
    }

    private AuditEvent auditEvent() {
        AuditEvent event = new AuditEvent("BACKTEST", "10", "BACKTEST_COMPLETED", "Backtest completed", "{}");
        ReflectionTestUtils.setField(event, "id", 20L);
        ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2024-01-02T00:00:00Z"));
        return event;
    }

    private Strategy strategy() {
        Strategy strategy = new Strategy("BTC SMA", "BTC-USD", "1h", StrategyType.SMA_CROSSOVER, "{}", StrategyStatus.ACTIVE);
        ReflectionTestUtils.setField(strategy, "id", 1L);
        return strategy;
    }
}
