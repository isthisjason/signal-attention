package com.signalattention.risk;

import com.signalattention.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskEvaluationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final RiskPolicyRepository riskPolicyRepository;

    public RiskEvaluationService(RiskPolicyRepository riskPolicyRepository) {
        this.riskPolicyRepository = riskPolicyRepository;
    }

    @Transactional(readOnly = true)
    public RiskEvaluationResponse evaluate(RiskEvaluationRequest request) {
        RiskPolicy policy = riskPolicyRepository.findByStrategyId(request.strategyId())
                .orElseThrow(() -> new ResourceNotFoundException("Risk policy not found for strategy: " + request.strategyId()));

        BigDecimal orderNotional = request.quantity().multiply(request.price()).setScale(8, RoundingMode.HALF_UP);
        BigDecimal positionSizePercent = orderNotional
                .divide(request.accountEquity(), 12, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(6, RoundingMode.HALF_UP);

        List<RiskReasonCode> reasonCodes = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        reasonCodes.add(RiskReasonCode.POLICY_FOUND);
        reasons.add("Risk policy found.");

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
            Instant cooldownEndsAt = request.lastRiskRejectionAt().plus(policy.getCooldownMinutes(), ChronoUnit.MINUTES);
            if (cooldownEndsAt.isAfter(evaluatedAt)) {
                decision = RiskDecision.REJECTED;
                reasonCodes.add(RiskReasonCode.COOLDOWN_ACTIVE);
                reasons.add("Risk cooldown is active after a recent rejection.");
            }
        }

        if (request.side() == RiskOrderSide.SELL && request.openPositionEntryPrice() != null) {
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

        return new RiskEvaluationResponse(
                decision,
                request.strategyId(),
                orderNotional,
                positionSizePercent,
                List.copyOf(reasonCodes),
                List.copyOf(reasons)
        );
    }
}
