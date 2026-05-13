package com.signalattention.papertrading;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.indicators.CrossoverSignal;
import com.signalattention.indicators.CrossoverSignalType;
import com.signalattention.indicators.SmaCrossoverDetector;
import com.signalattention.marketdata.MarketCandle;
import com.signalattention.marketdata.MarketCandleRepository;
import com.signalattention.strategies.SmaCrossoverRulesRequest;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import com.signalattention.strategies.StrategyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final MarketCandleRepository marketCandleRepository;
    private final SmaCrossoverDetector smaCrossoverDetector;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PaperTradingService(
            StrategyRepository strategyRepository,
            PaperSessionRepository sessionRepository,
            PaperOrderRepository orderRepository,
            PaperPositionRepository positionRepository,
            PaperOrderExecutor orderExecutor,
            MarketCandleRepository marketCandleRepository,
            SmaCrossoverDetector smaCrossoverDetector,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.strategyRepository = strategyRepository;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.orderExecutor = orderExecutor;
        this.marketCandleRepository = marketCandleRepository;
        this.smaCrossoverDetector = smaCrossoverDetector;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
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

    @Transactional(readOnly = true)
    public PaperSessionResponse getSession(Long id) {
        return PaperSessionResponse.from(findSession(id));
    }

    @Transactional(readOnly = true)
    public List<PaperSessionResponse> getStrategySessions(Long strategyId) {
        if (!strategyRepository.existsById(strategyId)) {
            throw new ResourceNotFoundException("Strategy not found: " + strategyId);
        }
        return sessionRepository.findByStrategyIdOrderByCreatedAtDesc(strategyId).stream()
                .map(PaperSessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaperSessionSummaryResponse getSummary(Long id) {
        PaperSession session = findSession(id);
        List<PaperPosition> openPositions = positionRepository.findByPaperSessionIdAndStatusOrderByOpenedAtAsc(id, PaperPositionStatus.OPEN);
        List<PaperPositionMarkResponse> marks = openPositions.stream()
                .map(position -> markPosition(session, position))
                .toList();
        BigDecimal openPositionValue = marks.stream()
                .map(PaperPositionMarkResponse::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unrealizedPnl = marks.stream()
                .map(PaperPositionMarkResponse::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal realizedPnl = positionRepository.findByPaperSessionIdOrderByOpenedAtAsc(id).stream()
                .filter(position -> position.getStatus() == PaperPositionStatus.CLOSED && position.getExitPrice() != null)
                .map(position -> position.getExitPrice().subtract(position.getEntryPrice()).multiply(position.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEquity = scaleMoney(session.getCashBalance().add(openPositionValue));
        boolean hasUnpricedPositions = marks.stream().anyMatch(mark -> !mark.priced());

        return new PaperSessionSummaryResponse(
                session.getId(),
                session.getStrategy().getId(),
                session.getStatus(),
                session.getInitialBalance(),
                session.getCashBalance(),
                scaleMoney(openPositionValue),
                scaleMoney(realizedPnl),
                scaleMoney(unrealizedPnl),
                totalEquity,
                hasUnpricedPositions,
                marks
        );
    }

    @Transactional
    public PaperSessionReplayResponse replay(Long id, PaperSessionReplayRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        PaperSession session = findSession(id);
        if (session.getStatus() != PaperSessionStatus.RUNNING) {
            throw new BadRequestException("Paper session must be running to replay candles");
        }
        Strategy strategy = session.getStrategy();
        if (strategy.getStrategyType() != StrategyType.SMA_CROSSOVER) {
            throw new BadRequestException("Unsupported strategy type: " + strategy.getStrategyType());
        }

        SmaCrossoverRulesRequest rules = parseRules(strategy);
        List<MarketCandle> candles = marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                strategy.getSymbol(),
                strategy.getTimeframe(),
                request.startDate(),
                request.endDate()
        );
        if (request.maxCandles() != null && candles.size() > request.maxCandles()) {
            candles = candles.subList(0, request.maxCandles());
        }
        if (candles.isEmpty()) {
            throw new BadRequestException("No candles found for requested paper replay range");
        }
        if (candles.size() < rules.longWindow()) {
            throw new BadRequestException("Not enough candles for long SMA window");
        }

        Map<Integer, CrossoverSignalType> signalsByIndex = smaCrossoverDetector.detect(
                        candles.stream().map(MarketCandle::getClose).toList(),
                        rules.shortWindow(),
                        rules.longWindow()
                )
                .stream()
                .collect(Collectors.toMap(CrossoverSignal::index, CrossoverSignal::type));

        int signalsProcessed = 0;
        int filledOrders = 0;
        int rejectedOrders = 0;
        for (int index = 0; index < candles.size(); index++) {
            CrossoverSignalType signal = signalsByIndex.get(index);
            if (signal == null) {
                continue;
            }
            PaperOrderRequest orderRequest = orderRequestForSignal(session, strategy, rules, candles.get(index), signal);
            if (orderRequest == null) {
                continue;
            }
            PaperOrder order = orderExecutor.execute(session, orderRequest);
            signalsProcessed++;
            if (order.getStatus() == PaperOrderStatus.FILLED) {
                filledOrders++;
            } else {
                rejectedOrders++;
            }
            auditService.record(ENTITY_TYPE, id.toString(), "PAPER_REPLAY_ORDER_" + order.getStatus().name(), "Paper replay order " + order.getStatus().name().toLowerCase(), orderMetadata(order));
        }

        PaperSessionReplayResponse response = new PaperSessionReplayResponse(
                session.getId(),
                candles.size(),
                signalsProcessed,
                filledOrders,
                rejectedOrders,
                request.startDate(),
                request.endDate()
        );
        auditService.record(ENTITY_TYPE, id.toString(), "PAPER_REPLAY_COMPLETED", "Paper candle replay completed", replayMetadata(response));
        return response;
    }

    private PaperSession findSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paper session not found: " + id));
    }

    private PaperPositionMarkResponse markPosition(PaperSession session, PaperPosition position) {
        MarketCandle latest = marketCandleRepository.findFirstBySymbolAndTimeframeOrderByOpenTimeDesc(
                position.getSymbol(),
                session.getStrategy().getTimeframe()
        );
        if (latest == null) {
            return new PaperPositionMarkResponse(
                    position.getId(),
                    position.getSymbol(),
                    position.getQuantity(),
                    position.getEntryPrice(),
                    null,
                    null,
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    false
            );
        }
        BigDecimal marketValue = scaleMoney(position.getQuantity().multiply(latest.getClose()));
        BigDecimal costBasis = position.getQuantity().multiply(position.getEntryPrice());
        return new PaperPositionMarkResponse(
                position.getId(),
                position.getSymbol(),
                position.getQuantity(),
                position.getEntryPrice(),
                latest.getClose(),
                latest.getOpenTime(),
                marketValue,
                scaleMoney(marketValue.subtract(costBasis)),
                true
        );
    }

    private PaperOrderRequest orderRequestForSignal(
            PaperSession session,
            Strategy strategy,
            SmaCrossoverRulesRequest rules,
            MarketCandle candle,
            CrossoverSignalType signal
    ) {
        if (signal == CrossoverSignalType.BULLISH_CROSSOVER) {
            if (openPosition(session.getId(), strategy.getSymbol()) != null) {
                return null;
            }
            BigDecimal allocation = session.getCashBalance()
                    .multiply(rules.positionSizePercent())
                    .divide(new BigDecimal("100"), MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal quantity = allocation.divide(candle.getClose(), MONEY_SCALE, RoundingMode.HALF_UP);
            if (quantity.signum() <= 0) {
                return null;
            }
            return new PaperOrderRequest(PaperOrderSide.BUY, strategy.getSymbol(), quantity, candle.getClose());
        }
        PaperPosition position = openPosition(session.getId(), strategy.getSymbol());
        if (position == null) {
            return null;
        }
        return new PaperOrderRequest(PaperOrderSide.SELL, strategy.getSymbol(), position.getQuantity(), candle.getClose());
    }

    private PaperPosition openPosition(Long sessionId, String symbol) {
        return positionRepository.findFirstByPaperSessionIdAndSymbolAndStatusOrderByOpenedAtAsc(sessionId, symbol, PaperPositionStatus.OPEN)
                .orElse(null);
    }

    private void validateDateRange(Instant startDate, Instant endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must be before or equal to endDate");
        }
    }

    private SmaCrossoverRulesRequest parseRules(Strategy strategy) {
        try {
            return objectMapper.readValue(strategy.getRulesJson(), SmaCrossoverRulesRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Invalid strategy rules", exception);
        }
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

    private String replayMetadata(PaperSessionReplayResponse response) {
        return "{\"paperSessionId\":" + response.paperSessionId()
                + ",\"candlesRead\":" + response.candlesRead()
                + ",\"signalsProcessed\":" + response.signalsProcessed()
                + ",\"filledOrders\":" + response.filledOrders()
                + ",\"rejectedOrders\":" + response.rejectedOrders()
                + "}";
    }
}
