package com.signalattention.marketdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CandleCsvParser {

    private static final String EXPECTED_HEADER = "symbol,timeframe,openTime,open,high,low,close,volume";
    private static final int COLUMN_COUNT = 8;

    public CandleCsvParseResult parse(InputStream inputStream) throws IOException {
        List<ParsedMarketCandle> candles = new ArrayList<>();
        List<MarketDataImportError> errors = new ArrayList<>();
        int totalRows = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || header.isBlank()) {
                errors.add(new MarketDataImportError(1, "CSV header is missing"));
                return new CandleCsvParseResult(totalRows, candles, errors);
            }
            if (!EXPECTED_HEADER.equals(header.trim())) {
                errors.add(new MarketDataImportError(1, "CSV header must be: " + EXPECTED_HEADER));
                return new CandleCsvParseResult(totalRows, candles, errors);
            }

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                totalRows++;
                parseRow(rowNumber, line, candles, errors);
            }
        }

        return new CandleCsvParseResult(totalRows, candles, errors);
    }

    private void parseRow(
            int rowNumber,
            String line,
            List<ParsedMarketCandle> candles,
            List<MarketDataImportError> errors
    ) {
        String[] columns = line.split(",", -1);
        if (columns.length != COLUMN_COUNT) {
            errors.add(new MarketDataImportError(rowNumber, "Expected 8 columns but found " + columns.length));
            return;
        }

        String symbol = columns[0].trim();
        String timeframe = columns[1].trim();
        String openTimeValue = columns[2].trim();
        if (symbol.isBlank() || timeframe.isBlank() || openTimeValue.isBlank()) {
            errors.add(new MarketDataImportError(rowNumber, "symbol, timeframe, and openTime are required"));
            return;
        }

        Instant openTime;
        try {
            openTime = Instant.parse(openTimeValue);
        } catch (DateTimeParseException exception) {
            errors.add(new MarketDataImportError(rowNumber, "openTime must be an ISO-8601 instant"));
            return;
        }

        BigDecimal open = parsePositiveDecimal(rowNumber, "open", columns[3], errors);
        BigDecimal high = parsePositiveDecimal(rowNumber, "high", columns[4], errors);
        BigDecimal low = parsePositiveDecimal(rowNumber, "low", columns[5], errors);
        BigDecimal close = parsePositiveDecimal(rowNumber, "close", columns[6], errors);
        BigDecimal volume = parsePositiveDecimal(rowNumber, "volume", columns[7], errors);
        if (open == null || high == null || low == null || close == null || volume == null) {
            return;
        }

        candles.add(new ParsedMarketCandle(rowNumber, symbol, timeframe, openTime, open, high, low, close, volume));
    }

    private BigDecimal parsePositiveDecimal(
            int rowNumber,
            String field,
            String rawValue,
            List<MarketDataImportError> errors
    ) {
        String value = rawValue.trim();
        if (value.isBlank()) {
            errors.add(new MarketDataImportError(rowNumber, field + " is required"));
            return null;
        }

        try {
            BigDecimal decimal = new BigDecimal(value);
            if (decimal.signum() <= 0) {
                errors.add(new MarketDataImportError(rowNumber, field + " must be greater than zero"));
                return null;
            }
            return decimal;
        } catch (NumberFormatException exception) {
            errors.add(new MarketDataImportError(rowNumber, field + " must be a valid decimal"));
            return null;
        }
    }
}
