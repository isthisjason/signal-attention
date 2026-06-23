package com.signalattention.attentionshowcase;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AttentionShowcaseControllerTests {

    @Test
    void getSummaryReturnsShowcaseSummary() throws Exception {
        AttentionShowcaseService service = org.mockito.Mockito.mock(AttentionShowcaseService.class);
        when(service.getSummary()).thenReturn(new AttentionShowcaseSummaryResponse(
                true,
                "auto",
                "rules",
                "rules",
                "no_eligible_run",
                null,
                false,
                null,
                "needs_replay",
                List.of("No saved regime replay exists yet."),
                0L,
                new AttentionShowcaseSummaryResponse.DisagreementSummary(
                        0,
                        0,
                        BigDecimal.ZERO.setScale(6),
                        null,
                        null,
                        0,
                        List.of()
                ),
                "Run an attention regime replay.",
                List.of()
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AttentionShowcaseController(service)).build();

        mockMvc.perform(get("/api/attention-showcase/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelReady", is(true)))
                .andExpect(jsonPath("$.effectiveMode", is("rules")))
                .andExpect(jsonPath("$.robustnessLabel", is("needs_replay")))
                .andExpect(jsonPath("$.disagreementSummary.totalWindows", is(0)));
    }
}
