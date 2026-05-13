package com.signalattention.papertrading;

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
@Table(name = "paper_sessions")
public class PaperSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaperSessionStatus status;

    @Column(name = "initial_balance", nullable = false, precision = 20, scale = 8)
    private BigDecimal initialBalance;

    @Column(name = "cash_balance", nullable = false, precision = 20, scale = 8)
    private BigDecimal cashBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    protected PaperSession() {
    }

    public PaperSession(Strategy strategy, BigDecimal initialBalance) {
        this.strategy = strategy;
        this.initialBalance = initialBalance;
        this.cashBalance = initialBalance;
        this.status = PaperSessionStatus.CREATED;
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

    public PaperSessionStatus getStatus() {
        return status;
    }

    public void setStatus(PaperSessionStatus status) {
        this.status = status;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
