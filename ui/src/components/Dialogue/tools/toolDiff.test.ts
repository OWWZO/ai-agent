import { describe, expect, it } from "vitest";
import {
  buildEditDiffCode,
  extractEditPath,
  prefersToolDiffPanel,
} from "./toolDiff";

describe("toolDiff", () => {
  it("prefers edit/write for side panel", () => {
    expect(prefersToolDiffPanel("Edit")).toBe(true);
    expect(prefersToolDiffPanel("multi_edit")).toBe(true);
    expect(prefersToolDiffPanel("Write")).toBe(true);
    expect(prefersToolDiffPanel("Bash")).toBe(false);
  });

  it("extracts path from arg json", () => {
    expect(extractEditPath('{"path":"src/a.ts","old_string":"a","new_string":"b"}')).toBe(
      "src/a.ts"
    );
    expect(extractEditPath('{"file_path":"b.py"}')).toBe("b.py");
  });

  it("builds unified diff for edit", () => {
    const code = buildEditDiffCode({
      name: "Edit",
      arg: JSON.stringify({
        path: "hello.ts",
        old_string: "one\ntwo\nthree",
        new_string: "one\ntwo!\nthree",
      }),
    });
    expect(code).toContain("--- a/hello.ts");
    expect(code).toContain("+++ b/hello.ts");
    expect(code).toContain("-two");
    expect(code).toContain("+two!");
  });

  it("returns null for write (show raw output)", () => {
    expect(
      buildEditDiffCode({
        name: "Write",
        arg: JSON.stringify({ path: "a.ts", content: "x" }),
      })
    ).toBeNull();
  });
});
