package com.signalattention.audit;

import com.signalattention.common.BadRequestException;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AuditEventRepository auditEventRepository;

    public AuditEventQueryService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> list(String entityType, String entityId, Integer limit) {
        PageRequest pageRequest = PageRequest.of(0, normalizedLimit(limit));
        List<AuditEvent> events;
        // Choose the most specific repository query available so filters stay database-side.
        if (hasText(entityType) && hasText(entityId)) {
            events = auditEventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageRequest);
        } else if (hasText(entityType)) {
            events = auditEventRepository.findByEntityTypeOrderByCreatedAtDesc(entityType, pageRequest);
        } else if (hasText(entityId)) {
            events = auditEventRepository.findByEntityIdOrderByCreatedAtDesc(entityId, pageRequest);
        } else {
            events = auditEventRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        }
        return events.stream().map(AuditEventResponse::from).toList();
    }

    private int normalizedLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new BadRequestException("limit must be at least 1");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
