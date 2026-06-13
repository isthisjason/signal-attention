package com.signalattention.marketregime;

import com.signalattention.ml.MlMarketRegimeResponse;
import com.signalattention.ml.MlMarketRegimeStatusResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class MarketRegimeController {

    private final MarketRegimeService marketRegimeService;

    public MarketRegimeController(MarketRegimeService marketRegimeService) {
        this.marketRegimeService = marketRegimeService;
    }

    @GetMapping("/api/market-regime")
    public MlMarketRegimeResponse getMarketRegime(
            @RequestParam String symbol,
            @RequestParam String timeframe,
            @RequestParam(defaultValue = "128") Integer limit
    ) {
        return marketRegimeService.predictMarketRegime(symbol, timeframe, limit);
    }

    @GetMapping("/api/market-regime/status")
    public MlMarketRegimeStatusResponse getMarketRegimeStatus() {
        return marketRegimeService.getModelStatus();
    }

    @PostMapping("/api/regime-runs")
    public RegimeRunResponse runRegimeReplay(@Valid @RequestBody RegimeRunRequest request) {
        return marketRegimeService.runRegimeReplay(request);
    }

    @GetMapping("/api/regime-runs/{id}")
    public RegimeRunResponse getRegimeRun(@PathVariable Long id) {
        return marketRegimeService.getRegimeRun(id);
    }

    @GetMapping("/api/regime-runs")
    public List<RegimeRunSummaryResponse> listRegimeRuns(
            @RequestParam String symbol,
            @RequestParam String timeframe,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return marketRegimeService.listRegimeRuns(symbol, timeframe, limit);
    }

    @GetMapping("/api/backtests/{id}/regime-analysis")
    public RegimeBacktestAnalysisResponse analyzeBacktestByRegime(
            @PathVariable Long id,
            @RequestParam Long regimeRunId
    ) {
        return marketRegimeService.analyzeBacktestByRegime(id, regimeRunId);
    }
}
