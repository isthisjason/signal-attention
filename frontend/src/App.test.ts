import { describe, expect, it } from "vitest";
import { selectPaperSessionId, toInstant } from "./App";
import { PaperSession } from "./api/paperTrading";

function session(id: number): PaperSession {
  return {
    id,
    strategyId: 1,
    status: "CREATED",
    initialBalance: 100000,
    cashBalance: 100000,
    createdAt: "2024-01-01T00:00:00Z",
    startedAt: null,
    stoppedAt: null,
  };
}

describe("paper session selection", () => {
  it("keeps the current session when it is still available", () => {
    expect(selectPaperSessionId(2, [session(1), session(2)])).toBe(2);
  });

  it("prefers a newly created session when it is available", () => {
    expect(selectPaperSessionId(1, [session(1), session(3)], 3)).toBe(3);
  });

  it("falls back when the current session belongs to another strategy", () => {
    expect(selectPaperSessionId(9, [session(4), session(5)])).toBe(4);
  });

  it("clears the selection when no sessions exist", () => {
    expect(selectPaperSessionId(9, [])).toBeNull();
  });
});

describe("date input conversion", () => {
  it("converts local datetime input to an instant", () => {
    const instant = toInstant("2024-01-01T00:00");

    expect(instant).toMatch(/2024-01-01T\d{2}:00:00.000Z/);
    expect(Number.isNaN(Date.parse(instant))).toBe(false);
  });

  it("raises a user-facing validation message for invalid dates", () => {
    expect(() => toInstant("", "Backtest start")).toThrow(
      "Backtest start must be a valid date and time.",
    );
  });
});
