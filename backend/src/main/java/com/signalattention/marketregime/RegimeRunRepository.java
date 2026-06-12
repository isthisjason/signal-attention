package com.signalattention.marketregime;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimeRunRepository extends JpaRepository<RegimeRun, Long> {

    List<RegimeRun> findBySymbolAndTimeframeOrderByCreatedAtDesc(String symbol, String timeframe, Pageable pageable);
}
