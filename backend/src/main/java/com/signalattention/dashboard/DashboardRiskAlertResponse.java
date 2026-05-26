package com.signalattention.dashboard;

import java.time.Instant;

public record DashboardRiskAlertResponse(
        DashboardAlertSeverity severity,
        String category,
        String entityType,
        String entityId,
        String message,
        Instant createdAt
) {
}
