import { ChartState } from "../ChartShell";

export function TextInput<T extends Record<string, string>>({
  label,
  name,
  state,
  setState,
  type = "text",
}: {
  label: string;
  name: keyof T & string;
  state: T;
  setState: (state: T) => void;
  type?: string;
}) {
  return (
    <label>
      {label}
      <input
        min={type === "number" ? "0" : undefined}
        step={type === "number" ? "any" : undefined}
        type={type}
        value={state[name]}
        onChange={(event) => setState({ ...state, [name]: event.target.value })}
      />
    </label>
  );
}

export function DateInput<T extends Record<string, string>>(props: {
  label: string;
  name: keyof T & string;
  state: T;
  setState: (state: T) => void;
}) {
  return <TextInput {...props} type="datetime-local" />;
}

export function ResultGrid({ items }: { items: Array<[string, string | number]> }) {
  return (
    <dl className="result-grid">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function PanelMessage({
  title,
  message,
  tone = "neutral",
}: {
  title: string;
  message: string;
  tone?: "neutral" | "error";
}) {
  return (
    <section className={`panel panel-message panel-message-${tone}`}>
      <h2>{title}</h2>
      <p>{message}</p>
    </section>
  );
}

export function serviceErrorMessage(message: string, service: "backend" | "ml") {
  const serviceName = service === "backend" ? "backend API" : "ML service";
  return `${message} Check that the ${serviceName} is running, then refresh this dashboard.`;
}

export function EmptyChart({ title }: { title: string }) {
  return (
    <ChartState
      title={`${title} chart unavailable`}
      message="Run a backtest with enough imported candles to populate this series."
    />
  );
}
