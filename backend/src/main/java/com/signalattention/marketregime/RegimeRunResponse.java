package com.signalattention.marketregime;

import com.signalattention.backtesting.TradeSide;
import com.signalattention.marketdata.CandleResponse;
import com.signalattention.ml.MlMarketRegimeFeatures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RegimeRunResponse(
        String symbol,
        String timeframe,
        Integer windowSize,
        Integer stride,
        Boolean includeAnomalies,
        Integer pointCount,
        List<CandleResponse> candles,
        List<RegimeRunPoint> points,
        List<RegimeTradeMarker> tradeMarkers
) {
    public record RegimeRunPoint(
            Instant windowStart,
            Instant windowEnd,
            String regimeLabel,
            BigDecimal confidence,
            List<String> reasons,
            MlMarketRegimeFeatures features,
            BigDecimal anomalyScore,
            String anomalyLabel,
            List<String> anomalyReasons
    ) {
    }

    public record RegimeTradeMarker(
            Long tradeId,
            TradeSide side,
            Instant entryTime,
            BigDecimal entryPrice,
            Instant exitTime,
            BigDecimal exitPrice,
            BigDecimal netPnl
    ) {
    }
}
