package com.signalattention.marketdata;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCandleRepository extends JpaRepository<MarketCandle, Long> {

    List<MarketCandle> findBySymbolAndTimeframeOrderByOpenTimeAsc(String symbol, String timeframe);

    List<MarketCandle> findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
            String symbol,
            String timeframe,
            Instant start,
            Instant end
    );
}
