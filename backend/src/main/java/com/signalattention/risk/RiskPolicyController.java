package com.signalattention.risk;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RiskPolicyController {

    private final RiskPolicyService riskPolicyService;

    public RiskPolicyController(RiskPolicyService riskPolicyService) {
        this.riskPolicyService = riskPolicyService;
    }

    @PostMapping("/api/strategies/{strategyId}/risk-policy")
    public RiskPolicyResponse upsert(@PathVariable Long strategyId, @Valid @RequestBody RiskPolicyRequest request) {
        return riskPolicyService.upsert(strategyId, request);
    }

    @GetMapping("/api/strategies/{strategyId}/risk-policy")
    public RiskPolicyResponse get(@PathVariable Long strategyId) {
        return riskPolicyService.get(strategyId);
    }
}
