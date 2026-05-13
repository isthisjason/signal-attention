package com.signalattention.papertrading;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperOrderRepository extends JpaRepository<PaperOrder, Long> {

    List<PaperOrder> findByPaperSessionIdOrderByCreatedAtAsc(Long paperSessionId);
}
