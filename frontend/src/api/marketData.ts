import { getJson, uploadForm } from "./client";

export type MarketDataImportError = {
  rowNumber: number;
  message: string;
};

export type MarketDataImportSummary = {
  totalRows: number;
  rowsImported: number;
  rowsRejected: number;
  errors: MarketDataImportError[];
};

export type MarketDataQuality = {
  symbol: string;
  timeframe: string;
  candleCount: number;
  firstOpenTime: string | null;
  lastOpenTime: string | null;
  expectedIntervalMinutes: number;
  duplicateTimestampCount: number;
  gapCount: number;
  invalidOhlcCount: number;
  zeroOrNegativeVolumeCount: number;
  warnings: string[];
};

export function importMarketData(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return uploadForm<MarketDataImportSummary>("/api/market-data/import", formData);
}

export function fetchMarketDataQuality(symbol = "BTC-USD", timeframe = "1h") {
  const params = new URLSearchParams({ symbol, timeframe });
  return getJson<MarketDataQuality>(`/api/market-data/quality?${params.toString()}`);
}
