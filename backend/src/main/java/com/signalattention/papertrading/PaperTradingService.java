package com.signalattention.papertrading;

import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaperTradingService {

    private static final String ENTITY_TYPE = "PAPER_TRADING";
    private static final int MONEY_SCALE = 8;

    private final StrategyRepository strategyRepository;
    private final PaperSessionRepository sessionRepository;
    private final PaperOrderRepository orderRepository;
    private final PaperPositionRepository positionRepository;
    private final PaperOrderExecutor orderExecutor;
    private final AuditService auditService;

    public PaperTradingService(
            StrategyRepository strategyRepository,
            PaperSessionRepository sessionRepository,
            PaperOrderRepository orderRepository,
            PaperPositionRepository positionRepository,
            PaperOrderExecutor orderExecutor,
            AuditService auditService
    ) {
        this.strategyRepository = strategyRepository;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.orderExecutor = orderExecutor;
        this.auditService = auditService;
    }

    @Transactional
    public PaperSessionResponse createSession(Long strategyId, PaperSessionCreateRequest request) {
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + strategyId));
        PaperSession saved = sessionRepository.save(new PaperSession(strategy, scaleMoney(request.initialBalance())));
        auditService.record(ENTITY_TYPE, saved.getId().toString(), "PAPER_SESSION_CREATED", "Paper session created", metadata(saved));
        return PaperSessionResponse.from(saved);
    }

    @Transactional
    public PaperSessionResponse startSession(Long id) {
        PaperSession session = findSession(id);
        if (session.getStatus() != PaperSessionStatus.CREATED) {
            throw new BadRequestException("Only created paper sessions can be started");
        }
        session.setStatus(PaperSessionStatus.RUNNING);
        session.setStartedAt(Instant.now());
        PaperSession saved = sessionRepository.save(session);
        auditService.record(ENTITY_TYPE, id.toString(), "PAPER_SESSION_STARTED", "Paper session started", metadata(saved));
        return PaperSessionResponse.from(saved);
    }

    @Transactional
    public PaperSessionResponse stopSession(Long id) {
        PaperSession session = findSession(id);
        if (session.getStatus() != PaperSessionStatus.RUNNING) {
            throw new BadRequestException("Only running paper sessions can be stopped");
        }
        session.setStatus(PaperSessionStatus.STOPPED);
        session.setStoppedAt(Instant.now());
        PaperSession saved = sessionRepository.save(session);
        auditService.record(ENTITY_TYPE, id.toString(), "PAPER_SESSION_STOPPED", "Paper session stopped", metadata(saved));
        return PaperSessionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PaperOrderResponse> getOrders(Long id) {
        findSession(id);
        return orderRepository.findByPaperSessionIdOrderByCreatedAtAsc(id).stream()
                .map(PaperOrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaperPositionResponse> getPositions(Long id) {
        findSession(id);
        return positionRepository.findByPaperSessionIdOrderByOpenedAtAsc(id).stream()
                .map(PaperPositionResponse::from)
                .toList();
    }

    @Transactional
    public PaperOrderResponse submitOrder(Long id, PaperOrderRequest request) {
        PaperSession session = findSession(id);
        if (session.getStatus() != PaperSessionStatus.RUNNING) {
            throw new BadRequestException("Paper session must be running to submit orders");
        }

        PaperOrder order = orderExecutor.execute(session, request);
        auditService.record(ENTITY_TYPE, id.toString(), "PAPER_ORDER_" + order.getStatus().name(), "Paper order " + order.getStatus().name().toLowerCase(), orderMetadata(order));
        return PaperOrderResponse.from(order);
    }

    private PaperSession findSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paper session not found: " + id));
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String metadata(PaperSession session) {
        return "{\"strategyId\":" + session.getStrategy().getId() + ",\"status\":\"" + session.getStatus() + "\"}";
    }

    private String orderMetadata(PaperOrder order) {
        return "{\"orderId\":" + order.getId() + ",\"status\":\"" + order.getStatus() + "\"}";
    }
}
