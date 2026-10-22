/**
 * unified diff → 轻量预览行。
 *
 * 移植自 ZCode `lib/patchDiffPreview.ts` 的轻量分支，只保留工具卡内联 diff 用得到的部分：
 * - 剥掉 `diff --git` / `index` / `---` / `+++` 这些协议头，只留 hunk 正文；
 * - 没有 hunk 的 patch（rename-only / mode-only）退化为保留元信息行，避免展开后一片空白；
 * - 超大 patch 中间插一条截断 marker，由渲染层翻译成「已省略 N 行」。
 *
 * ⚠️ Reactor 的 `buildEditDiffCode()` 产出的是**简化 patch**：文件头 + 裸 `@@` + 正文，
 * 没有 `@@ -1,3 +1,3 @@` 的行号区间。所以这里不能依赖行号做校验，只能按前缀分类。
 */

export type LightweightDiffLineKind = "added" | "removed" | "context";

export interface LightweightDiffLineParts {
  content: string;
  kind: LightweightDiffLineKind;
  marker: string;
  raw: string;
}

/** 超过这个行数就中间截断：内联 diff 挂在消息流里，不能让它拖垮渲染。 */
const MAX_PLAIN_TEXT_FALLBACK_RENDER_LINES = 800;
// 与 ZCode 同样用内部 marker token 承载截断信息，展示文案留给组件层（避免 lib 层硬编码中文）。
const TRUNCATED_MARKER_PREFIX = "\\ __CUTE_DIFF_TRUNCATED__:";
const TRUNCATED_MARKER_REGEX = /^\\ __CUTE_DIFF_TRUNCATED__:(\d+)$/;

function isPatchHunkHeaderLine(line: string): boolean {
  return line === "@@" || line.startsWith("@@ ");
}

/**
 * patch 是否具备完整 diff 查看器（`@pierre/diffs` 的 `PatchDiff`）能解析的 hunk 头
 * —— 即带行号区间的 `@@ -44,7 +44,7 @@`。
 *
 * ⚠️ 前端 `toolDiff.ts buildEditDiffCode()` 的 LCS 回退产出的是**裸 `@@`**：
 * 那个形态喂给 `parsePatchFiles` 会得到 `hunks: 0`，面板一片空白。
 * 历史会话（toolResult 里没有后端 `diff` 字段）走的就是这条路径，
 * 所以调用方必须先判定，不兼容时退回 markdown 围栏渲染。
 */
export function isPatchDiffCompatible(patch: string): boolean {
  return /^@@ -\d+(?:,\d+)? \+\d+(?:,\d+)? @@/m.test(patch);
}

function buildTruncatedMarkerLine(omittedLineCount: number): string {
  return `${TRUNCATED_MARKER_PREFIX}${omittedLineCount}`;
}

export function parseTruncatedMarkerOmittedLineCount(line: string): number | null {
  const matched = TRUNCATED_MARKER_REGEX.exec(line);
  if (!matched) return null;

  const omittedLineCount = Number.parseInt(matched[1] ?? "", 10);
  if (!Number.isFinite(omittedLineCount) || omittedLineCount <= 0) return null;
  return omittedLineCount;
}

/** 去掉 unified diff 的 `+` / `-` / 空格协议前缀，取出真正的代码内容。 */
export function getPatchPreviewLineContent(line: string): string {
  if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" ")) {
    return line.slice(1);
  }
  return line;
}

export function getLightweightDiffLineParts(line: string): LightweightDiffLineParts {
  if (line.startsWith("+")) {
    return {
      content: getPatchPreviewLineContent(line),
      kind: "added",
      marker: "+",
      raw: line,
    };
  }
  if (line.startsWith("-")) {
    return {
      content: getPatchPreviewLineContent(line),
      kind: "removed",
      marker: "-",
      raw: line,
    };
  }
  if (line.startsWith(" ")) {
    return {
      content: getPatchPreviewLineContent(line),
      kind: "context",
      marker: " ",
      raw: line,
    };
  }
  return {
    content: getPatchPreviewLineContent(line),
    kind: "context",
    marker: "",
    raw: line,
  };
}

function collectPatchMetadataPreviewLines(lines: readonly string[]): string[] {
  return lines.filter((line) => {
    if (!line.trim()) return false;
    return (
      !line.startsWith("diff --git ") &&
      !line.startsWith("index ") &&
      !line.startsWith("--- ") &&
      !line.startsWith("+++ ")
    );
  });
}

function collectPlainTextPreviewLines(lines: readonly string[]): string[] {
  const previewLines: string[] = [];
  let inHunk = false;

  for (const line of lines) {
    if (!inHunk) {
      if (isPatchHunkHeaderLine(line)) {
        inHunk = true;
      }
      continue;
    }

    // 不能按 `+++ / ---` 前缀过滤：hunk 正文里真实存在以 `++` / `--` 开头的行。
    // 只跳过 hunk 头本身，进入 hunk 之后一律原样保留。
    if (isPatchHunkHeaderLine(line)) {
      continue;
    }
    previewLines.push(line);
  }

  if (inHunk || previewLines.length > 0) {
    return previewLines;
  }

  // rename-only / mode-only 这类 patch 没有 @@，但变更信息仍然要展示。
  return collectPatchMetadataPreviewLines(lines);
}

function limitPlainTextPreviewLines(lines: readonly string[]): string[] {
  if (lines.length <= MAX_PLAIN_TEXT_FALLBACK_RENDER_LINES) {
    return [...lines];
  }

  const keepCount = MAX_PLAIN_TEXT_FALLBACK_RENDER_LINES - 1;
  const headCount = Math.ceil(keepCount / 2);
  const tailCount = keepCount - headCount;

  return [
    ...lines.slice(0, headCount),
    buildTruncatedMarkerLine(lines.length - keepCount),
    ...lines.slice(lines.length - tailCount),
  ];
}

/**
 * 内联 diff 的最终行列表：已剥协议头、已截断、已去掉尾部空行。
 * 空 patch 返回 `[""]`（渲染成一行空白），而不是空数组 —— 与 ZCode 一致，
 * 让调用方不必为「有 patch 但内容为空」再分一次支。
 */
export function getPlainTextPatchPreviewLines(patch: string): string[] {
  const normalized = limitPlainTextPreviewLines(collectPlainTextPreviewLines(patch.split(/\r?\n/)));

  while (normalized.length > 0 && normalized[normalized.length - 1] === "") {
    normalized.pop();
  }

  return normalized.length > 0 ? normalized : [""];
}
