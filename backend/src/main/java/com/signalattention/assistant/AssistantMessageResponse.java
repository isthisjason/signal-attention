package com.signalattention.assistant;

import java.time.Instant;

public record AssistantMessageResponse(
        Long id,
        AssistantMessageRole role,
        String content,
        Instant createdAt
) {

    public static AssistantMessageResponse from(AssistantMessage message) {
        return new AssistantMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
