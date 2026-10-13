import { normalizeToolName } from "./toolMeta";

const MAX_DIFF_ROWS = 5000;
const MAX_DIFF_CELLS = 1_000_000;

function parseArg(arg: string): Record<string, unknown> | null {
  const s = arg.trim();
  if (!s.startsWith("{")) return null;
  try {
    const v = JSON.parse(s);
    return v && typeof v === "object" && !Array.isArray(v)
      ? (v as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

function splitLines(s: string): string[] {
  if (s === "") return [];
  const lines = s.split("\n");
  if (lines.length > 0 && lines[lines.length - 1] === "") lines.pop();
  return lines;
}

/**
 * 校验一份 unified diff 是否可用：必须是非空字符串且含 hunk 头（`@@`）。
 *
 * 后端 `WorkspaceEditTool` 的 `diff` 字段在两种情况下会缺席 ——
 * 历史会话（字段还不存在）与生成失败（片段过大等），所以调用方必须能区分
 * 「拿到一份能用的 patch」和「拿到了个空壳」。
 */
export function isUsableUnifiedPatch(patch: unknown): patch is string {
  return typeof patch === "string" && patch.includes("@@");
}

/**
 * Build a unified-diff string for Edit old_string/new_string.
 *
 * 三级回退（顺序不能变）：
 * 1. `backendPatch` —— 后端产出的 unified diff，带**文件真实行号**与前后 3 行上下文；
 * 2. 下面基于 `old_string` / `new_string` 两个片段的 LCS 合成 —— 历史会话靠它；
 * 3. `null` → 调用方退回「参数 + 结果」两段。
 */
export function buildEditDiffCode(tool: {
  name: string;
  arg: string;
  /** 后端 unified diff（优先）；缺失 / 无效时回退 LCS */
  backendPatch?: string | null;
}): string | null {
  const kind = normalizeToolName(tool.name);
  if (kind !== "edit" && kind !== "multi_edit") return null;
  if (isUsableUnifiedPatch(tool.backendPatch)) return tool.backendPatch;
  const d = parseArg(tool.arg);
  if (!d) return null;
  if (d.replace_all === true) return null;

  const before = typeof d.old_string === "string" ? d.old_string : undefined;
  const after = typeof d.new_string === "string" ? d.new_string : undefined;
  if (before === undefined || after === undefined) return null;

  const oldLines = splitLines(before);
  const newLines = splitLines(after);
  const n = oldLines.length;
  const m = newLines.length;
  if (n > MAX_DIFF_ROWS || m > MAX_DIFF_ROWS) return null;
  if ((n + 1) * (m + 1) > MAX_DIFF_CELLS) return null;

  const dp: number[][] = Array.from({ length: n + 1 }, () =>
    Array.from({ length: m + 1 }, () => 0)
  );
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      dp[i]![j] =
        oldLines[i - 1] === newLines[j - 1]
          ? dp[i - 1]![j - 1]! + 1
          : Math.max(dp[i - 1]![j]!, dp[i]![j - 1]!);
    }
  }

  type Op = { type: "context" | "add" | "del"; text: string };
  const ops: Op[] = [];
  let i = n;
  let j = m;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      ops.push({ type: "context", text: oldLines[i - 1]! });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || dp[i]![j - 1]! >= dp[i - 1]![j]!)) {
      ops.push({ type: "add", text: newLines[j - 1]! });
      j--;
    } else {
      ops.push({ type: "del", text: oldLines[i - 1]! });
      i--;
    }
  }
  ops.reverse();

  const path =
    (typeof d.path === "string" && d.path) ||
    (typeof d.file_path === "string" && d.file_path) ||
    "file";
  const body = ops
    .map((op) => {
      if (op.type === "add") return `+${op.text}`;
      if (op.type === "del") return `-${op.text}`;
      return ` ${op.text}`;
    })
    .join("\n");
  return `--- a/${path}\n+++ b/${path}\n@@\n${body}`;
}

