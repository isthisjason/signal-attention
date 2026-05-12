package com.signalattention.backtesting;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping("/api/strategies/{strategyId}/backtests")
    public ResponseEntity<BacktestRunResponse> runBacktest(
            @PathVariable Long strategyId,
            @Valid @RequestBody BacktestRequest request
    ) {
        BacktestRunResponse response = backtestService.runBacktest(strategyId, request);
        return ResponseEntity
                .created(URI.create("/api/backtests/" + response.id()))
                .body(response);
    }

    @GetMapping("/api/backtests/{id}")
    public BacktestRunResponse getRun(@PathVariable Long id) {
        return backtestService.getRun(id);
    }

    @GetMapping("/api/backtests/{id}/trades")
    public List<BacktestTradeResponse> getTrades(@PathVariable Long id) {
        return backtestService.getTrades(id);
    }

    @GetMapping("/api/backtests/{id}/metrics")
    public BacktestMetricsResponse getMetrics(@PathVariable Long id) {
        return backtestService.getMetrics(id);
    }
}
