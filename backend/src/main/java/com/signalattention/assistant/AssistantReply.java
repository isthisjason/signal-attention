package com.signalattention.assistant;

import java.util.List;

public record AssistantReply(String content, List<AssistantProposedAction> proposedActions) {
}
