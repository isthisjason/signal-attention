import { uploadForm } from "./client";

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

export function importMarketData(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return uploadForm<MarketDataImportSummary>("/api/market-data/import", formData);
}
