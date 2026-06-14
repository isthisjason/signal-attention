package com.signalattention.assistant;

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
import java.time.Instant;

@Entity
@Table(name = "assistant_messages")
public class AssistantMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AssistantSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssistantMessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssistantMessage() {
    }

    public AssistantMessage(AssistantSession session, AssistantMessageRole role, String content) {
        this.session = session;
        this.role = role;
        this.content = content;
    }

    @PrePersist
    void prePersist() {
        // Message order is append-only; edits are represented by later messages.
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AssistantSession getSession() {
        return session;
    }

    public AssistantMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
