package com.signalattention.marketdata;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCandleRepository extends JpaRepository<MarketCandle, Long> {

    boolean existsBySymbolAndTimeframeAndOpenTime(String symbol, String timeframe, Instant openTime);

    List<MarketCandle> findBySymbolAndTimeframeOrderByOpenTimeAsc(String symbol, String timeframe);

    List<MarketCandle> findBySymbolAndTimeframeAndOpenTimeGreaterThanEqualOrderByOpenTimeAsc(
            String symbol,
            String timeframe,
            Instant start
    );

    List<MarketCandle> findBySymbolAndTimeframeAndOpenTimeLessThanEqualOrderByOpenTimeAsc(
            String symbol,
            String timeframe,
            Instant end
    );

    List<MarketCandle> findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
            String symbol,
            String timeframe,
            Instant start,
            Instant end
    );

    MarketCandle findFirstBySymbolAndTimeframeOrderByOpenTimeDesc(String symbol, String timeframe);
}
