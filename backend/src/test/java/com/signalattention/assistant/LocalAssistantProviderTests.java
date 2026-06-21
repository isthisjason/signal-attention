package com.signalattention.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalAssistantProviderTests {

    private final LocalAssistantProvider provider = new LocalAssistantProvider();

    @Test
    void replyProposesRegimeReplayWhenContextIsConcrete() {
        AssistantReply reply = provider.reply(
                "run regime replay",
                context(1L, 2L, null)
        );

        assertThat(reply.content()).contains("cannot give buy or sell advice");
        assertThat(reply.content()).contains("average confidence is 72.5%");
        assertThat(reply.proposedActions()).hasSize(1);
        AssistantProposedAction action = reply.proposedActions().getFirst();
        assertThat(action.actionType()).isEqualTo(AssistantActionType.RUN_REGIME_REPLAY);
        assertThat(action.payload()).containsEntry("strategyId", 1L);
        assertThat(action.payload()).containsEntry("backtestId", 2L);
    }

    @Test
    void replyDoesNotProposeActionWithoutRequiredContext() {
        AssistantReply reply = provider.reply("run a backtest", context(null, null, null));

        assertThat(reply.proposedActions()).isEmpty();
        assertThat(reply.content()).contains("Select a strategy and date range");
    }

    @Test
    void replyProposesPaperReplayForSelectedSession() {
        AssistantReply reply = provider.reply(
                "paper replay",
                context(1L, null, 7L)
        );

        assertThat(reply.proposedActions()).singleElement()
                .extracting(AssistantProposedAction::actionType)
                .isEqualTo(AssistantActionType.REPLAY_PAPER_SESSION);
    }

    @Test
    void replyProposesAttentionDiagnosticsForSelectedStrategy() {
        AssistantReply reply = provider.reply(
                "inspect attention evidence",
                context(1L, null, null)
        );

        assertThat(reply.proposedActions()).singleElement()
                .extracting(AssistantProposedAction::actionType)
                .isEqualTo(AssistantActionType.INSPECT_ATTENTION_DIAGNOSTICS);
        assertThat(reply.proposedActions().getFirst().payload()).containsEntry("limit", 20);
    }

    @Test
    void replyProposesModelLabReview() {
        AssistantReply reply = provider.reply(
                "review model lab promotion",
                context(1L, null, null)
        );

        assertThat(reply.content()).contains("Model lab has 2 runs");
        assertThat(reply.proposedActions()).singleElement()
                .extracting(AssistantProposedAction::actionType)
                .isEqualTo(AssistantActionType.REVIEW_MODEL_LAB);
    }

    @Test
    void replyProposesRobustnessReviewForLatestRegimeRun() {
        AssistantReply reply = provider.reply(
                "review robustness",
                context(1L, 2L, null)
        );

        assertThat(reply.proposedActions()).singleElement()
                .extracting(AssistantProposedAction::actionType)
                .isEqualTo(AssistantActionType.REVIEW_REGIME_ROBUSTNESS);
        assertThat(reply.proposedActions().getFirst().payload()).containsEntry("regimeRunId", 33L);
        assertThat(reply.proposedActions().getFirst().payload()).containsEntry("backtestId", 2L);
    }

    private AssistantContext context(Long strategyId, Long backtestId, Long paperSessionId) {
        return new AssistantContext(
                1,
                1,
                paperSessionId == null ? 0 : 1,
                strategyId,
                backtestId,
                paperSessionId,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-10T00:00:00Z"),
                33L,
                "TRENDING_UP",
                3,
                new BigDecimal("72.500000"),
                new BigDecimal("33.333333"),
                false,
                false,
                "mixed",
                2,
                1,
                "run-123",
                0
        );
    }
}
