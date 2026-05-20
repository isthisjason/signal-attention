import { describe, expect, it } from "vitest";

describe("frontend test harness", () => {
  it("runs vitest in jsdom", () => {
    const element = document.createElement("div");
    element.textContent = "SignalAttention";

    expect(element).toHaveTextContent("SignalAttention");
  });
});
