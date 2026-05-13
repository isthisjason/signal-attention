package com.signalattention.audit;

import java.time.Instant;

public record AuditEventResponse(
        Long id,
        String entityType,
        String entityId,
        String action,
        String message,
        String metadataJson,
        Instant createdAt
) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getEntityType(),
                event.getEntityId(),
                event.getAction(),
                event.getMessage(),
                event.getMetadataJson(),
                event.getCreatedAt()
        );
    }
}
