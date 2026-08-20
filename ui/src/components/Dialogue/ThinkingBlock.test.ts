import { describe, expect, it } from "vitest";

function splitParagraphs(text: string): string[] {
  return text
    .split(/\n{2,}/)
    .map((p) => p.trim())
    .filter((p) => p.length > 0);
}

describe("thinking paragraph fold", () => {
  it("uses last paragraph as teaser", () => {
    const parts = splitParagraphs("first para\n\nsecond para\n\nfinal teaser");
    expect(parts[parts.length - 1]).toBe("final teaser");
    expect(parts.length).toBe(3);
  });

  it("single paragraph is not foldable", () => {
    expect(splitParagraphs("only one").length).toBe(1);
  });
});
