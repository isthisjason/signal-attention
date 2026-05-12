package com.signalattention.marketdata;

import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketDataService {

    private static final String ENTITY_TYPE = "MARKET_DATA";

    private final CandleCsvParser candleCsvParser;
    private final MarketCandleRepository marketCandleRepository;
    private final AuditService auditService;

    public MarketDataService(
            CandleCsvParser candleCsvParser,
            MarketCandleRepository marketCandleRepository,
            AuditService auditService
    ) {
        this.candleCsvParser = candleCsvParser;
        this.marketCandleRepository = marketCandleRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MarketDataImportSummary importCsv(InputStream inputStream) {
        CandleCsvParseResult parseResult;
        try {
            parseResult = candleCsvParser.parse(inputStream);
        } catch (IOException exception) {
            auditService.record(ENTITY_TYPE, null, "CSV_IMPORT_FAILED", "CSV import failed", null);
            throw new BadRequestException("Unable to read CSV file", exception);
        }

        List<MarketDataImportError> errors = new ArrayList<>(parseResult.errors());
        int imported = 0;
        Set<String> seenImportKeys = new HashSet<>();

        for (ParsedMarketCandle parsedCandle : parseResult.candles()) {
            String importKey = importKey(parsedCandle);
            if (!seenImportKeys.add(importKey)) {
                errors.add(new MarketDataImportError(parsedCandle.rowNumber(), "Duplicate candle in CSV"));
                continue;
            }
            if (marketCandleRepository.existsBySymbolAndTimeframeAndOpenTime(
                    parsedCandle.symbol(),
                    parsedCandle.timeframe(),
                    parsedCandle.openTime()
            )) {
                errors.add(new MarketDataImportError(parsedCandle.rowNumber(), "Duplicate candle already exists"));
                continue;
            }

            marketCandleRepository.save(parsedCandle.toEntity());
            imported++;
        }

        MarketDataImportSummary summary = new MarketDataImportSummary(
                parseResult.totalRows(),
                imported,
                parseResult.totalRows() - imported,
                List.copyOf(errors)
        );
        auditService.record(
                ENTITY_TYPE,
                null,
                "CSV_IMPORT_COMPLETED",
                "CSV import completed",
                "{\"totalRows\":" + summary.totalRows()
                        + ",\"rowsImported\":" + summary.rowsImported()
                        + ",\"rowsRejected\":" + summary.rowsRejected() + "}"
        );
        return summary;
    }

    @Transactional(readOnly = true)
    public List<CandleResponse> findCandles(String symbol, String timeframe, Instant start, Instant end) {
        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("symbol is required");
        }
        if (timeframe == null || timeframe.isBlank()) {
            throw new BadRequestException("timeframe is required");
        }
        if (start != null && end != null && start.isAfter(end)) {
            throw new BadRequestException("start must be before or equal to end");
        }

        List<MarketCandle> candles;
        if (start != null && end != null) {
            candles = marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                    symbol,
                    timeframe,
                    start,
                    end
            );
        } else if (start != null) {
            candles = marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeGreaterThanEqualOrderByOpenTimeAsc(
                    symbol,
                    timeframe,
                    start
            );
        } else if (end != null) {
            candles = marketCandleRepository.findBySymbolAndTimeframeAndOpenTimeLessThanEqualOrderByOpenTimeAsc(
                    symbol,
                    timeframe,
                    end
            );
        } else {
            candles = marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe);
        }

        return candles.stream()
                .map(CandleResponse::from)
                .toList();
    }

    private String importKey(ParsedMarketCandle candle) {
        return candle.symbol() + "|" + candle.timeframe() + "|" + candle.openTime();
    }
}
