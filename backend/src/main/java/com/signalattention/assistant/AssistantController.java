package com.signalattention.assistant;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/api/assistant/sessions")
    public AssistantSessionResponse createSession(@RequestBody(required = false) AssistantCreateSessionRequest request) {
        return assistantService.createSession(request);
    }

    @GetMapping("/api/assistant/sessions/{id}")
    public AssistantSessionResponse getSession(@PathVariable Long id) {
        return assistantService.getSession(id);
    }

    @PostMapping("/api/assistant/sessions/{id}/messages")
    public AssistantSessionResponse sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody AssistantMessageRequest request
    ) {
        return assistantService.sendMessage(id, request);
    }
}
