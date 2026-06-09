package com.signalattention.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.audit.AuditService;
import com.signalattention.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskEvaluationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String ENTITY_TYPE = "RISK_EVALUATION";

    private final RiskPolicyRepository riskPolicyRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RiskEvaluationService(
            RiskPolicyRepository riskPolicyRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.riskPolicyRepository = riskPolicyRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RiskEvaluationResponse evaluate(RiskEvaluationRequest request) {
        RiskPolicy policy = riskPolicyRepository.findByStrategyId(request.strategyId())
                .orElseThrow(() -> new ResourceNotFoundException("Risk policy not found for strategy: " + request.strategyId()));

        // Position size is measured against account equity so the same order scales across balances.
        BigDecimal orderNotional = request.quantity().multiply(request.price()).setScale(8, RoundingMode.HALF_UP);
        BigDecimal positionSizePercent = orderNotional
                .divide(request.accountEquity(), 12, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(6, RoundingMode.HALF_UP);

        List<RiskReasonCode> reasonCodes = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        reasonCodes.add(RiskReasonCode.POLICY_FOUND);
        reasons.add("Risk policy found.");

        // The response accumulates all reasons so users can see every policy signal, not just the first failure.
        RiskDecision decision = RiskDecision.APPROVED;
        if (positionSizePercent.compareTo(policy.getMaxPositionSizePercent()) > 0) {
            decision = RiskDecision.REJECTED;
            reasonCodes.add(RiskReasonCode.POSITION_SIZE_EXCEEDS_LIMIT);
            reasons.add("Order position size exceeds the policy limit.");
        } else {
            reasonCodes.add(RiskReasonCode.POSITION_SIZE_WITHIN_LIMIT);
            reasons.add("Order position size is within the policy limit.");
        }

        if (request.currentDailyLoss() != null
                && request.currentDailyLoss().compareTo(policy.getMaxDailyLossPercent()) >= 0) {
            decision = RiskDecision.REJECTED;
            reasonCodes.add(RiskReasonCode.MAX_DAILY_LOSS_EXCEEDED);
            reasons.add("Current daily loss has reached or exceeded the policy limit.");
        } else {
            reasonCodes.add(RiskReasonCode.DAILY_LOSS_WITHIN_LIMIT);
            reasons.add("Current daily loss is within the policy limit.");
        }

        Instant evaluatedAt = request.evaluatedAt() == null ? Instant.now() : request.evaluatedAt();
        if (request.lastRiskRejectionAt() != null && policy.getCooldownMinutes() > 0) {
            // Caller-provided timestamps make the rule deterministic in tests and replay scenarios.
            Instant cooldownEndsAt = request.lastRiskRejectionAt().plus(policy.getCooldownMinutes(), ChronoUnit.MINUTES);
            if (cooldownEndsAt.isAfter(evaluatedAt)) {
                // Cooldown blocks another order after a recent rejection even if size checks pass.
                decision = RiskDecision.REJECTED;
                reasonCodes.add(RiskReasonCode.COOLDOWN_ACTIVE);
                reasons.add("Risk cooldown is active after a recent rejection.");
            }
        }

        if (request.side() == RiskOrderSide.SELL && request.openPositionEntryPrice() != null) {
            // Stop-loss and take-profit are annotations for closing orders, not automatic executions.
            BigDecimal openPositionReturn = request.price()
                    .subtract(request.openPositionEntryPrice())
                    .divide(request.openPositionEntryPrice(), 12, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED);
            if (openPositionReturn.compareTo(policy.getStopLossPercent().negate()) <= 0) {
                reasonCodes.add(RiskReasonCode.STOP_LOSS_TRIGGERED);
                reasons.add("Sell order closes a position at or beyond the stop-loss threshold.");
            }
            if (openPositionReturn.compareTo(policy.getTakeProfitPercent()) >= 0) {
                reasonCodes.add(RiskReasonCode.TAKE_PROFIT_TRIGGERED);
                reasons.add("Sell order closes a position at or beyond the take-profit threshold.");
            }
        }

        RiskEvaluationResponse response = new RiskEvaluationResponse(
                decision,
                request.strategyId(),
                orderNotional,
                positionSizePercent,
                List.copyOf(reasonCodes),
                List.copyOf(reasons)
        );
        // Every evaluation is audited, including approvals, because policy decisions explain paper-order history.
        auditService.record(
                ENTITY_TYPE,
                request.strategyId().toString(),
                "RISK_" + decision.name(),
                "Risk evaluation " + decision.name().toLowerCase(),
                metadataJson(response)
        );
        return response;
    }

    private String metadataJson(RiskEvaluationResponse response) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "strategyId", response.strategyId(),
                    "decision", response.decision(),
                    "orderNotional", response.orderNotional(),
                    "positionSizePercent", response.positionSizePercent(),
                    "reasonCodes", response.reasonCodes()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize risk evaluation metadata", exception);
        }
    }
}
