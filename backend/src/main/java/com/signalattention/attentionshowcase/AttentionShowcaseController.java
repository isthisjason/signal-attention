package com.signalattention.attentionshowcase;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttentionShowcaseController {

    private final AttentionShowcaseService service;

    public AttentionShowcaseController(AttentionShowcaseService service) {
        this.service = service;
    }

    @GetMapping("/api/attention-showcase/summary")
    public AttentionShowcaseSummaryResponse getSummary() {
        return service.getSummary();
    }
}
