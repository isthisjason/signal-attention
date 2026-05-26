package com.signalattention.dashboard;

import com.signalattention.audit.AuditEventRepository;
import com.signalattention.backtesting.BacktestRun;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.papertrading.PaperSessionRepository;
import com.signalattention.papertrading.PaperSessionStatus;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final BigDecimal MEDIUM_DRAWDOWN_THRESHOLD = new BigDecimal("10");
    private static final BigDecimal HIGH_DRAWDOWN_THRESHOLD = new BigDecimal("20");

    private final StrategyRepository strategyRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final PaperSessionRepository paperSessionRepository;
    private final AuditEventRepository auditEventRepository;

    public DashboardService(
            StrategyRepository strategyRepository,
            BacktestRunRepository backtestRunRepository,
            PaperSessionRepository paperSessionRepository,
            AuditEventRepository auditEventRepository
    ) {
        this.strategyRepository = strategyRepository;
        this.backtestRunRepository = backtestRunRepository;
        this.paperSessionRepository = paperSessionRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        return new DashboardSummaryResponse(
                strategyRepository.count(),
                backtestRunRepository.count(),
                paperSessionRepository.countByStatus(PaperSessionStatus.RUNNING),
                backtestRunRepository.findFirstByOrderByCreatedAtDesc().map(DashboardBacktestSummaryResponse::from).orElse(null),
                auditEventRepository.findTop10ByOrderByCreatedAtDesc().stream()
                        .map(DashboardAuditEventResponse::from)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<DashboardStrategyPerformanceResponse> getStrategyPerformance() {
        return strategyRepository.findAll().stream()
                .map(this::strategyPerformance)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardRiskAlertResponse> getRiskAlerts() {
        return backtestRunRepository.findAll().stream()
                .filter(run -> run.getMaxDrawdown() != null)
                .filter(run -> run.getMaxDrawdown().compareTo(MEDIUM_DRAWDOWN_THRESHOLD) >= 0)
                .map(this::drawdownAlert)
                .toList();
    }

    private DashboardStrategyPerformanceResponse strategyPerformance(Strategy strategy) {
        BacktestRun latestBacktest = backtestRunRepository.findFirstByStrategyIdOrderByCreatedAtDesc(strategy.getId()).orElse(null);
        return DashboardStrategyPerformanceResponse.from(
                strategy,
                latestBacktest,
                paperSessionRepository.countByStrategyId(strategy.getId())
        );
    }

    private DashboardRiskAlertResponse drawdownAlert(BacktestRun run) {
        DashboardAlertSeverity severity = run.getMaxDrawdown().compareTo(HIGH_DRAWDOWN_THRESHOLD) >= 0
                ? DashboardAlertSeverity.HIGH
                : DashboardAlertSeverity.MEDIUM;
        return new DashboardRiskAlertResponse(
                severity,
                "DRAWDOWN",
                "BACKTEST",
                String.valueOf(run.getId()),
                "Backtest drawdown reached " + run.getMaxDrawdown().stripTrailingZeros().toPlainString() + "%.",
                run.getCreatedAt()
        );
    }
}
