package com.signalattention.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.audit.AuditService;
import com.signalattention.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTests {

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AuditService auditService;

    private StrategyService strategyService;

    @BeforeEach
    void setUp() {
        strategyService = new StrategyService(strategyRepository, auditService, new ObjectMapper());
    }

    @Test
    void createPersistsActiveStrategyAndRecordsAuditEvent() {
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyResponse response = strategyService.create(createRequest());

        assertThat(response.status()).isEqualTo(StrategyStatus.ACTIVE);
        assertThat(response.rules().shortWindow()).isEqualTo(20);
        verify(strategyRepository).save(any(Strategy.class));
        verify(auditService).record(eq("STRATEGY"), any(), eq("STRATEGY_CREATED"), eq("Strategy created"), any());
    }

    @Test
    void getMissingStrategyThrowsNotFound() {
        when(strategyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> strategyService.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Strategy not found: 99");
    }

    private StrategyCreateRequest createRequest() {
        return new StrategyCreateRequest(
                "BTC SMA",
                "BTC-USD",
                "1h",
                StrategyType.SMA_CROSSOVER,
                new SmaCrossoverRulesRequest(
                        20,
                        50,
                        new BigDecimal("10000"),
                        new BigDecimal("0.1"),
                        new BigDecimal("25")
                )
        );
    }
}
