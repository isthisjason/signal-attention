package com.signalattention.assistant;

import java.util.Map;

public record AssistantProposedAction(
        AssistantActionType actionType,
        String summary,
        Map<String, Object> payload
) {
}
