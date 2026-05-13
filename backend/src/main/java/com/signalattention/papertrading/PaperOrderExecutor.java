package com.signalattention.papertrading;

import com.signalattention.common.BadRequestException;
import com.signalattention.risk.RiskDecision;
import com.signalattention.risk.RiskEvaluationRequest;
import com.signalattention.risk.RiskEvaluationResponse;
import com.signalattention.risk.RiskEvaluationService;
import com.signalattention.risk.RiskOrderSide;
import com.signalattention.risk.RiskPolicyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class PaperOrderExecutor {

    private static final int MONEY_SCALE = 8;

    private final PaperSessionRepository sessionRepository;
    private final PaperOrderRepository orderRepository;
    private final PaperPositionRepository positionRepository;
    private final RiskPolicyRepository riskPolicyRepository;
    private final RiskEvaluationService riskEvaluationService;

    public PaperOrderExecutor(
            PaperSessionRepository sessionRepository,
            PaperOrderRepository orderRepository,
            PaperPositionRepository positionRepository,
            RiskPolicyRepository riskPolicyRepository,
            RiskEvaluationService riskEvaluationService
    ) {
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.riskPolicyRepository = riskPolicyRepository;
        this.riskEvaluationService = riskEvaluationService;
    }

    public PaperOrder execute(PaperSession session, PaperOrderRequest request) {
        BigDecimal quantity = scaleMoney(request.quantity());
        BigDecimal price = scaleMoney(request.price());
        BigDecimal notional = scaleMoney(quantity.multiply(price));
        String rejection = rejectionReason(session, request, notional);
        PaperOrderStatus status = rejection == null ? PaperOrderStatus.FILLED : PaperOrderStatus.REJECTED;
        PaperOrder order = orderRepository.save(new PaperOrder(
                session,
                request.side(),
                status,
                request.symbol(),
                quantity,
                price,
                notional,
                rejection
        ));
        if (status == PaperOrderStatus.FILLED) {
            applyFill(session, request.side(), request.symbol(), quantity, price, notional);
        }
        return order;
    }

    private String rejectionReason(PaperSession session, PaperOrderRequest request, BigDecimal notional) {
        if (request.side() == PaperOrderSide.BUY && session.getCashBalance().compareTo(notional) < 0) {
            return "Insufficient paper cash balance.";
        }
        PaperPosition openPosition = openPosition(session.getId(), request.symbol()).orElse(null);
        if (request.side() == PaperOrderSide.SELL && openPosition == null) {
            return "No open paper position exists for the symbol.";
        }
        if (request.side() == PaperOrderSide.SELL && openPosition.getQuantity().compareTo(request.quantity()) < 0) {
            return "Sell quantity exceeds the open paper position.";
        }
        if (riskPolicyRepository.findByStrategyId(session.getStrategy().getId()).isPresent()
                && riskDecision(session, request, notional, openPosition) == RiskDecision.REJECTED) {
            return "Risk policy rejected the paper order.";
        }
        return null;
    }

    private RiskDecision riskDecision(PaperSession session, PaperOrderRequest request, BigDecimal notional, PaperPosition openPosition) {
        RiskEvaluationResponse response = riskEvaluationService.evaluate(new RiskEvaluationRequest(
                session.getStrategy().getId(),
                request.symbol(),
                request.side() == PaperOrderSide.BUY ? RiskOrderSide.BUY : RiskOrderSide.SELL,
                request.quantity(),
                request.price(),
                session.getCashBalance().max(notional),
                BigDecimal.ZERO,
                openPosition == null ? null : openPosition.getEntryPrice(),
                null,
                Instant.now()
        ));
        return response.decision();
    }

    private void applyFill(PaperSession session, PaperOrderSide side, String symbol, BigDecimal quantity, BigDecimal price, BigDecimal notional) {
        if (side == PaperOrderSide.BUY) {
            applyBuy(session, symbol, quantity, price, notional);
        } else {
            applySell(session, symbol, quantity, price, notional);
        }
        sessionRepository.save(session);
    }

    private void applyBuy(PaperSession session, String symbol, BigDecimal quantity, BigDecimal price, BigDecimal notional) {
        session.setCashBalance(scaleMoney(session.getCashBalance().subtract(notional)));
        PaperPosition position = openPosition(session.getId(), symbol)
                .orElseGet(() -> new PaperPosition(session, symbol, BigDecimal.ZERO, price));
        BigDecimal currentValue = position.getQuantity().multiply(position.getEntryPrice());
        BigDecimal newQuantity = position.getQuantity().add(quantity);
        position.setEntryPrice(scaleMoney(currentValue.add(notional).divide(newQuantity, MONEY_SCALE, RoundingMode.HALF_UP)));
        position.setQuantity(scaleMoney(newQuantity));
        positionRepository.save(position);
    }

    private void applySell(PaperSession session, String symbol, BigDecimal quantity, BigDecimal price, BigDecimal notional) {
        PaperPosition position = openPosition(session.getId(), symbol)
                .orElseThrow(() -> new BadRequestException("No open paper position exists for the symbol"));
        session.setCashBalance(scaleMoney(session.getCashBalance().add(notional)));
        if (position.getQuantity().compareTo(quantity) == 0) {
            position.setStatus(PaperPositionStatus.CLOSED);
            position.setExitPrice(price);
            position.setClosedAt(Instant.now());
        } else {
            position.setQuantity(scaleMoney(position.getQuantity().subtract(quantity)));
        }
        positionRepository.save(position);
    }

    private java.util.Optional<PaperPosition> openPosition(Long sessionId, String symbol) {
        return positionRepository.findFirstByPaperSessionIdAndSymbolAndStatusOrderByOpenedAtAsc(sessionId, symbol, PaperPositionStatus.OPEN);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
