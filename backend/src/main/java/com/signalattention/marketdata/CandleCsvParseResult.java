package com.signalattention.marketdata;

import java.util.List;

public record CandleCsvParseResult(
        int totalRows,
        List<ParsedMarketCandle> candles,
        List<MarketDataImportError> errors
) {
}
