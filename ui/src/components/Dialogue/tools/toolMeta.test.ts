import { describe, expect, it } from "vitest";
import {
  normalizeToolName,
  toolChip,
  toolLabel,
  toolSummary,
} from "./toolMeta";

describe("toolMeta", () => {
  it("normalizes aliases", () => {
    expect(normalizeToolName("shell")).toBe("bash");
    expect(normalizeToolName("web_search")).toBe("search");
    expect(normalizeToolName("Read")).toBe("read");
  });

  it("summarizes read path with range", () => {
    expect(
      toolSummary(
        "read",
        JSON.stringify({ path: "src/a.ts", offset: 10, limit: 20 })
      )
    ).toBe("src/a.ts:10-30");
  });

  it("hides empty object in collapsed header", () => {
    expect(toolSummary("bash", "{}")).toBe("");
    expect(toolSummary("bash", "{}", true)).toBe("{}");
  });

  it("summarizes bash command", () => {
    expect(
      toolSummary("bash", JSON.stringify({ command: "ls -la" }))
    ).toBe("ls -la");
  });

  it("labels and chips", () => {
    expect(toolLabel("web_search")).toBe("Search");
    expect(
      toolChip({
        name: "read",
        arg: "{}",
        output: ["a", "b", "c"],
      })
    ).toBe("3 lines");
  });
});
