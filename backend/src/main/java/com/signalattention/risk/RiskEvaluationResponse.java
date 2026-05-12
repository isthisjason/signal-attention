package com.signalattention.risk;

import java.math.BigDecimal;
import java.util.List;

public record RiskEvaluationResponse(
        RiskDecision decision,
        Long strategyId,
        BigDecimal orderNotional,
        BigDecimal positionSizePercent,
        List<RiskReasonCode> reasonCodes,
        List<String> reasons
) {
}
