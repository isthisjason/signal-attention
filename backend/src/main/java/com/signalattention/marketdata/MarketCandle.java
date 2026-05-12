package com.signalattention.marketdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "market_candles",
        uniqueConstraints = @UniqueConstraint(
                name = "market_candles_symbol_timeframe_open_time_key",
                columnNames = {"symbol", "timeframe", "open_time"}
        )
)
public class MarketCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String timeframe;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "open", nullable = false, precision = 20, scale = 8)
    private BigDecimal openPrice;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal high;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal low;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal close;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal volume;

    protected MarketCandle() {
    }

    public MarketCandle(
            String symbol,
            String timeframe,
            Instant openTime,
            BigDecimal openPrice,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume
    ) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.openTime = openTime;
        this.openPrice = openPrice;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public Instant getOpenTime() {
        return openTime;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public BigDecimal getVolume() {
        return volume;
    }
}
