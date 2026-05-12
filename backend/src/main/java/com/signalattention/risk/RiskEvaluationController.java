package com.signalattention.risk;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RiskEvaluationController {

    private final RiskEvaluationService riskEvaluationService;

    public RiskEvaluationController(RiskEvaluationService riskEvaluationService) {
        this.riskEvaluationService = riskEvaluationService;
    }

    @PostMapping("/api/risk/evaluate-order")
    public RiskEvaluationResponse evaluate(@Valid @RequestBody RiskEvaluationRequest request) {
        return riskEvaluationService.evaluate(request);
    }
}
