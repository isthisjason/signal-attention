package com.signalattention.marketdata;

import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
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
            // Check duplicates in the upload before checking the database so error rows point to the CSV source.
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
        validateMarket(symbol, timeframe);
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

    @Transactional(readOnly = true)
    public MarketDataQualityResponse analyzeQuality(String symbol, String timeframe) {
        validateMarket(symbol, timeframe);
        // Quality checks assume the stored candles are sorted chronologically for gap detection.
        long expectedIntervalMinutes = expectedIntervalMinutes(timeframe);
        List<MarketCandle> candles = marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc(
                symbol,
                timeframe
        );

        int duplicateTimestampCount = 0;
        int gapCount = 0;
        int invalidOhlcCount = 0;
        int zeroOrNegativeVolumeCount = 0;
        List<String> warnings = new ArrayList<>();
        Instant previousOpenTime = null;

        for (MarketCandle candle : candles) {
            if (previousOpenTime != null) {
                long minutesBetweenCandles = Duration.between(previousOpenTime, candle.getOpenTime()).toMinutes();
                if (minutesBetweenCandles == 0) {
                    duplicateTimestampCount++;
                } else if (minutesBetweenCandles > expectedIntervalMinutes) {
                    gapCount++;
                }
            }
            previousOpenTime = candle.getOpenTime();

            if (hasInvalidOhlc(candle)) {
                invalidOhlcCount++;
            }
            if (candle.getVolume().compareTo(BigDecimal.ZERO) <= 0) {
                zeroOrNegativeVolumeCount++;
            }
        }

        if (candles.isEmpty()) {
            warnings.add("No candles found for " + symbol + " " + timeframe + ".");
        }
        if (duplicateTimestampCount > 0) {
            warnings.add(duplicateTimestampCount + " duplicate candle timestamp(s) found.");
        }
        if (gapCount > 0) {
            warnings.add(gapCount + " time gap(s) larger than the expected timeframe found.");
        }
        if (invalidOhlcCount > 0) {
            warnings.add(invalidOhlcCount + " candle(s) have inconsistent OHLC values.");
        }
        if (zeroOrNegativeVolumeCount > 0) {
            warnings.add(zeroOrNegativeVolumeCount + " candle(s) have zero or negative volume.");
        }
        if (!candles.isEmpty() && warnings.isEmpty()) {
            warnings.add("No market data quality warnings found.");
        }

        return new MarketDataQualityResponse(
                symbol,
                timeframe,
                candles.size(),
                candles.isEmpty() ? null : candles.getFirst().getOpenTime(),
                candles.isEmpty() ? null : candles.getLast().getOpenTime(),
                expectedIntervalMinutes,
                duplicateTimestampCount,
                gapCount,
                invalidOhlcCount,
                zeroOrNegativeVolumeCount,
                List.copyOf(warnings)
        );
    }

    private void validateMarket(String symbol, String timeframe) {
        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("symbol is required");
        }
        if (timeframe == null || timeframe.isBlank()) {
            throw new BadRequestException("timeframe is required");
        }
    }

    private long expectedIntervalMinutes(String timeframe) {
        String trimmed = timeframe.trim().toLowerCase();
        if (trimmed.length() < 2) {
            throw new BadRequestException("timeframe must use a supported interval such as 1m, 1h, or 1d");
        }

        long amount;
        try {
            amount = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
        } catch (NumberFormatException exception) {
            throw new BadRequestException("timeframe must use a supported interval such as 1m, 1h, or 1d", exception);
        }
        if (amount <= 0) {
            throw new BadRequestException("timeframe amount must be positive");
        }

        // Only fixed minute/hour/day intervals are supported by the local sample-data workflow.
        return switch (trimmed.charAt(trimmed.length() - 1)) {
            case 'm' -> amount;
            case 'h' -> amount * 60;
            case 'd' -> amount * 24 * 60;
            default -> throw new BadRequestException("timeframe must use a supported interval such as 1m, 1h, or 1d");
        };
    }

    private boolean hasInvalidOhlc(MarketCandle candle) {
        BigDecimal maxBody = candle.getOpenPrice().max(candle.getClose());
        BigDecimal minBody = candle.getOpenPrice().min(candle.getClose());
        return candle.getHigh().compareTo(maxBody) < 0 || candle.getLow().compareTo(minBody) > 0;
    }

    private String importKey(ParsedMarketCandle candle) {
        return candle.symbol() + "|" + candle.timeframe() + "|" + candle.openTime();
    }
}
