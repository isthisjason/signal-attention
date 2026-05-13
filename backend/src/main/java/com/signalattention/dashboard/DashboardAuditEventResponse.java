package com.signalattention.dashboard;

import com.signalattention.audit.AuditEvent;
import java.time.Instant;

public record DashboardAuditEventResponse(
        Long id,
        String entityType,
        String entityId,
        String action,
        String message,
        Instant createdAt
) {

    public static DashboardAuditEventResponse from(AuditEvent event) {
        return new DashboardAuditEventResponse(
                event.getId(),
                event.getEntityType(),
                event.getEntityId(),
                event.getAction(),
                event.getMessage(),
                event.getCreatedAt()
        );
    }
}
