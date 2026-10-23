import { describe, expect, it } from "vitest";
import { clampOutputLines } from "./toolOutputLimit";

describe("clampOutputLines", () => {
  it("未超限时原样返回", () => {
    const text = "a\nb\nc";
    expect(clampOutputLines(text, 10)).toEqual({
      text,
      hiddenCount: 0,
    });
  });

  it("超限时保留头部与尾部，并报出隐藏行数", () => {
    const lines = Array.from({ length: 250 }, (_, i) => `line-${i}`);
    const result = clampOutputLines(lines.join("\n"));
    expect(result.hiddenCount).toBe(50);
    const kept = result.text.split("\n");
    expect(kept).toHaveLength(200);
    expect(kept[0]).toBe("line-0");
    expect(kept.at(-1)).toBe("line-249");
  });

  it("limit 小于等于 0 时不截断", () => {
    const text = Array.from({ length: 50 }, (_, i) => `${i}`).join("\n");
    expect(clampOutputLines(text, 0).hiddenCount).toBe(0);
  });
});
