import { ReactNode } from "react";
import { ResponsiveContainer } from "recharts";

type ChartShellProps = {
  title: string;
  value?: string;
  children: ReactNode;
  height?: number;
};

export function ChartShell({ title, value, children, height = 180 }: ChartShellProps) {
  return (
    <div className="series-card chart-shell">
      <div className="series-heading">
        <h3>{title}</h3>
        {value ? <strong>{value}</strong> : null}
      </div>
      <div className="chart-frame" style={{ minHeight: height }}>
        <ResponsiveContainer width="100%" height={height}>
          {children}
        </ResponsiveContainer>
      </div>
    </div>
  );
}

export function ChartEmptyState({ title, message }: { title: string; message: string }) {
  return <ChartState title={title} message={message} />;
}

export function ChartState({ title, message }: { title: string; message: string }) {
  return (
    <div className="chart-state">
      <strong>{title}</strong>
      <p>{message}</p>
    </div>
  );
}
