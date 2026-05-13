package com.signalattention.papertrading;

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
@Table(name = "paper_orders")
public class PaperOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paper_session_id", nullable = false)
    private PaperSession paperSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaperOrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaperOrderStatus status;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal notional;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaperOrder() {
    }

    public PaperOrder(PaperSession paperSession, PaperOrderSide side, PaperOrderStatus status, String symbol,
                      BigDecimal quantity, BigDecimal price, BigDecimal notional, String rejectionReason) {
        this.paperSession = paperSession;
        this.side = side;
        this.status = status;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.notional = notional;
        this.rejectionReason = rejectionReason;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public PaperSession getPaperSession() {
        return paperSession;
    }

    public PaperOrderSide getSide() {
        return side;
    }

    public PaperOrderStatus getStatus() {
        return status;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getNotional() {
        return notional;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
