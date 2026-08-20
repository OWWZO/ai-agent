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

/** Build a unified-diff string for Edit old_string/new_string. Null → show raw output. */
export function buildEditDiffCode(tool: {
  name: string;
  arg: string;
}): string | null {
  const kind = normalizeToolName(tool.name);
  if (kind !== "edit" && kind !== "multi_edit") return null;
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

export function extractEditPath(arg: string): string | undefined {
  const d = parseArg(arg);
  if (!d) return undefined;
  if (typeof d.path === "string") return d.path;
  if (typeof d.file_path === "string") return d.file_path;
  return undefined;
}

export function prefersToolDiffPanel(name: string): boolean {
  const key = normalizeToolName(name);
  return key === "edit" || key === "multi_edit" || key === "write";
}

/** Edit/Write 行尾 chip：`+A −B`（对齐 kimi EditTool） */
export function editDiffStats(
  name: string,
  arg: string
): { added: number; removed: number } | null {
  const key = normalizeToolName(name);
  if (key !== "edit" && key !== "multi_edit" && key !== "write") {
    return null;
  }

  const code = buildEditDiffCode({ name, arg });
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
  if (!stats) return "";
  if (!stats.added && !stats.removed) return "";
  return `+${stats.added} −${stats.removed}`;
}
