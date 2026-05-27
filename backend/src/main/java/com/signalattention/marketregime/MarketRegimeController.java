package com.signalattention.marketregime;

import com.signalattention.ml.MlMarketRegimeResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/api/regime-runs")
    public RegimeRunResponse runRegimeReplay(@Valid @RequestBody RegimeRunRequest request) {
        return marketRegimeService.runRegimeReplay(request);
    }
}
