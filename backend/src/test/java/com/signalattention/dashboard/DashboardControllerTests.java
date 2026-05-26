package com.signalattention.dashboard;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardControllerTests {

    @Test
    void getRiskAlertsReturnsDashboardRiskAlerts() throws Exception {
        DashboardService service = org.mockito.Mockito.mock(DashboardService.class);
        when(service.getRiskAlerts()).thenReturn(List.of(new DashboardRiskAlertResponse(
                DashboardAlertSeverity.HIGH,
                "DRAWDOWN",
                "BACKTEST",
                "10",
                "Backtest drawdown reached 20%.",
                Instant.parse("2024-01-01T00:00:00Z")
        )));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();

        mockMvc.perform(get("/api/dashboard/risk-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].severity", is("HIGH")))
                .andExpect(jsonPath("$[0].category", is("DRAWDOWN")))
                .andExpect(jsonPath("$[0].entityId", is("10")));
    }
}
