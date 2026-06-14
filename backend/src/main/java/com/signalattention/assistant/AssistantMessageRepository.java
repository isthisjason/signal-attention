package com.signalattention.assistant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

    List<AssistantMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
