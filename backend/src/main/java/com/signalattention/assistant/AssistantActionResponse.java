package com.signalattention.assistant;

import java.time.Instant;

public record AssistantActionResponse(
        Long id,
        Long messageId,
        AssistantActionType actionType,
        AssistantActionStatus status,
        String summary,
        String payloadJson,
        String executionResultJson,
        String failureMessage,
        Instant createdAt,
        Instant confirmedAt,
        Instant rejectedAt,
        Instant executedAt
) {

    public static AssistantActionResponse from(AssistantAction action) {
        return new AssistantActionResponse(
                action.getId(),
                action.getMessage() == null ? null : action.getMessage().getId(),
                action.getActionType(),
                action.getStatus(),
                action.getSummary(),
                action.getPayloadJson(),
                action.getExecutionResultJson(),
                action.getFailureMessage(),
                action.getCreatedAt(),
                action.getConfirmedAt(),
                action.getRejectedAt(),
                action.getExecutedAt()
        );
    }
}
