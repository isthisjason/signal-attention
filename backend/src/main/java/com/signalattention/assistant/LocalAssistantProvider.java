package com.signalattention.assistant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LocalAssistantProvider implements AssistantProvider {

    @Override
    public AssistantReply reply(String prompt, AssistantContext context) {
        // The local provider is deterministic so tests and demos do not depend on LLM credentials.
        String normalized = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        List<AssistantProposedAction> actions = new ArrayList<>();
        StringBuilder response = new StringBuilder();
        response.append("Current workspace has ")
                .append(context.strategyCount()).append(" strategies, ")
                .append(context.backtestCount()).append(" backtests, and ")
                .append(context.runningPaperSessionCount()).append(" running paper sessions. ");
        if (context.latestRegimeLabel() != null) {
            response.append("Latest saved regime run ended in ")
                    .append(context.latestRegimeLabel())
                    .append(" across ")
                    .append(context.latestRegimePointCount())
                    .append(" windows. ");
        }
        response.append("I can explain simulation state and prepare reviewable research actions, but I cannot give buy or sell advice.");

        if (normalized.contains("regime")) {
            maybeAddRegimeReplay(context, actions);
        } else if (normalized.contains("paper") && normalized.contains("replay")) {
            maybeAddPaperReplay(context, actions);
        } else if (normalized.contains("paper") && normalized.contains("start")) {
            maybeAddStartPaperSession(context, actions);
        } else if (normalized.contains("backtest") || normalized.contains("test")) {
            maybeAddBacktest(context, actions);
        } else if (context.strategyId() != null && context.startDate() != null && context.endDate() != null) {
            maybeAddRegimeReplay(context, actions);
        }

        if (actions.isEmpty()) {
            response.append(" Select a strategy and date range if you want me to prepare a concrete action.");
        } else {
            response.append(" I prepared one action for review.");
        }
        return new AssistantReply(response.toString(), actions);
    }

    private void maybeAddBacktest(AssistantContext context, List<AssistantProposedAction> actions) {
        if (context.strategyId() == null || context.startDate() == null || context.endDate() == null) {
            return;
        }
        actions.add(new AssistantProposedAction(
                AssistantActionType.RUN_BACKTEST,
                "Run a backtest for the selected strategy and date range.",
                Map.of("strategyId", context.strategyId(), "startDate", context.startDate().toString(), "endDate", context.endDate().toString())
        ));
    }

    private void maybeAddRegimeReplay(AssistantContext context, List<AssistantProposedAction> actions) {
        if (context.strategyId() == null || context.startDate() == null || context.endDate() == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("strategyId", context.strategyId());
        payload.put("startDate", context.startDate().toString());
        payload.put("endDate", context.endDate().toString());
        payload.put("windowSize", 64);
        payload.put("stride", 8);
        payload.put("includeAnomalies", true);
        if (context.backtestId() != null) {
            payload.put("backtestId", context.backtestId());
        }
        actions.add(new AssistantProposedAction(
                AssistantActionType.RUN_REGIME_REPLAY,
                "Run regime replay for the selected strategy and date range.",
                payload
        ));
    }

    private void maybeAddStartPaperSession(AssistantContext context, List<AssistantProposedAction> actions) {
        if (context.paperSessionId() == null) {
            return;
        }
        actions.add(new AssistantProposedAction(
                AssistantActionType.START_PAPER_SESSION,
                "Start the selected paper session.",
                Map.of("paperSessionId", context.paperSessionId())
        ));
    }

    private void maybeAddPaperReplay(AssistantContext context, List<AssistantProposedAction> actions) {
        if (context.paperSessionId() == null || context.startDate() == null || context.endDate() == null) {
            return;
        }
        actions.add(new AssistantProposedAction(
                AssistantActionType.REPLAY_PAPER_SESSION,
                "Replay candles through the selected paper session.",
                Map.of(
                        "paperSessionId", context.paperSessionId(),
                        "startDate", context.startDate().toString(),
                        "endDate", context.endDate().toString(),
                        "maxCandles", 200
                )
        ));
    }
}
