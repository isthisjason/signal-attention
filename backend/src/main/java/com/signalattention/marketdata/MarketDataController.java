package com.signalattention.marketdata;

import com.signalattention.common.BadRequestException;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MarketDataImportSummary importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("CSV file is required");
        }
        try {
            return marketDataService.importCsv(file.getInputStream());
        } catch (IOException exception) {
            throw new BadRequestException("Unable to read CSV file", exception);
        }
    }

    @GetMapping("/candles")
    public List<CandleResponse> candles(
            @RequestParam String symbol,
            @RequestParam String timeframe,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {
        return marketDataService.findCandles(symbol, timeframe, parseInstant("start", start), parseInstant("end", end));
    }

    @GetMapping("/quality")
    public MarketDataQualityResponse quality(@RequestParam String symbol, @RequestParam String timeframe) {
        return marketDataService.analyzeQuality(symbol, timeframe);
    }

    private Instant parseInstant(String parameterName, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(parameterName + " must be an ISO-8601 instant", exception);
        }
    }
}
