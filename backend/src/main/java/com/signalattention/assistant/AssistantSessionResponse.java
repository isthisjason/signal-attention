package com.signalattention.assistant;

import java.time.Instant;
import java.util.List;

public record AssistantSessionResponse(
        Long id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<AssistantMessageResponse> messages,
        List<AssistantActionResponse> actions
) {
}
