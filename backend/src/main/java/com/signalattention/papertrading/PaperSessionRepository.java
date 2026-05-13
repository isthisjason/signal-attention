package com.signalattention.papertrading;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperSessionRepository extends JpaRepository<PaperSession, Long> {

    List<PaperSession> findByStrategyIdOrderByCreatedAtDesc(Long strategyId);

    long countByStatus(PaperSessionStatus status);
}
