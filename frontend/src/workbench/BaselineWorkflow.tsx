import { FormEvent } from "react";
import { Area, AreaChart, CartesianGrid, Tooltip, XAxis, YAxis } from "recharts";
import { ChartShell, ChartState } from "../ChartShell";
import {
  BacktestDrawdownPoint,
  BacktestEquityPoint,
  BacktestRun,
  BacktestTrade,
  MlRiskScore,
} from "../api/backtests";
import { MarketDataImportSummary, MarketDataQuality } from "../api/marketData";
import {
  PaperOrder,
  PaperPosition,
  PaperReplayResult,
  PaperSession,
  PaperSessionSummary,
} from "../api/paperTrading";
import { Strategy } from "../api/strategies";
import { formatCurrency, formatDateTime, formatPercent } from "./format";
import { DateInput, ResultGrid, serviceErrorMessage, TextInput } from "./presentation";
import { LoadState } from "./types";

export type StrategyFormState = {
  name: string;
  symbol: string;
  timeframe: string;
  shortWindow: string;
  longWindow: string;
  initialBalance: string;
  feePercent: string;
  positionSizePercent: string;
};

export type BacktestFormState = { startDate: string; endDate: string };

export type PaperFormState = {
  initialBalance: string;
  startDate: string;
  endDate: string;
  maxCandles: string;
  orderSide: string;
  orderSymbol: string;
  orderQuantity: string;
  orderPrice: string;
};

export function MarketDataImportPanel({
  busy,
  importSummary,
  qualityState,
  onSubmit,
}: {
  busy: boolean;
  importSummary: MarketDataImportSummary | null;
  qualityState: LoadState<MarketDataQuality>;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <section className="panel" id="data">
      <SectionTitle title="Market data" status={qualityState.status === "success" && qualityState.data.candleCount > 0 ? "ready" : "needs data"} />
      <form className="control-stack" onSubmit={onSubmit}>
        <label>Candle CSV<input accept=".csv,text/csv" name="csv" type="file" /></label>
        <button className="button" disabled={busy} type="submit">{busy ? "Importing" : "Import CSV"}</button>
      </form>
      {importSummary ? (
        <ResultGrid items={[["Rows read", importSummary.totalRows], ["Imported", importSummary.rowsImported], ["Rejected", importSummary.rowsRejected]]} />
      ) : <p className="muted">No import has run in this browser session.</p>}
      {importSummary?.errors.length ? (
        <ul className="compact-list">{importSummary.errors.slice(0, 4).map((error) => <li key={`${error.rowNumber}-${error.message}`}>Row {error.rowNumber}: {error.message}</li>)}</ul>
      ) : null}
      <MarketDataQualityPanel state={qualityState} />
    </section>
  );
}

function MarketDataQualityPanel({ state }: { state: LoadState<MarketDataQuality> }) {
  if (state.status === "loading") return <p className="muted">Checking BTC-USD 1h data quality.</p>;
  if (state.status === "error") return <p className="muted">{serviceErrorMessage(state.error, "backend")}</p>;
  const quality = state.data;
  return (
    <div className="quality-summary" aria-label="Market data quality">
      <div className="series-heading"><h3>Data quality</h3><strong>{quality.symbol} {quality.timeframe}</strong></div>
      <ResultGrid items={[
        ["Candles", quality.candleCount], ["Gaps", quality.gapCount], ["Bad OHLC", quality.invalidOhlcCount],
        ["Bad volume", quality.zeroOrNegativeVolumeCount], ["Interval", `${quality.expectedIntervalMinutes}m`],
        ["Duplicates", quality.duplicateTimestampCount],
      ]} />
      <p className="muted quality-range">
        {quality.firstOpenTime && quality.lastOpenTime
          ? `${formatDateTime(quality.firstOpenTime)} to ${formatDateTime(quality.lastOpenTime)}`
          : "No candles found for the default market."}
      </p>
      <ul className="compact-list">{quality.warnings.slice(0, 4).map((warning) => <li key={warning}>{warning}</li>)}</ul>
    </div>
  );
}

