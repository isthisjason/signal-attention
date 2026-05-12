package com.signalattention.strategies;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strategies")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @PostMapping
    public ResponseEntity<StrategyResponse> create(@Valid @RequestBody StrategyCreateRequest request) {
        StrategyResponse response = strategyService.create(request);
        return ResponseEntity
                .created(URI.create("/api/strategies/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<StrategyResponse> list() {
        return strategyService.list();
    }

    @GetMapping("/{id}")
    public StrategyResponse get(@PathVariable Long id) {
        return strategyService.get(id);
    }

    @PutMapping("/{id}")
    public StrategyResponse update(@PathVariable Long id, @Valid @RequestBody StrategyUpdateRequest request) {
        return strategyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        strategyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
