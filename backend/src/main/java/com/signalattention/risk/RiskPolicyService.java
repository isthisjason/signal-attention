package com.signalattention.risk;

import com.signalattention.audit.AuditService;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskPolicyService {

    private static final String ENTITY_TYPE = "RISK_POLICY";

    private final StrategyRepository strategyRepository;
    private final RiskPolicyRepository riskPolicyRepository;
    private final AuditService auditService;

    public RiskPolicyService(
            StrategyRepository strategyRepository,
            RiskPolicyRepository riskPolicyRepository,
            AuditService auditService
    ) {
        this.strategyRepository = strategyRepository;
        this.riskPolicyRepository = riskPolicyRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RiskPolicyResponse upsert(Long strategyId, RiskPolicyRequest request) {
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + strategyId));
        // One policy belongs to one strategy; repeated saves update the existing guardrails in place.
        RiskPolicy policy = riskPolicyRepository.findByStrategyId(strategyId)
                .orElseGet(() -> new RiskPolicy(
                        strategy,
                        request.maxPositionSizePercent(),
                        request.stopLossPercent(),
                        request.takeProfitPercent(),
                        request.maxDailyLossPercent(),
                        request.cooldownMinutes()
                ));

        policy.setMaxPositionSizePercent(request.maxPositionSizePercent());
        policy.setStopLossPercent(request.stopLossPercent());
        policy.setTakeProfitPercent(request.takeProfitPercent());
        policy.setMaxDailyLossPercent(request.maxDailyLossPercent());
        policy.setCooldownMinutes(request.cooldownMinutes());

        RiskPolicy saved = riskPolicyRepository.save(policy);
        auditService.record(
                ENTITY_TYPE,
                saved.getId() == null ? null : saved.getId().toString(),
                "RISK_POLICY_UPSERTED",
                "Risk policy upserted",
                "{\"strategyId\":" + strategyId + "}"
        );
        return RiskPolicyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RiskPolicyResponse get(Long strategyId) {
        return riskPolicyRepository.findByStrategyId(strategyId)
                .map(RiskPolicyResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Risk policy not found for strategy: " + strategyId));
    }
}