/**
 * `write` 没有 `old_string`，构造不出真实 diff。
 *
 * 但内联 diff 预览需要一份「整文件新增」的 patch —— ZCode 对新建文件同样走这条
 * 轻量 diff 路径（`patchDiffPreview` 的 `isCreatedFile` 分支），否则写入工具
 * 展开后什么都没有。这里按同一语义合成：正文每行都是 `+`。
 *
 * 与 `buildEditDiffCode` 一样，拿不到内容 / 超出行数上限时返回 null，
 * 由调用方退回「参数 + 结果」两段。
 */
export function buildWriteDiffCode(name: string, arg: string): string | null {
  if (normalizeToolName(name) !== "write") return null;
  const d = parseArg(arg);
  if (!d) return null;

  const path =
    (typeof d.path === "string" && d.path) ||
    (typeof d.file_path === "string" && d.file_path) ||
    (typeof d.filePath === "string" && d.filePath) ||
    "";
  if (!path) return null;

  const content =
    typeof d.content === "string"
      ? d.content
      : typeof d.new_string === "string"
        ? d.new_string
        : typeof d.text === "string"
          ? d.text
          : "";
  if (!content) return null;

  const lines = splitLines(content);
  if (lines.length === 0 || lines.length > MAX_DIFF_ROWS) return null;

  const body = lines.map((line) => `+${line}`).join("\n");
  return `--- a/${path}\n+++ b/${path}\n@@\n${body}`;
}

export function extractEditPath(arg: string): string | undefined {
  const d = parseArg(arg);
  if (!d) return undefined;
  if (typeof d.path === "string") return d.path;
  if (typeof d.file_path === "string") return d.file_path;
  return undefined;
}

/** 多文件 edit 的单个文件摘要（对齐 ZCode `rawFileSummaries[number]`）。 */
export type EditFileSummary = {
  path: string;
  fileName: string;
  patch?: string;
  added: number;
  removed: number;
};

/**
 * 把 edit / multi_edit / write 的入参拆成「一个文件一个摘要」。
 *
 * ZCode 的多文件 edit（`rawFileSummaries`）在 Reactor 侧没有对应后端字段，
 * 兼容三种常见的入参形态：
 * 1. `{ files: [{ path, old_string, new_string }, ...] }`
 * 2. `{ edits: [{ file_path, old_string, new_string }, ...] }`
 * 3. 单文件 `{ path, old_string, new_string }` → 长度 1 的数组
 *
 * `write` 也产出摘要（只有 path + 新增行数，没有 patch）—— 否则 `EditToolCall` 拿不到
 * `singlePath`，文件名 chip 根本不渲染，写入工具就没有「点开右侧」的入口。
 *
 * 返回空数组表示「拿不到逐文件信息」（例如 `replace_all`），调用方应降级。
 */
