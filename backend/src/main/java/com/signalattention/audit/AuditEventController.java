package com.signalattention.audit;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditEventController {

    private final AuditEventQueryService auditEventQueryService;

    public AuditEventController(AuditEventQueryService auditEventQueryService) {
        this.auditEventQueryService = auditEventQueryService;
    }

    @GetMapping("/api/audit-events")
    public List<AuditEventResponse> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) Integer limit
    ) {
        return auditEventQueryService.list(entityType, entityId, limit);
    }
}
