package com.signalattention.assistant;

import com.signalattention.common.BadRequestException;
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
@Table(name = "assistant_actions")
public class AssistantAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AssistantSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private AssistantMessage message;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private AssistantActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssistantActionStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "execution_result_json", columnDefinition = "text")
    private String executionResultJson;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    protected AssistantAction() {
    }

    public AssistantAction(AssistantSession session, AssistantMessage message, AssistantActionType actionType,
                           String summary, String payloadJson) {
        this.session = session;
        this.message = message;
        this.actionType = actionType;
        this.summary = summary;
        this.payloadJson = payloadJson;
        this.status = AssistantActionStatus.PROPOSED;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void markConfirmed() {
        // Confirmation is the safety boundary between assistant suggestion and state mutation.
        requireStatus(AssistantActionStatus.PROPOSED);
        status = AssistantActionStatus.CONFIRMED;
        confirmedAt = Instant.now();
    }

    public void markRejected() {
        requireStatus(AssistantActionStatus.PROPOSED);
        status = AssistantActionStatus.REJECTED;
        rejectedAt = Instant.now();
    }

    public void markExecuted(String executionResultJson) {
        requireStatus(AssistantActionStatus.CONFIRMED);
        status = AssistantActionStatus.EXECUTED;
        this.executionResultJson = executionResultJson;
        executedAt = Instant.now();
    }

    public void markFailed(String failureMessage) {
        status = AssistantActionStatus.FAILED;
        this.failureMessage = failureMessage;
        executedAt = Instant.now();
    }

    private void requireStatus(AssistantActionStatus expected) {
        if (status != expected) {
            throw new BadRequestException("Assistant action must be " + expected + " but was " + status);
        }
    }

    public Long getId() {
        return id;
    }

    public AssistantSession getSession() {
        return session;
    }

    public AssistantMessage getMessage() {
        return message;
    }

    public AssistantActionType getActionType() {
        return actionType;
    }

    public AssistantActionStatus getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getExecutionResultJson() {
        return executionResultJson;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
