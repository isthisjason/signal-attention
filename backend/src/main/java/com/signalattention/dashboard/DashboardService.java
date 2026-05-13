package com.signalattention.dashboard;

import com.signalattention.audit.AuditEventRepository;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.papertrading.PaperSessionRepository;
import com.signalattention.papertrading.PaperSessionStatus;
import com.signalattention.strategies.StrategyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

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
}
