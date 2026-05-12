package com.signalattention.backtesting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestTradeRepository extends JpaRepository<BacktestTrade, Long> {

    List<BacktestTrade> findByBacktestRunIdOrderByEntryTimeAsc(Long backtestRunId);
}
