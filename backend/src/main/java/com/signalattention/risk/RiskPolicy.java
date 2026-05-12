package com.signalattention.risk;

import com.signalattention.strategies.Strategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "risk_policies")
public class RiskPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    @Column(name = "max_position_size_percent", nullable = false, precision = 12, scale = 6)
    private BigDecimal maxPositionSizePercent;

    @Column(name = "stop_loss_percent", nullable = false, precision = 12, scale = 6)
    private BigDecimal stopLossPercent;

    @Column(name = "take_profit_percent", nullable = false, precision = 12, scale = 6)
    private BigDecimal takeProfitPercent;

    @Column(name = "max_daily_loss_percent", nullable = false, precision = 12, scale = 6)
    private BigDecimal maxDailyLossPercent;

    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RiskPolicy() {
    }

    public RiskPolicy(
            Strategy strategy,
            BigDecimal maxPositionSizePercent,
            BigDecimal stopLossPercent,
            BigDecimal takeProfitPercent,
            BigDecimal maxDailyLossPercent,
            Integer cooldownMinutes
    ) {
        this.strategy = strategy;
        this.maxPositionSizePercent = maxPositionSizePercent;
        this.stopLossPercent = stopLossPercent;
        this.takeProfitPercent = takeProfitPercent;
        this.maxDailyLossPercent = maxDailyLossPercent;
        this.cooldownMinutes = cooldownMinutes;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public BigDecimal getMaxPositionSizePercent() {
        return maxPositionSizePercent;
    }

    public void setMaxPositionSizePercent(BigDecimal maxPositionSizePercent) {
        this.maxPositionSizePercent = maxPositionSizePercent;
    }

    public BigDecimal getStopLossPercent() {
        return stopLossPercent;
    }

    public void setStopLossPercent(BigDecimal stopLossPercent) {
        this.stopLossPercent = stopLossPercent;
    }

    public BigDecimal getTakeProfitPercent() {
        return takeProfitPercent;
    }

    public void setTakeProfitPercent(BigDecimal takeProfitPercent) {
        this.takeProfitPercent = takeProfitPercent;
    }

    public BigDecimal getMaxDailyLossPercent() {
        return maxDailyLossPercent;
    }

    public void setMaxDailyLossPercent(BigDecimal maxDailyLossPercent) {
        this.maxDailyLossPercent = maxDailyLossPercent;
    }

    public Integer getCooldownMinutes() {
        return cooldownMinutes;
    }

    public void setCooldownMinutes(Integer cooldownMinutes) {
        this.cooldownMinutes = cooldownMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
