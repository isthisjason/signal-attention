package com.signalattention.dashboard;

import java.util.List;

public record DashboardSummaryResponse(
        long strategyCount,
        long backtestCount,
        long activePaperSessionCount,
        DashboardBacktestSummaryResponse latestBacktest,
        List<DashboardAuditEventResponse> recentAuditEvents
) {
}
