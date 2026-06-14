package com.signalattention.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantSessionRepository extends JpaRepository<AssistantSession, Long> {
}
