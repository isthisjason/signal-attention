package com.signalattention.papertrading;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaperTradingController {

    private final PaperTradingService paperTradingService;

    public PaperTradingController(PaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
    }

    @PostMapping("/api/strategies/{strategyId}/paper-sessions")
    public ResponseEntity<PaperSessionResponse> createSession(
            @PathVariable Long strategyId,
            @Valid @RequestBody PaperSessionCreateRequest request
    ) {
        PaperSessionResponse response = paperTradingService.createSession(strategyId, request);
        return ResponseEntity
                .created(URI.create("/api/paper-sessions/" + response.id()))
                .body(response);
    }

    @PatchMapping("/api/paper-sessions/{id}/start")
    public PaperSessionResponse startSession(@PathVariable Long id) {
        return paperTradingService.startSession(id);
    }

    @PatchMapping("/api/paper-sessions/{id}/stop")
    public PaperSessionResponse stopSession(@PathVariable Long id) {
        return paperTradingService.stopSession(id);
    }

    @GetMapping("/api/paper-sessions/{id}")
    public PaperSessionResponse getSession(@PathVariable Long id) {
        return paperTradingService.getSession(id);
    }

    @GetMapping("/api/strategies/{strategyId}/paper-sessions")
    public List<PaperSessionResponse> getStrategySessions(@PathVariable Long strategyId) {
        return paperTradingService.getStrategySessions(strategyId);
    }

    @GetMapping("/api/paper-sessions/{id}/summary")
    public PaperSessionSummaryResponse getSummary(@PathVariable Long id) {
        return paperTradingService.getSummary(id);
    }

    @PostMapping("/api/paper-sessions/{id}/replay")
    public PaperSessionReplayResponse replay(@PathVariable Long id, @Valid @RequestBody PaperSessionReplayRequest request) {
        return paperTradingService.replay(id, request);
    }

    @PostMapping("/api/paper-sessions/{id}/orders")
    public PaperOrderResponse submitOrder(@PathVariable Long id, @Valid @RequestBody PaperOrderRequest request) {
        return paperTradingService.submitOrder(id, request);
    }

    @GetMapping("/api/paper-sessions/{id}/orders")
    public List<PaperOrderResponse> getOrders(@PathVariable Long id) {
        return paperTradingService.getOrders(id);
    }

    @GetMapping("/api/paper-sessions/{id}/positions")
    public List<PaperPositionResponse> getPositions(@PathVariable Long id) {
        return paperTradingService.getPositions(id);
    }
}
