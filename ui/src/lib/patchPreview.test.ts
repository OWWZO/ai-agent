import { describe, expect, it } from "vitest";
import {
  getLightweightDiffLineParts,
  getPatchPreviewLineContent,
  getPlainTextPatchPreviewLines,
  parseTruncatedMarkerOmittedLineCount,
} from "./patchPreview";

const SINGLE_FILE_PATCH = [
  "--- a/src/a.ts",
  "+++ b/src/a.ts",
  "@@",
  " const keep = 1;",
  "-const a = 1;",
  "+const a = 2;",
].join("\n");

describe("patchPreview", () => {
  it("剥掉 --- / +++ / @@ 协议头，只保留 hunk 正文", () => {
    const lines = getPlainTextPatchPreviewLines(SINGLE_FILE_PATCH);

    expect(lines).toEqual([
      " const keep = 1;",
      "-const a = 1;",
      "+const a = 2;",
    ]);
  });

  it("按前缀把行分成 added / removed / context", () => {
    expect(getLightweightDiffLineParts("+x")).toEqual({
      content: "x",
      kind: "added",
      marker: "+",
      raw: "+x",
    });
    expect(getLightweightDiffLineParts("-x").kind).toBe("removed");
    expect(getLightweightDiffLineParts(" x").kind).toBe("context");
    // 无前缀的裸行（rename-only 的元信息）也算 context，但 marker 为空
    expect(getLightweightDiffLineParts("rename from a.ts").marker).toBe("");
  });

  it("正文里以 `++` / `--` 开头的行不会被当成协议头丢掉", () => {
    const patch = ["--- a/x.md", "+++ b/x.md", "@@", "+++ 这是正文", "--- 这也是正文"].join("\n");
    const lines = getPlainTextPatchPreviewLines(patch);

    expect(lines).toEqual(["+++ 这是正文", "--- 这也是正文"]);
    // 去掉一个前缀字符后才是真正的内容
    expect(getPatchPreviewLineContent(lines[0])).toBe("++ 这是正文");
  });

  it("没有 @@ 的 patch 退化为保留元信息行，而不是空白", () => {
    const patch = ["diff --git a/x b/x", "index 111..222 100644", "rename from x", "rename to y"].join(
      "\n"
    );

    expect(getPlainTextPatchPreviewLines(patch)).toEqual(["rename from x", "rename to y"]);
  });

  it("超过 800 行时中间插截断 marker，尾部保留", () => {
    const body = Array.from({ length: 1200 }, (_, index) => `+line ${index}`);
    const lines = getPlainTextPatchPreviewLines(["--- a/x", "+++ b/x", "@@", ...body].join("\n"));

    expect(lines).toHaveLength(800);
    const markerIndex = lines.findIndex((line) => parseTruncatedMarkerOmittedLineCount(line) !== null);
    expect(markerIndex).toBeGreaterThan(0);
    const omitted = parseTruncatedMarkerOmittedLineCount(lines[markerIndex]);
    expect(omitted).toBe(1200 - 799);
    // 尾部仍然保留最后一行
    expect(lines[lines.length - 1]).toBe("+line 1199");
  });

  it("空 patch 返回单个空行，调用方不必再分支", () => {
    expect(getPlainTextPatchPreviewLines("")).toEqual([""]);
    expect(parseTruncatedMarkerOmittedLineCount("+normal line")).toBeNull();
  });
});
