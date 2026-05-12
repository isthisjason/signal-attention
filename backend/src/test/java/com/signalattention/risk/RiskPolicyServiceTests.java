package com.signalattention.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalattention.audit.AuditService;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
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
class RiskPolicyServiceTests {

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private RiskPolicyRepository riskPolicyRepository;

    @Mock
    private AuditService auditService;

    private RiskPolicyService riskPolicyService;

    @BeforeEach
    void setUp() {
        riskPolicyService = new RiskPolicyService(strategyRepository, riskPolicyRepository, auditService);
    }

    @Test
    void upsertCreatesPolicyForStrategy() {
        Strategy strategy = strategy();
        when(strategyRepository.findById(1L)).thenReturn(Optional.of(strategy));
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.empty());
        when(riskPolicyRepository.save(any(RiskPolicy.class))).thenAnswer(invocation -> {
            RiskPolicy policy = invocation.getArgument(0);
            ReflectionTestUtils.setField(policy, "id", 99L);
            return policy;
        });

        RiskPolicyResponse response = riskPolicyService.upsert(1L, request());

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.strategyId()).isEqualTo(1L);
        assertThat(response.maxPositionSizePercent()).isEqualByComparingTo("25");
        verify(auditService).record(eq("RISK_POLICY"), eq("99"), eq("RISK_POLICY_UPSERTED"), eq("Risk policy upserted"), any());
    }

    @Test
    void upsertUpdatesExistingPolicy() {
        Strategy strategy = strategy();
        RiskPolicy policy = new RiskPolicy(
                strategy,
                new BigDecimal("10"),
                new BigDecimal("2"),
                new BigDecimal("5"),
                new BigDecimal("4"),
                5
        );
        ReflectionTestUtils.setField(policy, "id", 99L);
        when(strategyRepository.findById(1L)).thenReturn(Optional.of(strategy));
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.of(policy));
        when(riskPolicyRepository.save(policy)).thenReturn(policy);

        RiskPolicyResponse response = riskPolicyService.upsert(1L, request());

        assertThat(response.maxPositionSizePercent()).isEqualByComparingTo("25");
        assertThat(response.cooldownMinutes()).isEqualTo(30);
    }

    @Test
    void upsertMissingStrategyThrowsNotFound() {
        when(strategyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> riskPolicyService.upsert(99L, request()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Strategy not found: 99");
    }

    @Test
    void getMissingPolicyThrowsNotFound() {
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> riskPolicyService.get(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Risk policy not found for strategy: 1");
    }

    private RiskPolicyRequest request() {
        return new RiskPolicyRequest(
                new BigDecimal("25"),
                new BigDecimal("5"),
                new BigDecimal("12"),
                new BigDecimal("8"),
                30
        );
    }

    private Strategy strategy() {
        Strategy strategy = new Strategy(
                "BTC SMA",
                "BTC-USD",
                "1h",
                StrategyType.SMA_CROSSOVER,
                "{\"shortWindow\":20,\"longWindow\":50,\"initialBalance\":10000,\"feePercent\":0.1,\"positionSizePercent\":25}",
                StrategyStatus.ACTIVE
        );
        ReflectionTestUtils.setField(strategy, "id", 1L);
        return strategy;
    }
}
