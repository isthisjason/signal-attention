export type LoadState<T> =
  | { status: "loading"; data: null; error: null }
  | { status: "success"; data: T; error: null }
  | { status: "error"; data: null; error: string };

export type WorkbenchActionId =
  | "import-data"
  | "create-strategy"
  | "run-backtest"
  | "score-risk"
  | "run-analysis"
  | "review-results";

export type WorkbenchAction = {
  id: WorkbenchActionId;
  title: string;
  detail: string;
};
