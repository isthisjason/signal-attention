package com.signalattention.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.audit.AuditService;
import com.signalattention.backtesting.BacktestRequest;
import com.signalattention.backtesting.BacktestRunResponse;
import com.signalattention.backtesting.BacktestService;
import com.signalattention.common.BadRequestException;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.marketregime.MarketRegimeService;
import com.signalattention.marketregime.RegimeRunRequest;
import com.signalattention.marketregime.RegimeRunResponse;
import com.signalattention.ml.MlMarketRegimeDiagnosticsResponse;
import com.signalattention.papertrading.PaperSessionReplayRequest;
import com.signalattention.papertrading.PaperSessionReplayResponse;
import com.signalattention.papertrading.PaperSessionResponse;
import com.signalattention.papertrading.PaperTradingService;
import com.signalattention.strategies.Strategy;
import com.signalattention.strategies.StrategyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AssistantActionExecutor {

    private final ObjectMapper objectMapper;
    private final BacktestService backtestService;
    private final MarketRegimeService marketRegimeService;
    private final PaperTradingService paperTradingService;
    private final StrategyRepository strategyRepository;
    private final AuditService auditService;

    public AssistantActionExecutor(
            ObjectMapper objectMapper,
            BacktestService backtestService,
            MarketRegimeService marketRegimeService,
            PaperTradingService paperTradingService,
            StrategyRepository strategyRepository,
            AuditService auditService
    ) {
        this.objectMapper = objectMapper;
        this.backtestService = backtestService;
        this.marketRegimeService = marketRegimeService;
        this.paperTradingService = paperTradingService;
        this.strategyRepository = strategyRepository;
        this.auditService = auditService;
    }

    public String execute(AssistantAction action) {
        // Only enum-backed actions reach this switch, keeping assistant execution on known service paths.
        Object result = switch (action.getActionType()) {
            case RUN_BACKTEST -> runBacktest(action);
            case RUN_REGIME_REPLAY -> runRegimeReplay(action);
            case INSPECT_ATTENTION_DIAGNOSTICS -> inspectAttentionDiagnostics(action);
            case REVIEW_MODEL_LAB -> marketRegimeService.getExperimentDiagnostics();
            case REVIEW_REGIME_ROBUSTNESS -> reviewRegimeRobustness(action);
            case START_PAPER_SESSION -> startPaperSession(action);
            case REPLAY_PAPER_SESSION -> replayPaperSession(action);
        };
        return toJson(result);
    }

    private BacktestRunResponse runBacktest(AssistantAction action) {
        JsonNode payload = payload(action);
        Long strategyId = requiredLong(payload, "strategyId");
        BacktestRequest request = new BacktestRequest(
                requiredInstant(payload, "startDate"),
                requiredInstant(payload, "endDate"),
                optionalBigDecimal(payload, "initialBalance"),
                optionalBigDecimal(payload, "feePercent"),
                optionalBigDecimal(payload, "positionSizePercent")
        );
        return backtestService.runBacktest(strategyId, request);
    }

    private RegimeRunResponse runRegimeReplay(AssistantAction action) {
        JsonNode payload = payload(action);
        Long strategyId = requiredLong(payload, "strategyId");
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + strategyId));
        RegimeRunRequest request = new RegimeRunRequest(
                strategy.getSymbol(),
                strategy.getTimeframe(),
                requiredInstant(payload, "startDate"),
                requiredInstant(payload, "endDate"),
                optionalInt(payload, "windowSize"),
                optionalInt(payload, "stride"),
                optionalBoolean(payload, "includeAnomalies"),
                optionalLong(payload, "backtestId")
        );
        return marketRegimeService.runRegimeReplay(request);
    }

    private MlMarketRegimeDiagnosticsResponse inspectAttentionDiagnostics(AssistantAction action) {
        JsonNode payload = payload(action);
        Long strategyId = requiredLong(payload, "strategyId");
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + strategyId));
        // Assistant diagnostics reuse the selected strategy market so confirmation cannot redirect analysis elsewhere.
        return marketRegimeService.diagnoseMarketRegime(
                strategy.getSymbol(),
                strategy.getTimeframe(),
                optionalInt(payload, "limit"),
                optionalInstant(payload, "windowEnd")
        );
    }

    private Object reviewRegimeRobustness(AssistantAction action) {
        JsonNode payload = payload(action);
        return marketRegimeService.summarizeRobustness(
                requiredLong(payload, "regimeRunId"),
                optionalLong(payload, "backtestId")
        );
    }

    private PaperSessionResponse startPaperSession(AssistantAction action) {
        return paperTradingService.startSession(requiredLong(payload(action), "paperSessionId"));
    }

    private PaperSessionReplayResponse replayPaperSession(AssistantAction action) {
        JsonNode payload = payload(action);
        PaperSessionReplayRequest request = new PaperSessionReplayRequest(
                requiredInstant(payload, "startDate"),
                requiredInstant(payload, "endDate"),
                optionalInt(payload, "maxCandles")
        );
        return paperTradingService.replay(requiredLong(payload, "paperSessionId"), request);
    }

    public void auditAction(AssistantAction action, String event, String message) {
        auditService.record("ASSISTANT_ACTION", String.valueOf(action.getId()), event, message, action.getPayloadJson());
    }

    private JsonNode payload(AssistantAction action) {
        try {
            return objectMapper.readTree(action.getPayloadJson());
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Assistant action payload is invalid JSON");
        }
    }

    private Long requiredLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new BadRequestException("Assistant action payload requires " + field);
        }
        return value.longValue();
    }

    private Long optionalLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.longValue();
    }

    private Integer optionalInt(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.intValue();
    }

    private Boolean optionalBoolean(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.booleanValue();
    }

    private Instant requiredInstant(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual()) {
            throw new BadRequestException("Assistant action payload requires " + field);
        }
        return Instant.parse(value.textValue());
    }

    private Instant optionalInstant(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : Instant.parse(value.textValue());
    }

    private BigDecimal optionalBigDecimal(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(Map.of("result", value));
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to serialize assistant action result");
        }
    }
}