export function StrategyWorkflowPanel({
  busy, form, selectedStrategyId, state, onSelect, onSubmit, onUpdate,
}: {
  busy: boolean;
  form: StrategyFormState;
  selectedStrategyId: number | null;
  state: LoadState<Strategy[]>;
  onSelect: (id: number | null) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUpdate: (form: StrategyFormState) => void;
}) {
  return (
    <section className="panel" id="strategy">
      <SectionTitle title="Baseline strategy" status={selectedStrategyId ? "ready" : "needs setup"} />
      <label>Active baseline
        <select disabled={state.status !== "success"} value={selectedStrategyId ?? ""} onChange={(event) => onSelect(event.target.value ? Number(event.target.value) : null)}>
          <option value="">None selected</option>
          {state.status === "success" ? state.data.map((strategy) => <option key={strategy.id} value={strategy.id}>#{strategy.id} {strategy.name}</option>) : null}
        </select>
      </label>
      {state.status === "loading" ? <p className="muted">Loading saved strategies.</p> : null}
      {state.status === "error" ? <p className="muted">{serviceErrorMessage(state.error, "backend")}</p> : null}
      {state.status === "success" && state.data.length === 0 ? <p className="muted">No saved baselines yet. Create the sample SMA baseline to unlock comparisons and paper sessions.</p> : null}
      <form className="control-stack" onSubmit={onSubmit}>
        <div className="form-grid">
          <TextInput label="Name" name="name" state={form} setState={onUpdate} />
          <TextInput label="Symbol" name="symbol" state={form} setState={onUpdate} />
          <TextInput label="Timeframe" name="timeframe" state={form} setState={onUpdate} />
        </div>
        <details className="advanced-controls"><summary>Baseline tuning</summary><div className="form-grid">
          <TextInput label="Short SMA" name="shortWindow" state={form} setState={onUpdate} type="number" />
          <TextInput label="Long SMA" name="longWindow" state={form} setState={onUpdate} type="number" />
          <TextInput label="Initial balance" name="initialBalance" state={form} setState={onUpdate} type="number" />
          <TextInput label="Fee %" name="feePercent" state={form} setState={onUpdate} type="number" />
          <TextInput label="Position size %" name="positionSizePercent" state={form} setState={onUpdate} type="number" />
        </div></details>
        <button className="button" disabled={busy} type="submit">{busy ? "Creating" : "Create SMA baseline"}</button>
      </form>
    </section>
  );
}

export function BacktestWorkflowPanel({
  backtestRun, busy, form, riskScore, selectedStrategy, drawdownSeries, equitySeries, trades,
  onRiskScore, onSubmit, onUpdate,
}: {
  backtestRun: BacktestRun | null;
  busy: boolean;
  form: BacktestFormState;
  riskScore: MlRiskScore | null;
  selectedStrategy: Strategy | null;
  drawdownSeries: BacktestDrawdownPoint[];
  equitySeries: BacktestEquityPoint[];
  trades: BacktestTrade[];
  onRiskScore: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUpdate: (form: BacktestFormState) => void;
}) {
  return (
    <section className="panel" id="backtest">
      <SectionTitle title="Baseline comparison" status={backtestRun ? "complete" : selectedStrategy ? "ready" : "needs baseline"} />
      <p>{selectedStrategy ? `${selectedStrategy.symbol} ${selectedStrategy.timeframe}` : "Create or select a baseline first. Comparisons use the selected baseline rules."}</p>
      <form className="control-stack" onSubmit={onSubmit}>
        <div className="form-grid">
          <DateInput label="Start" name="startDate" state={form} setState={onUpdate} />
          <DateInput label="End" name="endDate" state={form} setState={onUpdate} />
        </div>
        <div className="button-row">
          <button className="button" disabled={busy || !selectedStrategy} type="submit">{busy ? "Working" : "Run comparison"}</button>
          <button className="button button-secondary" disabled={busy || !backtestRun} onClick={onRiskScore} type="button">Score baseline risk</button>
        </div>
      </form>
      {backtestRun ? <>
        <ResultGrid items={[["Run", `#${backtestRun.id}`], ["Return", formatPercent(backtestRun.totalReturn)], ["Drawdown", formatPercent(backtestRun.maxDrawdown)], ["Trades", backtestRun.tradeCount], ["Risk", backtestRun.mlRiskLabel || riskScore?.riskLabel || "Unscored"]]} />
        <MetricChart title="Equity" tone="positive" points={equitySeries.map((point) => ({ timestamp: point.timestamp, value: point.equity }))} formatValue={formatCurrency} />
        <MetricChart title="Drawdown" tone="negative" points={drawdownSeries.map((point) => ({ timestamp: point.timestamp, value: point.drawdownPercent }))} formatValue={formatPercent} />
        <TradePreview trades={trades} />
      </> : <p className="muted">No backtest has run in this browser session. Import candles and choose a strategy first.</p>}
      {riskScore ? <ul className="compact-list">{riskScore.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul> : null}
    </section>
  );
}

export function PaperTradingPanel({
  busy, form, orders, positions, replay, selectedSessionId, sessions, summary,
  onCreate, onOrder, onReplay, onSelect, onStart, onStop, onUpdate,
}: {
  busy: boolean;
  form: PaperFormState;
  orders: PaperOrder[];
  positions: PaperPosition[];
  replay: PaperReplayResult | null;
  selectedSessionId: number | null;
  sessions: PaperSession[];
  summary: PaperSessionSummary | null;
  onCreate: (event: FormEvent<HTMLFormElement>) => void;
  onOrder: () => void;
  onReplay: () => void;
  onSelect: (id: number | null) => void;
  onStart: () => void;
  onStop: () => void;
  onUpdate: (form: PaperFormState) => void;
}) {
  return (
    <section className="panel" id="paper">
      <SectionTitle title="Paper trading" status={summary?.status.toLowerCase() || (selectedSessionId ? "ready" : "optional")} />
      <label>Paper session<select value={selectedSessionId ?? ""} onChange={(event) => onSelect(event.target.value ? Number(event.target.value) : null)}>
        <option value="">None selected</option>{sessions.map((session) => <option key={session.id} value={session.id}>#{session.id} {session.status}</option>)}
      </select></label>
      {sessions.length === 0 ? <p className="muted">No paper sessions for the selected strategy. Create one after selecting a strategy.</p> : null}
      <form className="control-stack" onSubmit={onCreate}>
        <TextInput label="Initial balance" name="initialBalance" state={form} setState={onUpdate} type="number" />
        <button className="button" disabled={busy} type="submit">{busy ? "Working" : "Create session"}</button>
      </form>
      <div className="button-row">
        <button className="button button-secondary" disabled={busy || !selectedSessionId} onClick={onStart} type="button">Start</button>
        <button className="button button-secondary" disabled={busy || !selectedSessionId} onClick={onStop} type="button">Stop</button>
      </div>
      <details className="advanced-controls"><summary>Manual order</summary><div className="form-grid">
        <label>Side<select value={form.orderSide} onChange={(event) => onUpdate({ ...form, orderSide: event.target.value })}><option value="BUY">Buy</option><option value="SELL">Sell</option></select></label>
        <TextInput label="Symbol" name="orderSymbol" state={form} setState={onUpdate} />
        <TextInput label="Quantity" name="orderQuantity" state={form} setState={onUpdate} type="number" />
        <TextInput label="Price" name="orderPrice" state={form} setState={onUpdate} type="number" />
      </div><button className="button button-secondary" disabled={busy || !selectedSessionId} onClick={onOrder} type="button">{busy ? "Working" : "Submit order"}</button></details>
      <details className="advanced-controls"><summary>Replay settings</summary><div className="form-grid">
        <DateInput label="Replay start" name="startDate" state={form} setState={onUpdate} />
        <DateInput label="Replay end" name="endDate" state={form} setState={onUpdate} />
        <TextInput label="Max candles" name="maxCandles" state={form} setState={onUpdate} type="number" />
      </div></details>
      <button className="button" disabled={busy || !selectedSessionId} onClick={onReplay} type="button">{busy ? "Working" : "Replay candles"}</button>
      {summary ? <ResultGrid items={[["Status", summary.status], ["Cash", formatCurrency(summary.cashBalance)], ["Equity", formatCurrency(summary.totalEquity)], ["Open value", formatCurrency(summary.openPositionValue)], ["Unrealized", formatCurrency(summary.unrealizedPnl)]]} /> : null}
      {replay ? <ResultGrid items={[["Candles", replay.candlesRead], ["Signals", replay.signalsProcessed], ["Filled", replay.filledOrders], ["Rejected", replay.rejectedOrders]]} /> : null}
      {orders.length ? <div className="mini-table">{orders.slice(0, 4).map((order) => <div key={order.id}><span>{order.side}</span><strong>{order.status}</strong><small>{formatCurrency(order.notional)}</small></div>)}</div> : <p className="muted">No paper orders for the selected session.</p>}
      {positions.length ? <ul className="compact-list">{positions.slice(0, 4).map((position) => <li key={position.id}>{position.status} {position.quantity} {position.symbol} at {formatCurrency(position.entryPrice)}</li>)}</ul> : <p className="muted">No paper positions for the selected session.</p>}
    </section>
  );
}

function SectionTitle({ title, status }: { title: string; status: string }) {
  return <div className="section-title"><h2>{title}</h2><span>{status}</span></div>;
}

function MetricChart({
  title, points, formatValue, tone,
}: {
  title: string;
  points: Array<{ timestamp: string; value: number }>;
  formatValue: (value: number) => string;
  tone: "positive" | "negative";
}) {
  if (!points.length) return <ChartState title={`${title} chart unavailable`} message="Run a backtest with enough imported candles to populate this series." />;
  const values = points.map((point) => point.value);
  const first = points[0];
  const last = points.at(-1)!;
  const color = tone === "positive" ? "#0f766e" : "#b42318";
  const gradientId = `${tone}-metric-fill`;
  const data = points.map((point) => ({ ...point, label: formatDateTime(point.timestamp) }));
  return (
    <ChartShell title={title} value={formatValue(last.value)} footer={<><div className="series-meta"><span>{formatDateTime(first.timestamp)}</span><span>{formatDateTime(last.timestamp)}</span></div><div className="series-summary" aria-label={`${title} chart summary`}><span>Low {formatValue(Math.min(...values))}</span><span>High {formatValue(Math.max(...values))}</span><span>Latest {formatValue(last.value)}</span></div></>}>
      <AreaChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: 0 }}>
        <defs><linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1"><stop offset="5%" stopColor={color} stopOpacity={0.28} /><stop offset="95%" stopColor={color} stopOpacity={0.02} /></linearGradient></defs>
        <CartesianGrid stroke="var(--border)" strokeDasharray="4 5" vertical={false} /><XAxis dataKey="label" hide /><YAxis hide domain={["dataMin", "dataMax"]} />
        <Tooltip formatter={(value) => formatValue(Number(value))} labelFormatter={(label) => String(label)} />
        <Area dataKey="value" fill={`url(#${gradientId})`} isAnimationActive={false} stroke={color} strokeWidth={3} type="monotone" />
      </AreaChart>
    </ChartShell>
  );
}

function TradePreview({ trades }: { trades: BacktestTrade[] }) {
  if (!trades.length) return <p>No trades were generated for this backtest.</p>;
  return <div className="trade-detail"><h3>Trades</h3><div className="table-scroll"><table><thead><tr><th>Side</th><th>Entry</th><th>Exit</th><th>Fees</th><th>Net P&amp;L</th><th>Return</th></tr></thead><tbody>
    {trades.slice(0, 8).map((trade) => <tr key={trade.id}><td>{trade.side}</td><td>{formatCurrency(trade.entryPrice)}<small>{formatDateTime(trade.entryTime)}</small></td><td>{trade.exitPrice === null ? "Open" : formatCurrency(trade.exitPrice)}{trade.exitTime ? <small>{formatDateTime(trade.exitTime)}</small> : null}</td><td>{formatCurrency(trade.fees)}</td><td>{formatCurrency(trade.netPnl)}</td><td>{formatPercent(trade.returnPercent)}</td></tr>)}
  </tbody></table></div>{trades.length > 8 ? <p className="muted">Showing 8 of {trades.length} trades.</p> : null}</div>;
}
