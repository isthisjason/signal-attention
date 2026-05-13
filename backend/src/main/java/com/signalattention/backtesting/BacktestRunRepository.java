package com.signalattention.backtesting;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {

    Optional<BacktestRun> findFirstByOrderByCreatedAtDesc();
}
