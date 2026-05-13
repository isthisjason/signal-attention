package com.signalattention.papertrading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
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
class PaperTradingServiceLifecycleTests {

    @Mock
    private StrategyRepository strategyRepository;
    @Mock
    private PaperSessionRepository sessionRepository;
    @Mock
    private PaperOrderRepository orderRepository;
    @Mock
    private PaperPositionRepository positionRepository;
    @Mock
    private PaperOrderExecutor orderExecutor;
    @Mock
    private AuditService auditService;

    private PaperTradingService service;

    @BeforeEach
    void setUp() {
        service = new PaperTradingService(
                strategyRepository,
                sessionRepository,
                orderRepository,
                positionRepository,
                orderExecutor,
                auditService
        );
    }

    @Test
    void createSessionPersistsCreatedSession() {
        when(strategyRepository.findById(1L)).thenReturn(Optional.of(strategy()));
        when(sessionRepository.save(any(PaperSession.class))).thenAnswer(invocation -> {
            PaperSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 10L);
            return session;
        });

        PaperSessionResponse response = service.createSession(1L, new PaperSessionCreateRequest(new BigDecimal("10000")));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(PaperSessionStatus.CREATED);
        assertThat(response.cashBalance()).isEqualByComparingTo("10000.00000000");
        verify(auditService).record(eq("PAPER_TRADING"), eq("10"), eq("PAPER_SESSION_CREATED"), eq("Paper session created"), contains("CREATED"));
    }

    @Test
    void createSessionMissingStrategyThrowsNotFound() {
        when(strategyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSession(99L, new PaperSessionCreateRequest(BigDecimal.TEN)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Strategy not found: 99");
    }

    @Test
    void startAndStopSessionMoveThroughValidStates() {
        PaperSession session = session(PaperSessionStatus.CREATED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        PaperSessionResponse started = service.startSession(10L);
        PaperSessionResponse stopped = service.stopSession(10L);

        assertThat(started.status()).isEqualTo(PaperSessionStatus.RUNNING);
        assertThat(stopped.status()).isEqualTo(PaperSessionStatus.STOPPED);
        verify(auditService).record(eq("PAPER_TRADING"), eq("10"), eq("PAPER_SESSION_STARTED"), eq("Paper session started"), contains("RUNNING"));
        verify(auditService).record(eq("PAPER_TRADING"), eq("10"), eq("PAPER_SESSION_STOPPED"), eq("Paper session stopped"), contains("STOPPED"));
    }

    @Test
    void startRejectsStoppedSession() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session(PaperSessionStatus.STOPPED)));

        assertThatThrownBy(() -> service.startSession(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only created paper sessions can be started");
    }

    private PaperSession session(PaperSessionStatus status) {
        PaperSession session = new PaperSession(strategy(), new BigDecimal("10000"));
        ReflectionTestUtils.setField(session, "id", 10L);
        session.setStatus(status);
        return session;
    }

    private Strategy strategy() {
        Strategy strategy = new Strategy("BTC SMA", "BTC-USD", "1h", StrategyType.SMA_CROSSOVER, "{}", StrategyStatus.ACTIVE);
        ReflectionTestUtils.setField(strategy, "id", 1L);
        return strategy;
    }
}
