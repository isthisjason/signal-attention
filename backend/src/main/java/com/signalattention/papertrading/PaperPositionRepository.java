package com.signalattention.papertrading;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperPositionRepository extends JpaRepository<PaperPosition, Long> {

    List<PaperPosition> findByPaperSessionIdOrderByOpenedAtAsc(Long paperSessionId);

    List<PaperPosition> findByPaperSessionIdAndStatusOrderByOpenedAtAsc(Long paperSessionId, PaperPositionStatus status);

    Optional<PaperPosition> findFirstByPaperSessionIdAndSymbolAndStatusOrderByOpenedAtAsc(
            Long paperSessionId,
            String symbol,
            PaperPositionStatus status
    );
}
