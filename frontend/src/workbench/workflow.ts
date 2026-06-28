import { PaperSession } from "../api/paperTrading";
import { WorkbenchAction } from "./types";

export function toInstant(value: string, label = "Date") {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) {
    throw new Error(`${label} must be a valid date and time.`);
  }
  return date.toISOString();
}

export function selectPaperSessionId(
  current: number | null,
  sessions: PaperSession[],
  preferred?: number,
) {
  if (preferred !== undefined && sessions.some((session) => session.id === preferred)) {
    return preferred;
  }
  if (current !== null && sessions.some((session) => session.id === current)) {
    return current;
  }
  return sessions[0]?.id ?? null;
}

export function deriveWorkbenchAction({
  candleCount,
  strategyCount,
  hasBacktest,
  hasRiskScore,
  hasRegimeReplay,
}: {
  candleCount: number;
  strategyCount: number;
  hasBacktest: boolean;
  hasRiskScore: boolean;
  hasRegimeReplay: boolean;
}): WorkbenchAction {
  if (candleCount === 0) {
    return {
      id: "import-data",
      title: "Import market data",
      detail: "Load the BTC-USD sample candles so baselines, comparisons, and attention analysis have data.",
    };
  }
  if (strategyCount === 0) {
    return {
      id: "create-strategy",
      title: "Create the SMA baseline",
      detail: "Use the default BTC-USD 1h SMA crossover setup as the traditional comparison baseline.",
    };
  }
  if (!hasBacktest) {
    return {
      id: "run-backtest",
      title: "Run baseline comparison",
      detail: "Generate return, drawdown, trade, equity, and risk inputs for the selected baseline.",
    };
  }
  if (!hasRiskScore) {
    return {
      id: "score-risk",
      title: "Score baseline risk",
      detail: "Persist an explainable risk label so the baseline comparison and alerts can reflect it.",
    };
  }
  if (!hasRegimeReplay) {
    return {
      id: "run-analysis",
      title: "Run attention replay",
      detail: "Show candle context with attention-derived regime windows and baseline trade markers.",
    };
  }
  return {
    id: "review-results",
    title: "Review results",
    detail: "The main demo path is ready: review attention evidence, model lab diagnostics, robustness, and baseline comparisons.",
  };
}