export function extractEditFileSummaries(
  name: string,
  arg: string,
  backendPatch?: string | null
): EditFileSummary[] {
  const key = normalizeToolName(name);
  if (key !== "edit" && key !== "multi_edit" && key !== "write") return [];
  const d = parseArg(arg);
  if (!d) return [];

  // write：单文件、无 diff，只有「写入了多少行」。
  if (key === "write") {
    const path =
      (typeof d.path === "string" && d.path) ||
      (typeof d.file_path === "string" && d.file_path) ||
      (typeof d.filePath === "string" && d.filePath) ||
      "";
    if (!path) return [];
    const stats = editDiffStats(key, arg);
    return [
      {
        path,
        fileName: path.split(/[\\/]/).filter(Boolean).pop() || path,
        patch: undefined,
        added: stats?.added ?? 0,
        removed: stats?.removed ?? 0,
      },
    ];
  }

  const listRaw = Array.isArray(d.files)
    ? d.files
    : Array.isArray(d.edits)
      ? d.edits
      : Array.isArray(d.changes)
        ? d.changes
        : null;

  const entries: Record<string, unknown>[] = listRaw
    ? listRaw.filter(
      (item): item is Record<string, unknown> =>
        Boolean(item) && typeof item === "object" && !Array.isArray(item)
    )
    : [d];

  // 后端 diff 是「一次调用一个文件」的产物，只有单文件入参时才可能对得上；
  // 多文件入参（files/edits/changes）下套用同一份 patch 会把每个子卡都渲染成第一个文件的内容。
  const singleBackendPatch = entries.length === 1 ? backendPatch : null;

  const out: EditFileSummary[] = [];
  for (const entry of entries) {
    const path =
      (typeof entry.path === "string" && entry.path) ||
      (typeof entry.file_path === "string" && entry.file_path) ||
      (typeof entry.filePath === "string" && entry.filePath) ||
      "";
    if (!path) continue;
    const patch = buildEditDiffCode({
      name: key,
      arg: JSON.stringify(entry),
      backendPatch: singleBackendPatch,
    });
    let added = 0;
    let removed = 0;
    if (patch) {
      for (const line of patch.split("\n")) {
        if (line.startsWith("+++") || line.startsWith("---")) continue;
        if (line.startsWith("+")) added += 1;
        else if (line.startsWith("-")) removed += 1;
      }
    }
    out.push({
      path,
      fileName: path.split(/[\\/]/).filter(Boolean).pop() || path,
      patch: patch || undefined,
      added,
      removed,
    });
  }
  return out;
}

/** 多文件 edit 的总变更量（对齐 ZCode `totalChangeStat`）。 */
export function summarizeEditFiles(files: EditFileSummary[]): {
  added: number;
  removed: number;
} {
  return files.reduce(
    (acc, file) => ({
      added: acc.added + file.added,
      removed: acc.removed + file.removed,
    }),
    {
      added: 0,
      removed: 0,
    }
  );
}

export function prefersToolDiffPanel(name: string): boolean {
  const key = normalizeToolName(name);
  return key === "edit" || key === "multi_edit" || key === "write";
}

/** Edit/Write 行尾 chip：`+A −B`（对齐 kimi EditTool） */
export function editDiffStats(
  name: string,
  arg: string,
  backendPatch?: string | null
): { added: number; removed: number } | null {
  const key = normalizeToolName(name);
  if (key !== "edit" && key !== "multi_edit" && key !== "write") {
    return null;
  }

  // 上下文行以空格开头、`@@` 以 `@` 开头，都不会被计入 `+`/`-`；
  // 所以换用后端 patch 后计数语义不变（它本来就只统计真正的变更行）。
  const code = buildEditDiffCode({
    name,
    arg,
    backendPatch,
  });
  if (code) {
    let added = 0;
    let removed = 0;
    for (const line of code.split("\n")) {
      if (line.startsWith("+++") || line.startsWith("---")) continue;
      if (line.startsWith("+")) added += 1;
      else if (line.startsWith("-")) removed += 1;
    }
    if (added || removed) {
      return { added, removed };
    }
  }

  if (key === "write") {
    const d = parseArg(arg);
    const content =
      d && typeof d.content === "string"
        ? d.content
        : d && typeof d.new_string === "string"
          ? d.new_string
          : "";
    if (content) {
      return { added: splitLines(content).length, removed: 0 };
    }
  }

  return null;
}

export function formatEditDiffChip(name: string, arg: string): string {
  const stats = editDiffStats(name, arg);
  return stats ? formatDiffStats(stats) : "";
}

/**
 * `+A −B` 的唯一格式化入口。
 * 调用方若已经拿到 `editDiffStats` 结果（例如要额外渲染翻页数字），走这里复用，
 * 不要再自己拼一遍字符串——历史上后缀/符号在不同文件各写一份，出过 `-` 与 `−` 混用。
 */
export function formatDiffStats(stats: {
  added: number;
  removed: number;
}): string {
  if (!stats.added && !stats.removed) return "";
  return `+${stats.added} −${stats.removed}`;
}
