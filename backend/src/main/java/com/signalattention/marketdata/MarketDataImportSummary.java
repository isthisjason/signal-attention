package com.signalattention.marketdata;

import java.util.List;

public record MarketDataImportSummary(
        int totalRows,
        int rowsImported,
        int rowsRejected,
        List<MarketDataImportError> errors
) {
}
