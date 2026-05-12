package com.signalattention.risk;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskPolicyResponse(
        Long id,
        Long strategyId,
        BigDecimal maxPositionSizePercent,
        BigDecimal stopLossPercent,
        BigDecimal takeProfitPercent,
        BigDecimal maxDailyLossPercent,
        Integer cooldownMinutes,
        Instant createdAt,
        Instant updatedAt
) {

    public static RiskPolicyResponse from(RiskPolicy policy) {
        return new RiskPolicyResponse(
                policy.getId(),
                policy.getStrategy().getId(),
                policy.getMaxPositionSizePercent(),
                policy.getStopLossPercent(),
                policy.getTakeProfitPercent(),
                policy.getMaxDailyLossPercent(),
                policy.getCooldownMinutes(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
