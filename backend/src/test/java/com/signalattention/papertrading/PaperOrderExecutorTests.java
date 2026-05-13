package com.signalattention.papertrading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalattention.risk.RiskDecision;
import com.signalattention.risk.RiskEvaluationResponse;
import com.signalattention.risk.RiskEvaluationService;
import com.signalattention.risk.RiskPolicy;
import com.signalattention.risk.RiskPolicyRepository;
import com.signalattention.risk.RiskReasonCode;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyStatus;
import com.signalattention.strategies.StrategyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaperOrderExecutorTests {

    @Mock
    private PaperSessionRepository sessionRepository;
    @Mock
    private PaperOrderRepository orderRepository;
    @Mock
    private PaperPositionRepository positionRepository;
    @Mock
    private RiskPolicyRepository riskPolicyRepository;
    @Mock
    private RiskEvaluationService riskEvaluationService;

    private PaperOrderExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PaperOrderExecutor(sessionRepository, orderRepository, positionRepository, riskPolicyRepository, riskEvaluationService);
    }

    @Test
    void buyOrderFillsAndOpensPosition() {
        PaperSession session = session();
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.empty());
        when(positionRepository.findFirstByPaperSessionIdAndSymbolAndStatusOrderByOpenedAtAsc(10L, "BTC-USD", PaperPositionStatus.OPEN))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any(PaperOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperOrder order = executor.execute(session, request(PaperOrderSide.BUY, "0.1", "40000"));

        assertThat(order.getStatus()).isEqualTo(PaperOrderStatus.FILLED);
        assertThat(order.getNotional()).isEqualByComparingTo("4000.00000000");
        assertThat(session.getCashBalance()).isEqualByComparingTo("6000.00000000");
        verify(positionRepository).save(any(PaperPosition.class));
    }

    @Test
    void riskRejectionRecordsRejectedOrder() {
        PaperSession session = session();
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.of(policy()));
        when(positionRepository.findFirstByPaperSessionIdAndSymbolAndStatusOrderByOpenedAtAsc(10L, "BTC-USD", PaperPositionStatus.OPEN))
                .thenReturn(Optional.empty());
        when(riskEvaluationService.evaluate(any())).thenReturn(new RiskEvaluationResponse(
                RiskDecision.REJECTED,
                1L,
                new BigDecimal("4000"),
                new BigDecimal("40"),
                List.of(RiskReasonCode.POSITION_SIZE_EXCEEDS_LIMIT),
                List.of("Too large")
        ));
        when(orderRepository.save(any(PaperOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperOrder order = executor.execute(session, request(PaperOrderSide.BUY, "0.1", "40000"));

        assertThat(order.getStatus()).isEqualTo(PaperOrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Risk policy rejected the paper order.");
        assertThat(session.getCashBalance()).isEqualByComparingTo("10000");
    }

    @Test
    void sellOrderClosesOpenPosition() {
        PaperSession session = session();
        PaperPosition position = new PaperPosition(session, "BTC-USD", new BigDecimal("0.1"), new BigDecimal("40000"));
        when(riskPolicyRepository.findByStrategyId(1L)).thenReturn(Optional.empty());
        when(positionRepository.findFirstByPaperSessionIdAndSymbolAndStatusOrderByOpenedAtAsc(10L, "BTC-USD", PaperPositionStatus.OPEN))
                .thenReturn(Optional.of(position), Optional.of(position));
        when(orderRepository.save(any(PaperOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaperOrder order = executor.execute(session, request(PaperOrderSide.SELL, "0.1", "41000"));

        assertThat(order.getStatus()).isEqualTo(PaperOrderStatus.FILLED);
        assertThat(position.getStatus()).isEqualTo(PaperPositionStatus.CLOSED);
        assertThat(position.getExitPrice()).isEqualByComparingTo("41000.00000000");
        assertThat(session.getCashBalance()).isEqualByComparingTo("14100.00000000");
    }

    private PaperOrderRequest request(PaperOrderSide side, String quantity, String price) {
        return new PaperOrderRequest(side, "BTC-USD", new BigDecimal(quantity), new BigDecimal(price));
    }

    private PaperSession session() {
        PaperSession session = new PaperSession(strategy(), new BigDecimal("10000"));
        ReflectionTestUtils.setField(session, "id", 10L);
        session.setStatus(PaperSessionStatus.RUNNING);
        return session;
    }

    private RiskPolicy policy() {
        return new RiskPolicy(strategy(), new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("12"), new BigDecimal("8"), 0);
    }

    private Strategy strategy() {
        Strategy strategy = new Strategy("BTC SMA", "BTC-USD", "1h", StrategyType.SMA_CROSSOVER, "{}", StrategyStatus.ACTIVE);
        ReflectionTestUtils.setField(strategy, "id", 1L);
        return strategy;
    }
}
