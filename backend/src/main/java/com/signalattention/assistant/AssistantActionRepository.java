package com.signalattention.assistant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantActionRepository extends JpaRepository<AssistantAction, Long> {

    List<AssistantAction> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
