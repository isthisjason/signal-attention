package com.signalattention.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.common.BadRequestException;
import com.signalattention.common.ResourceNotFoundException;
import com.signalattention.backtesting.BacktestRunRepository;
import com.signalattention.marketregime.RegimePredictionRepository;
import com.signalattention.marketregime.RegimeRun;
import com.signalattention.marketregime.RegimeRunRepository;
import com.signalattention.papertrading.PaperSessionStatus;
import com.signalattention.papertrading.PaperSessionRepository;
import com.signalattention.strategies.StrategyRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantService {

    private final AssistantSessionRepository sessionRepository;
    private final AssistantMessageRepository messageRepository;
    private final AssistantActionRepository actionRepository;
    private final AssistantProvider assistantProvider;
    private final AssistantActionExecutor actionExecutor;
    private final ObjectMapper objectMapper;
    private final StrategyRepository strategyRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final PaperSessionRepository paperSessionRepository;
    private final RegimeRunRepository regimeRunRepository;
    private final RegimePredictionRepository regimePredictionRepository;

    public AssistantService(
            AssistantSessionRepository sessionRepository,
            AssistantMessageRepository messageRepository,
            AssistantActionRepository actionRepository,
            AssistantProvider assistantProvider,
            AssistantActionExecutor actionExecutor,
            ObjectMapper objectMapper,
            StrategyRepository strategyRepository,
            BacktestRunRepository backtestRunRepository,
            PaperSessionRepository paperSessionRepository,
            RegimeRunRepository regimeRunRepository,
            RegimePredictionRepository regimePredictionRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.actionRepository = actionRepository;
        this.assistantProvider = assistantProvider;
        this.actionExecutor = actionExecutor;
        this.objectMapper = objectMapper;
        this.strategyRepository = strategyRepository;
        this.backtestRunRepository = backtestRunRepository;
        this.paperSessionRepository = paperSessionRepository;
        this.regimeRunRepository = regimeRunRepository;
        this.regimePredictionRepository = regimePredictionRepository;
    }

    @Transactional
    public AssistantSessionResponse createSession(AssistantCreateSessionRequest request) {
        String title = request == null || request.title() == null || request.title().isBlank()
                ? "Research assistant"
                : request.title().trim();
        AssistantSession session = sessionRepository.save(new AssistantSession(title));
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public AssistantSessionResponse getSession(Long id) {
        return toResponse(findSession(id));
    }

    @Transactional
    public AssistantSessionResponse sendMessage(Long sessionId, AssistantMessageRequest request) {
        AssistantSession session = findSession(sessionId);
        String prompt = requirePrompt(request);
        AssistantMessage userMessage = messageRepository.save(new AssistantMessage(session, AssistantMessageRole.USER, prompt));
        AssistantContext context = buildContextSnapshot(request);
        AssistantReply reply = assistantProvider.reply(prompt, context);
        AssistantMessage assistantMessage = messageRepository.save(new AssistantMessage(session, AssistantMessageRole.ASSISTANT, reply.content()));
        saveAssistantTurn(session, assistantMessage, reply);
        session.touch();
        sessionRepository.save(session);
        return toResponse(session);
    }

    @Transactional
    public AssistantActionResponse confirmAction(Long actionId) {
        AssistantAction action = findAction(actionId);
        confirmActionBoundary(action);
        action.markConfirmed();
        actionRepository.save(action);
        actionExecutor.auditAction(action, "ASSISTANT_ACTION_CONFIRMED", "Assistant action confirmed");
        try {
            String resultJson = actionExecutor.execute(action);
            action.markExecuted(resultJson);
            actionExecutor.auditAction(action, "ASSISTANT_ACTION_EXECUTED", "Assistant action executed");
        } catch (RuntimeException exception) {
            action.markFailed(exception.getMessage());
            actionExecutor.auditAction(action, "ASSISTANT_ACTION_FAILED", exception.getMessage());
        }
        return AssistantActionResponse.from(actionRepository.save(action));
    }

    @Transactional
    public AssistantActionResponse rejectAction(Long actionId) {
        AssistantAction action = findAction(actionId);
        action.markRejected();
        actionExecutor.auditAction(action, "ASSISTANT_ACTION_REJECTED", "Assistant action rejected");
        return AssistantActionResponse.from(actionRepository.save(action));
    }

    AssistantContext buildContextSnapshot(AssistantMessageRequest request) {
        // Context is intentionally a compact snapshot so providers cannot mutate domain state directly.
        RegimeRun latestRun = regimeRunRepository.findAll().stream()
                .max(Comparator.comparing(RegimeRun::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        String latestLabel = null;
        Integer pointCount = null;
        if (latestRun != null) {
            pointCount = latestRun.getPointCount();
            latestLabel = regimePredictionRepository.findByRegimeRunIdOrderByWindowStartAsc(latestRun.getId()).stream()
                    .reduce((first, second) -> second)
                    .map(prediction -> prediction.getRegimeLabel())
                    .orElse(null);
        }
        return new AssistantContext(
                strategyRepository.count(),
                backtestRunRepository.count(),
                paperSessionRepository.countByStatus(PaperSessionStatus.RUNNING),
                request == null ? null : request.strategyId(),
                request == null ? null : request.backtestId(),
                request == null ? null : request.paperSessionId(),
                request == null ? null : request.startDate(),
                request == null ? null : request.endDate(),
                latestLabel,
                pointCount
        );
    }

    void saveAssistantTurn(AssistantSession session, AssistantMessage assistantMessage, AssistantReply reply) {
        // Proposed actions are persisted with JSON payloads so users can review exactly what will run.
        List<AssistantAction> actions = reply.proposedActions().stream()
                .map(action -> new AssistantAction(
                        session,
                        assistantMessage,
                        action.actionType(),
                        action.summary(),
                        toJson(action.payload())
                ))
                .toList();
        actionRepository.saveAll(actions);
        actions.forEach(action -> actionExecutor.auditAction(action, "ASSISTANT_ACTION_PROPOSED", action.getSummary()));
    }

    private void confirmActionBoundary(AssistantAction action) {
        // A proposed action is the only mutable assistant state that may cross into domain services.
        if (action.getStatus() != AssistantActionStatus.PROPOSED) {
            throw new BadRequestException("Only proposed assistant actions can be confirmed");
        }
    }

    private String requirePrompt(AssistantMessageRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new BadRequestException("prompt is required");
        }
        return request.prompt().trim();
    }

    private AssistantSession findSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assistant session not found: " + id));
    }

    private AssistantAction findAction(Long id) {
        return actionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assistant action not found: " + id));
    }

    private AssistantSessionResponse toResponse(AssistantSession session) {
        return new AssistantSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                        .map(AssistantMessageResponse::from)
                        .toList(),
                actionRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                        .map(AssistantActionResponse::from)
                        .toList()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to serialize assistant action payload");
        }
    }
}
