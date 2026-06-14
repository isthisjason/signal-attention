package com.signalattention.assistant;

public interface AssistantProvider {

    AssistantReply reply(String prompt, AssistantContext context);
}
