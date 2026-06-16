package com.signalattention.marketregime;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimeEvidenceSnapshotRepository extends JpaRepository<RegimeEvidenceSnapshot, Long> {
    List<RegimeEvidenceSnapshot> findBySymbolAndTimeframeOrderByCreatedAtDesc(String symbol, String timeframe, Pageable pageable);
}
