import { describe, expect, it } from "vitest";
import {
  fallbackStreamRawPreview,
  pickJsonStringField,
  pickJsonStringFieldAny,
} from "./jsonStreamField";

describe("jsonStreamField", () => {
  it("reads complete JSON string fields", () => {
    expect(
      pickJsonStringField("content", JSON.stringify({ path: "a.ts", content: "hello" }))
    ).toBe("hello");
  });

  it("extracts incomplete content while streaming", () => {
    const raw = '{"path":"src/a.ts","content":"def hello():\\n    print(\\"hi';
    expect(pickJsonStringField("path", raw)).toBe("src/a.ts");
    expect(pickJsonStringField("content", raw)).toContain("def hello()");
    expect(pickJsonStringField("content", raw)).toContain('print("hi');
  });

  it("prefers partialArgs when present", () => {
    expect(
      pickJsonStringField("content", '{"content":"old', { content: "from-partial" })
    ).toBe("from-partial");
  });

  it("picks first available key", () => {
    const raw = '{"new_string":"abc';
    expect(pickJsonStringFieldAny(["content", "new_string"], raw)).toBe("abc");
  });

  it("returns empty when key missing", () => {
    expect(pickJsonStringField("content", '{"path":"x"')).toBe("");
  });

  it("fallback trims huge raw preview", () => {
    const raw = "x".repeat(30000);
    const preview = fallbackStreamRawPreview(raw, 100);
    expect(preview.startsWith("x".repeat(100))).toBe(true);
    expect(preview.endsWith("…")).toBe(true);
  });
});
