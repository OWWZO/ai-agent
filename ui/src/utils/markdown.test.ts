import { describe, expect, it } from "vitest";

import { normalizeThinkingText } from "./markdown";

describe("normalizeThinkingText", () => {
  it("removes malformed thought separators without changing normal emphasis", () => {
    expect(
      normalizeThinkingText(
        "Planning data cleaning and reporting steps****Deciding output formats and tools"
      )
    ).toBe("Planning data cleaning and reporting steps\n\nDeciding output formats and tools");
    expect(normalizeThinkingText("**重点** 和 *补充*")).toBe("**重点** 和 *补充*");
  });
});
