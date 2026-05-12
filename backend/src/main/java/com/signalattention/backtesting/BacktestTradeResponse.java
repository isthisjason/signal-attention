package com.signalattention.backtesting;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestTradeResponse(
        Long id,
        Long backtestRunId,
        TradeSide side,
        Instant entryTime,
        BigDecimal entryPrice,
        Instant exitTime,
        BigDecimal exitPrice,
        BigDecimal quantity,
        BigDecimal grossPnl,
        BigDecimal fees,
        BigDecimal netPnl,
        BigDecimal returnPercent
) {

    public static BacktestTradeResponse from(BacktestTrade trade) {
        return new BacktestTradeResponse(
                trade.getId(),
                trade.getBacktestRun().getId(),
                trade.getSide(),
                trade.getEntryTime(),
                trade.getEntryPrice(),
                trade.getExitTime(),
                trade.getExitPrice(),
                trade.getQuantity(),
                trade.getGrossPnl(),
                trade.getFees(),
                trade.getNetPnl(),
                trade.getReturnPercent()
        );
    }
}
