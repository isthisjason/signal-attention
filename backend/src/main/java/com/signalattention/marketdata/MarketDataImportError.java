package com.signalattention.marketdata;

public record MarketDataImportError(
        int rowNumber,
        String message
) {
}
