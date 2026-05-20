import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchAuditEvents } from "./audit";
import {
  fetchBacktest,
  fetchBacktestTrades,
  runBacktest,
  scoreBacktestRisk,
} from "./backtests";
import { errorMessage, getJson } from "./client";
import { fetchDashboardSummary, fetchStrategyPerformance } from "./dashboard";
import { importMarketData } from "./marketData";
import { fetchMarketRegime } from "./marketRegime";
import {
  createPaperSession,
  fetchPaperOrders,
  fetchPaperPositions,
  fetchPaperSessionSummary,
  fetchStrategyPaperSessions,
  replayPaperSession,
  startPaperSession,
  stopPaperSession,
  submitPaperOrder,
} from "./paperTrading";
import { createStrategy, fetchStrategies } from "./strategies";

const fetchMock = vi.fn<typeof fetch>();

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init,
  });
}

function latestFetchCall() {
  const call = fetchMock.mock.calls.at(-1);
  if (!call) {
    throw new Error("Expected fetch to be called.");
  }
  const [url, init] = call;
  return { url: String(url), init: init as RequestInit | undefined };
}

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockImplementation(() => Promise.resolve(jsonResponse({ ok: true })));
  vi.stubGlobal("fetch", fetchMock);
  vi.stubEnv("VITE_API_BASE_URL", "http://api.test");
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe("API client helpers", () => {
  it("uses the configured API base URL", async () => {
    await getJson("/api/dashboard/summary");

    expect(latestFetchCall().url).toBe("http://api.test/api/dashboard/summary");
  });

  it("falls back to the local backend URL", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "");

    await getJson("/api/dashboard/summary");

    expect(latestFetchCall().url).toBe("http://localhost:8080/api/dashboard/summary");
  });

  it("surfaces API error messages", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ message: "Invalid request" }, { status: 400 }));

    await expect(getJson("/api/fails")).rejects.toEqual({
      message: "Invalid request",
      status: 400,
    });
  });

  it("normalizes thrown errors", () => {
    expect(errorMessage(new Error("Network down"))).toBe("Network down");
    expect(errorMessage({ message: "Backend rejected request" })).toBe("Backend rejected request");
    expect(errorMessage("bad")).toBe("Unexpected dashboard error.");
  });
});

describe("market data client", () => {
  it("uploads a CSV file as form data", async () => {
    const file = new File(["openTime,open"], "candles.csv", { type: "text/csv" });

    await importMarketData(file);

    const { url, init } = latestFetchCall();
    expect(url).toBe("http://api.test/api/market-data/import");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBeInstanceOf(FormData);
    expect(init?.headers).toEqual({ Accept: "application/json" });
  });
});

describe("strategy client", () => {
  it("fetches and creates strategies", async () => {
    await fetchStrategies();
    expect(latestFetchCall().url).toBe("http://api.test/api/strategies");
    expect(latestFetchCall().init?.method).toBeUndefined();

    await createStrategy({
      name: "BTC SMA",
      symbol: "BTC-USD",
      timeframe: "1h",
      strategyType: "SMA_CROSSOVER",
      rules: {
        shortWindow: 3,
        longWindow: 5,
        initialBalance: 10000,
        feePercent: 0.1,
        positionSizePercent: 50,
      },
    });
    expect(latestFetchCall().url).toBe("http://api.test/api/strategies");
    expect(latestFetchCall().init?.method).toBe("POST");
    expect(JSON.parse(String(latestFetchCall().init?.body))).toMatchObject({
      name: "BTC SMA",
      symbol: "BTC-USD",
    });
  });
});

describe("backtest client", () => {
  it("calls backtest workflow endpoints", async () => {
    await runBacktest(7, { startDate: "2024-01-01T00:00", endDate: "2024-01-10T00:00" });
    expect(latestFetchCall().url).toBe("http://api.test/api/strategies/7/backtests");
    expect(latestFetchCall().init?.method).toBe("POST");

    await fetchBacktest(11);
    expect(latestFetchCall().url).toBe("http://api.test/api/backtests/11");

    await fetchBacktestTrades(11);
    expect(latestFetchCall().url).toBe("http://api.test/api/backtests/11/trades");

    await scoreBacktestRisk(11);
    expect(latestFetchCall().url).toBe("http://api.test/api/backtests/11/ml-risk-score");
    expect(latestFetchCall().init?.method).toBe("POST");
  });
});

describe("paper trading client", () => {
  it("calls paper session and order endpoints", async () => {
    await createPaperSession(3, 100000);
    expect(latestFetchCall().url).toBe("http://api.test/api/strategies/3/paper-sessions");
    expect(JSON.parse(String(latestFetchCall().init?.body))).toEqual({ initialBalance: 100000 });

    await fetchStrategyPaperSessions(3);
    expect(latestFetchCall().url).toBe("http://api.test/api/strategies/3/paper-sessions");

    await startPaperSession(9);
    expect(latestFetchCall().url).toBe("http://api.test/api/paper-sessions/9/start");
    expect(latestFetchCall().init?.method).toBe("PATCH");

    await submitPaperOrder(9, {
      side: "BUY",
      symbol: "BTC-USD",
      quantity: 0.25,
      price: 42000,
    });
    expect(latestFetchCall().url).toBe("http://api.test/api/paper-sessions/9/orders");
    expect(JSON.parse(String(latestFetchCall().init?.body))).toMatchObject({ side: "BUY" });

    await fetchPaperOrders(9);
    expect(latestFetchCall().url).toBe("http://api.test/api/paper-sessions/9/orders");

    await fetchPaperPositions(9);
    expect(latestFetchCall().url).toBe("http://api.test/api/paper-sessions/9/positions");

    await fetchPaperSessionSummary(9);
    expect(latestFetchCall().url).toBe("http://api.test/api/paper-sessions/9/summary");

    await replayPaperSession(9, {
      startDate: "2024-01-01T00:00",
      endDate: "2024-01-10T00:00",
      maxCandles: 250,
    });
    expect(latestFetchCall().url).toBe("http://api.test/api/paper-sessions/9/replay");

    await stopPaperSession(9);
    expect(latestFetchCall().url).toBe("http://api.test/api/paper-sessions/9/stop");
  });
});

describe("dashboard and analysis clients", () => {
  it("calls dashboard, audit, and market regime endpoints", async () => {
    await fetchDashboardSummary();
    expect(latestFetchCall().url).toBe("http://api.test/api/dashboard/summary");

    await fetchStrategyPerformance();
    expect(latestFetchCall().url).toBe("http://api.test/api/dashboard/strategy-performance");

    await fetchAuditEvents();
    expect(latestFetchCall().url).toBe("http://api.test/api/audit-events?limit=12");

    await fetchMarketRegime("ETH-USD", "4h", 64);
    expect(latestFetchCall().url).toBe(
      "http://api.test/api/market-regime?symbol=ETH-USD&timeframe=4h&limit=64",
    );
  });
});
