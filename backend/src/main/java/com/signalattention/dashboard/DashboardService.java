package com.signalattention.dashboard;

import com.signalattention.audit.AuditEventRepository;
import com.signalattention.backtesting.BacktestRun;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.papertrading.PaperSessionRepository;
import com.signalattention.papertrading.PaperSessionStatus;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
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
        // Summary is intentionally cheap: counts plus the latest backtest and latest audit events.
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
        // Each strategy row shows the latest backtest and paper-session count for quick comparison.
        return strategyRepository.findAll().stream()
                .map(this::strategyPerformance)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardRiskAlertResponse> getRiskAlerts() {
        // Alerts are derived from saved backtest results; there is no separate alert table yet.
        return backtestRunRepository.findAll().stream()
                .flatMap(run -> Stream.of(drawdownAlert(run), mlRiskAlert(run)))
                .filter(alert -> alert != null)
                .sorted(Comparator
                        .comparing(DashboardRiskAlertResponse::severity)
                        .thenComparing(DashboardRiskAlertResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
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
        if (run.getMaxDrawdown() == null || run.getMaxDrawdown().compareTo(MEDIUM_DRAWDOWN_THRESHOLD) < 0) {
            return null;
        }
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

    private DashboardRiskAlertResponse mlRiskAlert(BacktestRun run) {
        DashboardAlertSeverity severity = switch (String.valueOf(run.getMlRiskLabel())) {
            case "LIKELY_OVERFIT", "HIGH_RISK" -> DashboardAlertSeverity.HIGH;
            case "MEDIUM_RISK" -> DashboardAlertSeverity.MEDIUM;
            default -> null;
        };
        if (severity == null) {
            return null;
        }
        return new DashboardRiskAlertResponse(
                severity,
                "ML_RISK",
                "BACKTEST",
                String.valueOf(run.getId()),
                "Backtest ML risk label is " + run.getMlRiskLabel() + ".",
                run.getCreatedAt()
        );
    }
}
