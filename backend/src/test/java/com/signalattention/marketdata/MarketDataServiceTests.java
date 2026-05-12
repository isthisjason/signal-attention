package com.signalattention.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalattention.audit.AuditService;
import com.signalattention.common.BadRequestException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MarketDataServiceTests {

    @Mock
    private MarketCandleRepository marketCandleRepository;

    @Mock
    private AuditService auditService;

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        marketDataService = new MarketDataService(new CandleCsvParser(), marketCandleRepository, auditService);
    }

    @Test
    void importsValidCandlesAndRecordsAuditEvent() {
        MarketDataImportSummary summary = marketDataService.importCsv(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,2024-01-01T00:00:00Z,42000,42100,41900,42050,12.5
                """));

        assertThat(summary.totalRows()).isEqualTo(1);
        assertThat(summary.rowsImported()).isEqualTo(1);
        assertThat(summary.rowsRejected()).isZero();
        verify(marketCandleRepository).save(any(MarketCandle.class));
        verify(auditService).record(eq("MARKET_DATA"), isNull(), eq("CSV_IMPORT_COMPLETED"), eq("CSV import completed"), any());
    }

    @Test
    void importsValidRowsAndReportsInvalidRows() {
        MarketDataImportSummary summary = marketDataService.importCsv(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,2024-01-01T00:00:00Z,42000,42100,41900,42050,12.5
                BTC-USD,1h,bad-time,42000,42100,41900,42050,12.5
                """));

        assertThat(summary.totalRows()).isEqualTo(2);
        assertThat(summary.rowsImported()).isEqualTo(1);
        assertThat(summary.rowsRejected()).isEqualTo(1);
        assertThat(summary.errors()).extracting(MarketDataImportError::message)
                .contains("openTime must be an ISO-8601 instant");
    }

    @Test
    void rejectsDuplicateRowsInSameCsv() {
        MarketDataImportSummary summary = marketDataService.importCsv(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,2024-01-01T00:00:00Z,42000,42100,41900,42050,12.5
                BTC-USD,1h,2024-01-01T00:00:00Z,42000,42100,41900,42050,12.5
                """));

        assertThat(summary.rowsImported()).isEqualTo(1);
        assertThat(summary.rowsRejected()).isEqualTo(1);
        assertThat(summary.errors()).extracting(MarketDataImportError::message)
                .contains("Duplicate candle in CSV");
    }

    @Test
    void rejectsDuplicateRowsAlreadyPersisted() {
        when(marketCandleRepository.existsBySymbolAndTimeframeAndOpenTime(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-01T00:00:00Z")
        )).thenReturn(true);

        MarketDataImportSummary summary = marketDataService.importCsv(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,2024-01-01T00:00:00Z,42000,42100,41900,42050,12.5
                """));

        assertThat(summary.rowsImported()).isZero();
        assertThat(summary.rowsRejected()).isEqualTo(1);
        assertThat(summary.errors()).extracting(MarketDataImportError::message)
                .contains("Duplicate candle already exists");
    }

    @Test
    void queriesCandlesWithNoDateBoundsSortedByRepository() {
        when(marketCandleRepository.findBySymbolAndTimeframeOrderByOpenTimeAsc("BTC-USD", "1h"))
                .thenReturn(List.of(candle("2024-01-01T00:00:00Z")));

        List<CandleResponse> candles = marketDataService.findCandles("BTC-USD", "1h", null, null);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().openTime()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void rejectsMissingSymbol() {
        assertThatThrownBy(() -> marketDataService.findCandles("", "1h", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("symbol is required");
    }

    @Test
    void rejectsStartAfterEnd() {
        assertThatThrownBy(() -> marketDataService.findCandles(
                "BTC-USD",
                "1h",
                Instant.parse("2024-01-02T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:00Z")
        )).isInstanceOf(BadRequestException.class)
                .hasMessage("start must be before or equal to end");
    }

    @Test
    void savesParsedCandleValues() {
        ArgumentCaptor<MarketCandle> captor = ArgumentCaptor.forClass(MarketCandle.class);

        marketDataService.importCsv(csv("""
                symbol,timeframe,openTime,open,high,low,close,volume
                BTC-USD,1h,2024-01-01T00:00:00Z,42000,42100,41900,42050,12.5
                """));

        verify(marketCandleRepository).save(captor.capture());
        assertThat(captor.getValue().getClose()).isEqualByComparingTo(new BigDecimal("42050"));
    }

    private MarketCandle candle(String openTime) {
        return new MarketCandle(
                "BTC-USD",
                "1h",
                Instant.parse(openTime),
                new BigDecimal("42000"),
                new BigDecimal("42100"),
                new BigDecimal("41900"),
                new BigDecimal("42050"),
                new BigDecimal("12.5")
        );
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
