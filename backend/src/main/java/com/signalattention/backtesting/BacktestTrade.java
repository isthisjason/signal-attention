package com.signalattention.backtesting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "backtest_trades")
public class BacktestTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "backtest_run_id", nullable = false)
    private BacktestRun backtestRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeSide side;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "entry_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(name = "exit_price", precision = 20, scale = 8)
    private BigDecimal exitPrice;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "gross_pnl", precision = 20, scale = 8)
    private BigDecimal grossPnl;

    @Column(precision = 20, scale = 8)
    private BigDecimal fees;

    @Column(name = "net_pnl", precision = 20, scale = 8)
    private BigDecimal netPnl;

    @Column(name = "return_percent", precision = 12, scale = 6)
    private BigDecimal returnPercent;

    protected BacktestTrade() {
    }

    public BacktestTrade(
            BacktestRun backtestRun,
            TradeSide side,
            Instant entryTime,
            BigDecimal entryPrice,
            BigDecimal quantity
    ) {
        this.backtestRun = backtestRun;
        this.side = side;
        this.entryTime = entryTime;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public BacktestRun getBacktestRun() {
        return backtestRun;
    }

    public TradeSide getSide() {
        return side;
    }

    public Instant getEntryTime() {
        return entryTime;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public Instant getExitTime() {
        return exitTime;
    }

    public void setExitTime(Instant exitTime) {
        this.exitTime = exitTime;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(BigDecimal exitPrice) {
        this.exitPrice = exitPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getGrossPnl() {
        return grossPnl;
    }

    public void setGrossPnl(BigDecimal grossPnl) {
        this.grossPnl = grossPnl;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public void setFees(BigDecimal fees) {
        this.fees = fees;
    }

    public BigDecimal getNetPnl() {
        return netPnl;
    }

    public void setNetPnl(BigDecimal netPnl) {
        this.netPnl = netPnl;
    }

    public BigDecimal getReturnPercent() {
        return returnPercent;
    }

    public void setReturnPercent(BigDecimal returnPercent) {
        this.returnPercent = returnPercent;
    }
}
