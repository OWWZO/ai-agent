import { describe, expect, it } from "vitest";
import {
  buildEditDiffCode,
  editDiffStats,
  extractEditFileSummaries,
  extractEditPath,
  isUsableUnifiedPatch,
  prefersToolDiffPanel,
  summarizeEditFiles,
} from "./toolDiff";

/** 后端 `workspace_edit` 产出的 unified diff（带 hunk 行号区间与上下文行）。 */
const BACKEND_PATCH = [
  "--- a/hello.ts",
  "+++ b/hello.ts",
  "@@ -44,7 +44,7 @@",
  " 第44行",
  " 第45行",
  " 第46行",
  "-第47行",
  "+第47行-新",
  " 第49行",
  " 第50行",
  "",
].join("\n");

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

describe("buildEditDiffCode 的三级回退（后端 diff → 片段 LCS → null）", () => {
  const singleArg = JSON.stringify({
    path: "hello.ts",
    old_string: "one",
    new_string: "two",
  });

  it("传了 backendPatch 就原样使用，不被 LCS 覆盖", () => {
    const code = buildEditDiffCode({
      name: "Edit",
      arg: singleArg,
      backendPatch: BACKEND_PATCH,
    });
    expect(code).toBe(BACKEND_PATCH);
    expect(code).toContain("@@ -44,7 +44,7 @@");
  });

  it("不传 backendPatch 时回退 LCS，仍是裸 @@ 头（既有行为逐字节不变）", () => {
    const code = buildEditDiffCode({
      name: "Edit",
      arg: JSON.stringify({
        path: "hello.ts",
        old_string: "one\ntwo\nthree",
        new_string: "one\ntwo!\nthree",
      }),
    });
    expect(code).toContain("--- a/hello.ts\n+++ b/hello.ts\n@@\n");
  });

  it("backendPatch 无效时回退 LCS", () => {
    for (const bad of ["", "not a patch", "--- a/x\n+++ b/x\n", null, undefined]) {
      const code = buildEditDiffCode({
        name: "Edit",
        arg: singleArg,
        backendPatch: bad,
      });
      expect(code).toContain("--- a/hello.ts\n+++ b/hello.ts\n@@\n");
    }
  });

  it("replace_all 有后端 patch 时不再返回 null（LCS 路径仍然返回 null）", () => {
    const arg = JSON.stringify({
      path: "a.ts",
      old_string: "x",
      new_string: "y",
      replace_all: true,
    });
    expect(buildEditDiffCode({
      name: "Edit",
      arg
    })).toBeNull();
    expect(buildEditDiffCode({
      name: "Edit",
      arg,
      backendPatch: BACKEND_PATCH
    })).toBe(
      BACKEND_PATCH
    );
  });

  it("非 edit 工具不吃 backendPatch（write 的 diff 属 follow-up）", () => {
    expect(
      buildEditDiffCode({
        name: "Write",
        arg: singleArg,
        backendPatch: BACKEND_PATCH
      })
    ).toBeNull();
  });

  it("isUsableUnifiedPatch 只认含 @@ 的字符串", () => {
    expect(isUsableUnifiedPatch(BACKEND_PATCH)).toBe(true);
    expect(isUsableUnifiedPatch("--- a/x\n+++ b/x\n")).toBe(false);
    expect(isUsableUnifiedPatch(null)).toBe(false);
    expect(isUsableUnifiedPatch(42)).toBe(false);
  });
});

describe("editDiffStats 消费后端 patch", () => {
  const arg = JSON.stringify({
    path: "hello.ts",
    old_string: "第47行",
    new_string: "第47行-新"
  });

  it("上下文行不计入 +A −B", () => {
    expect(editDiffStats("Edit", arg, BACKEND_PATCH)).toEqual({
      added: 1,
      removed: 1
    });
  });

  it("没有后端 patch 时结果与 LCS 路径一致", () => {
    expect(editDiffStats("Edit", arg)).toEqual({
      added: 1,
      removed: 1
    });
  });

  it("多 hunk 的后端 patch 逐 hunk 累加", () => {
    const multi = [
      "--- a/x.ts",
      "+++ b/x.ts",
      "@@ -2,7 +2,8 @@",
      " L2",
      "-TOKEN",
      "+TOKEN-A",
      "+TOKEN-B",
      " L8",
      "@@ -27,7 +28,8 @@",
      " L27",
      "-TOKEN",
      "+TOKEN-A",
      "+TOKEN-B",
      " L33",
      "",
    ].join("\n");
    expect(editDiffStats("Edit", arg, multi)).toEqual({
      added: 4,
      removed: 2
    });
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
      JSON.stringify({
        path: "a.ts",
        content: "one\ntwo\nthree"
      })
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

  it("单文件 + backendPatch → 子卡用后端 patch 并据它计数", () => {
    const files = extractEditFileSummaries(
      "Edit",
      JSON.stringify({
        path: "hello.ts",
        old_string: "第47行",
        new_string: "第47行-新"
      }),
      BACKEND_PATCH
    );
    expect(files).toHaveLength(1);
    expect(files[0].patch).toBe(BACKEND_PATCH);
    expect(files[0].added).toBe(1);
    expect(files[0].removed).toBe(1);
  });

  it("replace_all 单文件 + backendPatch → 也有 patch（不再退化成参数 JSON）", () => {
    const files = extractEditFileSummaries(
      "Edit",
      JSON.stringify({
        path: "hello.ts",
        old_string: "第47行",
        new_string: "第47行-新",
        replace_all: true,
      }),
      BACKEND_PATCH
    );
    expect(files[0].patch).toBe(BACKEND_PATCH);
  });

  it("多文件入参不吃单份 backendPatch（否则每张子卡都是第一个文件的内容）", () => {
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
            path: "b.ts",
            old_string: "p",
            new_string: "q"
          },
        ],
      }),
      BACKEND_PATCH
    );
    expect(files).toHaveLength(2);
    expect(files[0].patch).not.toBe(BACKEND_PATCH);
    expect(files[0].patch).toContain("--- a/a.ts");
    expect(files[1].patch).toContain("--- a/b.ts");
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
