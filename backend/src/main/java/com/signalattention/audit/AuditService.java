package com.signalattention.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(String entityType, String entityId, String action, String message, String metadataJson) {
        AuditEvent event = new AuditEvent(entityType, entityId, action, message, metadataJson);
        return auditEventRepository.save(event);
    }
}
