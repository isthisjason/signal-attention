package com.signalattention.audit;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop10ByOrderByCreatedAtDesc();

    List<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditEvent> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    List<AuditEvent> findByEntityIdOrderByCreatedAtDesc(String entityId, Pageable pageable);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId, Pageable pageable);
}
