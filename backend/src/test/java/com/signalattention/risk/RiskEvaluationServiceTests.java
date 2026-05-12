package com.signalattention.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyStatus;
import com.signalattention.strategies.StrategyType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RiskEvaluationServiceTests {

    @Mock
    private RiskPolicyRepository riskPolicyRepository;

    private RiskEvaluationService riskEvaluationService;

    @BeforeEach
    void setUp() {
        riskEvaluationService = new RiskEvaluationService(riskPolicyRepository);
    }

    @Test
    void evaluateApprovesOrderWithinMaxPositionSize() {
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.of(policy()));

        RiskEvaluationResponse response = riskEvaluationService.evaluate(request("2", "1000", "10000"));

        assertThat(response.decision()).isEqualTo(RiskDecision.APPROVED);
        assertThat(response.orderNotional()).isEqualByComparingTo("2000.00000000");
        assertThat(response.positionSizePercent()).isEqualByComparingTo("20.000000");
        assertThat(response.reasonCodes()).contains(RiskReasonCode.POSITION_SIZE_WITHIN_LIMIT);
    }

    @Test
    void evaluateRejectsOrderAboveMaxPositionSize() {
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.of(policy()));

        RiskEvaluationResponse response = riskEvaluationService.evaluate(request("4", "1000", "10000"));

        assertThat(response.decision()).isEqualTo(RiskDecision.REJECTED);
        assertThat(response.positionSizePercent()).isEqualByComparingTo("40.000000");
        assertThat(response.reasonCodes()).contains(RiskReasonCode.POSITION_SIZE_EXCEEDS_LIMIT);
    }

    @Test
    void evaluateMissingPolicyThrowsNotFound() {
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> riskEvaluationService.evaluate(request("2", "1000", "10000")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Risk policy not found for strategy: 1");
    }

    private RiskEvaluationRequest request(String quantity, String price, String equity) {
        return new RiskEvaluationRequest(
                1L,
                "BTC-USD",
                RiskOrderSide.BUY,
                new BigDecimal(quantity),
                new BigDecimal(price),
                new BigDecimal(equity),
                BigDecimal.ZERO,
                null,
                null,
                null
        );
    }

    private RiskPolicy policy() {
        Strategy strategy = new Strategy(
                "BTC SMA",
                "BTC-USD",
                "1h",
                StrategyType.SMA_CROSSOVER,
                "{}",
                StrategyStatus.ACTIVE
        );
        ReflectionTestUtils.setField(strategy, "id", 1L);
        return new RiskPolicy(
                strategy,
                new BigDecimal("25"),
                new BigDecimal("5"),
                new BigDecimal("12"),
                new BigDecimal("8"),
                30
        );
    }
}
