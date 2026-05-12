package com.signalattention.backtesting;

import com.signalattention.strategies.Strategy;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "backtest_runs")
public class BacktestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "initial_balance", nullable = false, precision = 20, scale = 8)
    private BigDecimal initialBalance;

    @Column(name = "final_balance", precision = 20, scale = 8)
    private BigDecimal finalBalance;

    @Column(name = "total_return", precision = 12, scale = 6)
    private BigDecimal totalReturn;

    @Column(name = "max_drawdown", precision = 12, scale = 6)
    private BigDecimal maxDrawdown;

    @Column(name = "win_rate", precision = 12, scale = 6)
    private BigDecimal winRate;

    @Column(name = "profit_factor", precision = 12, scale = 6)
    private BigDecimal profitFactor;

    @Column(name = "trade_count")
    private Integer tradeCount;

    @Column(name = "average_trade_return", precision = 12, scale = 6)
    private BigDecimal averageTradeReturn;

    @Column(name = "fee_drag", precision = 20, scale = 8)
    private BigDecimal feeDrag;

    @Column(precision = 12, scale = 6)
    private BigDecimal volatility;

    @Column(name = "ml_risk_score", precision = 10, scale = 4)
    private BigDecimal mlRiskScore;

    @Column(name = "ml_risk_label")
    private String mlRiskLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BacktestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected BacktestRun() {
    }

    public BacktestRun(Strategy strategy, Instant startDate, Instant endDate, BigDecimal initialBalance, BacktestStatus status) {
        this.strategy = strategy;
        this.startDate = startDate;
        this.endDate = endDate;
        this.initialBalance = initialBalance;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public BigDecimal getFinalBalance() {
        return finalBalance;
    }

    public void setFinalBalance(BigDecimal finalBalance) {
        this.finalBalance = finalBalance;
    }

    public BigDecimal getTotalReturn() {
        return totalReturn;
    }

    public void setTotalReturn(BigDecimal totalReturn) {
        this.totalReturn = totalReturn;
    }

    public BigDecimal getMaxDrawdown() {
        return maxDrawdown;
    }

    public void setMaxDrawdown(BigDecimal maxDrawdown) {
        this.maxDrawdown = maxDrawdown;
    }

    public BigDecimal getWinRate() {
        return winRate;
    }

    public void setWinRate(BigDecimal winRate) {
        this.winRate = winRate;
    }

    public BigDecimal getProfitFactor() {
        return profitFactor;
    }

    public void setProfitFactor(BigDecimal profitFactor) {
        this.profitFactor = profitFactor;
    }

    public Integer getTradeCount() {
        return tradeCount;
    }

    public void setTradeCount(Integer tradeCount) {
        this.tradeCount = tradeCount;
    }

    public BigDecimal getAverageTradeReturn() {
        return averageTradeReturn;
    }

    public void setAverageTradeReturn(BigDecimal averageTradeReturn) {
        this.averageTradeReturn = averageTradeReturn;
    }

    public BigDecimal getFeeDrag() {
        return feeDrag;
    }

    public void setFeeDrag(BigDecimal feeDrag) {
        this.feeDrag = feeDrag;
    }

    public BigDecimal getVolatility() {
        return volatility;
    }

    public void setVolatility(BigDecimal volatility) {
        this.volatility = volatility;
    }

    public BigDecimal getMlRiskScore() {
        return mlRiskScore;
    }

    public void setMlRiskScore(BigDecimal mlRiskScore) {
        this.mlRiskScore = mlRiskScore;
    }

    public String getMlRiskLabel() {
        return mlRiskLabel;
    }

    public void setMlRiskLabel(String mlRiskLabel) {
        this.mlRiskLabel = mlRiskLabel;
    }

    public BacktestStatus getStatus() {
        return status;
    }

    public void setStatus(BacktestStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
