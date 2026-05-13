package com.signalattention.dashboard;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/summary")
    public DashboardSummaryResponse getSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/api/dashboard/strategy-performance")
    public List<DashboardStrategyPerformanceResponse> getStrategyPerformance() {
        return dashboardService.getStrategyPerformance();
    }
}
