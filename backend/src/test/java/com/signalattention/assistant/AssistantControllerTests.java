package com.signalattention.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantControllerTests {

    @Test
    void createSessionDelegatesToService() {
        AssistantService service = org.mockito.Mockito.mock(AssistantService.class);
        AssistantController controller = new AssistantController(service);
        AssistantCreateSessionRequest request = new AssistantCreateSessionRequest("Research");
        AssistantSessionResponse expected = sessionResponse();
        when(service.createSession(request)).thenReturn(expected);

        AssistantSessionResponse actual = controller.createSession(request);

        assertThat(actual).isSameAs(expected);
        verify(service).createSession(request);
    }

    @Test
    void sendMessageDelegatesToService() {
        AssistantService service = org.mockito.Mockito.mock(AssistantService.class);
        AssistantController controller = new AssistantController(service);
        AssistantMessageRequest request = new AssistantMessageRequest(
                "run regime replay",
                1L,
                2L,
                null,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-10T00:00:00Z")
        );
        AssistantSessionResponse expected = sessionResponse();
        when(service.sendMessage(10L, request)).thenReturn(expected);

        AssistantSessionResponse actual = controller.sendMessage(10L, request);

        assertThat(actual).isSameAs(expected);
        verify(service).sendMessage(10L, request);
    }

    @Test
    void actionEndpointsDelegateToService() {
        AssistantService service = org.mockito.Mockito.mock(AssistantService.class);
        AssistantController controller = new AssistantController(service);
        AssistantActionResponse expected = actionResponse(AssistantActionStatus.EXECUTED);
        when(service.confirmAction(5L)).thenReturn(expected);
        when(service.rejectAction(6L)).thenReturn(actionResponse(AssistantActionStatus.REJECTED));

        assertThat(controller.confirmAction(5L)).isSameAs(expected);
        assertThat(controller.rejectAction(6L).status()).isEqualTo(AssistantActionStatus.REJECTED);
        verify(service).confirmAction(5L);
        verify(service).rejectAction(6L);
    }

    private AssistantSessionResponse sessionResponse() {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        return new AssistantSessionResponse(1L, "Research", now, now, List.of(), List.of());
    }

    private AssistantActionResponse actionResponse(AssistantActionStatus status) {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        return new AssistantActionResponse(
                5L,
                4L,
                AssistantActionType.RUN_REGIME_REPLAY,
                status,
                "Run replay",
                "{}",
                null,
                null,
                now,
                null,
                null,
                null
        );
    }
}
