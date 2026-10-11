import { describe, expect, it } from "vitest";
import {
  buildEditDiffCode,
  extractEditFileSummaries,
  extractEditPath,
  prefersToolDiffPanel,
  summarizeEditFiles,
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
        arg: JSON.stringify({
          path: "a.ts",
          content: "x"
        }),
      })
    ).toBeNull();
  });
});

describe("extractEditFileSummaries", () => {
  it("单文件 edit 产出长度 1 的数组", () => {
    const files = extractEditFileSummaries(
      "Edit",
      JSON.stringify({
        path: "src/a.ts",
        old_string: "one\ntwo",
        new_string: "one\ntwo!",
      })
    );
    expect(files).toHaveLength(1);
    expect(files[0].path).toBe("src/a.ts");
    expect(files[0].fileName).toBe("a.ts");
    expect(files[0].added).toBe(1);
    expect(files[0].removed).toBe(1);
    expect(files[0].patch).toContain("--- a/src/a.ts");
  });

  it("files[] 形态产出逐文件摘要", () => {
    const files = extractEditFileSummaries(
      "multi_edit",
      JSON.stringify({
        files: [
          {
            path: "a.ts",
            old_string: "x",
            new_string: "y"
          },
          {
            file_path: "b/c.ts",
            old_string: "p\nq",
            new_string: "p"
          },
        ],
      })
    );
    expect(files.map((f) => f.path)).toEqual(["a.ts", "b/c.ts"]);
    expect(files[1].fileName).toBe("c.ts");
  });

  it("edits[] 形态同样被识别", () => {
    const files = extractEditFileSummaries(
      "multi_edit",
      JSON.stringify({
        edits: [{
          file_path: "z.ts",
          old_string: "1",
          new_string: "2"
        }],
      })
    );
    expect(files).toHaveLength(1);
    expect(files[0].path).toBe("z.ts");
  });

  it("write 产出 path-only 摘要（否则写入工具没有文件名 chip）", () => {
    const files = extractEditFileSummaries(
      "Write",
      JSON.stringify({ path: "a.ts", content: "one\ntwo\nthree" })
    );
    expect(files).toHaveLength(1);
    expect(files[0].path).toBe("a.ts");
    expect(files[0].fileName).toBe("a.ts");
    expect(files[0].patch).toBeUndefined();
    expect(files[0].added).toBe(3);
    expect(files[0].removed).toBe(0);
  });

  it("非 edit/write 工具返回空数组（调用方走降级）", () => {
    expect(
      extractEditFileSummaries("Bash", JSON.stringify({ path: "a.ts" }))
    ).toEqual([]);
    expect(
      extractEditFileSummaries("Read", JSON.stringify({ path: "a.ts" }))
    ).toEqual([]);
  });

  it("replace_all 的 patch 缺失但路径仍保留", () => {
    const files = extractEditFileSummaries(
      "Edit",
      JSON.stringify({
        path: "a.ts",
        old_string: "x",
        new_string: "y",
        replace_all: true,
      })
    );
    expect(files).toHaveLength(1);
    expect(files[0].path).toBe("a.ts");
    expect(files[0].patch).toBeUndefined();
  });

  it("缺少 path 的条目被丢弃", () => {
    const files = extractEditFileSummaries(
      "multi_edit",
      JSON.stringify({
        files: [{
          old_string: "x",
          new_string: "y"
        }]
      })
    );
    expect(files).toEqual([]);
  });
});

describe("summarizeEditFiles", () => {
  it("累加所有文件的增删行数", () => {
    expect(
      summarizeEditFiles([
        {
          path: "a",
          fileName: "a",
          added: 3,
          removed: 1
        },
        {
          path: "b",
          fileName: "b",
          added: 2,
          removed: 4
        },
      ])
    ).toEqual({
      added: 5,
      removed: 5
    });
  });

  it("空数组为零", () => {
    expect(summarizeEditFiles([])).toEqual({
      added: 0,
      removed: 0
    });
  });
});
