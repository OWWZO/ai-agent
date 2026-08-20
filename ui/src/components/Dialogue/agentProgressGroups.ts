import {
  formatSubAgentDuration,
  isAgentDispatchTask,
  resolveSubAgentDisplay,
} from "@/utils/chat/subagent";
import {
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
} from "./tools/toolTaskAdapter";
import { toolLabel, toolSummary } from "./tools/toolMeta";
import type { AgentPhase } from "@/types/agentRuntime";

export type AgentProgressGroup = {
  key: string;
  /** 「Calling …」行；空字符串表示无调用头的裸输出组 */
  call: string;
  output: string[];
};

const OUTPUT_FOLD_THRESHOLD = 8;
const OUTPUT_HEAD = 5;
const OUTPUT_TAIL = 2;

export { OUTPUT_FOLD_THRESHOLD, OUTPUT_HEAD, OUTPUT_TAIL };

/** 对齐 kimi：以 `Calling ` 开头的行开启新组，后续非 Calling 行归入其 output */
export function groupProgressLines(lines: string[]): AgentProgressGroup[] {
  const groups: AgentProgressGroup[] = [];
  let current: AgentProgressGroup | null = null;
  let idx = 0;

  for (const raw of lines) {
    const line = raw.trimEnd();
    if (!line) continue;
    if (line.startsWith("Calling ")) {
      current = { key: `g${idx++}`, call: line, output: [] };
      groups.push(current);
    } else if (current) {
      current.output.push(line);
    } else {
      current = { key: `g${idx++}`, call: "", output: [line] };
      groups.push(current);
    }
  }

  return groups;
}

function callingLineForChild(child: CHAT.Task): string {
  const name = resolveTaskToolName(child);
  const label = toolLabel(name);
  const summary = toolSummary(name, resolveTaskToolArg(child));
  return summary ? `Calling ${label} · ${summary}` : `Calling ${label}`;
}

/**
 * 详情面板进度源：
 * 1. 优先用 SSE progressLines（含 Calling 前缀）
 * 2. 否则用嵌套子工具合成 Calling 组
 * 3. 心跳 running · … 行并入无 call 组或附加在末尾
 */
export function buildAgentProgressGroups(tool: CHAT.Task): AgentProgressGroup[] {
  const display = resolveSubAgentDisplay(tool);
  const fromLines = groupProgressLines(display.progressLines || []);
  const hasCalling = fromLines.some((g) => g.call.startsWith("Calling "));
  if (hasCalling) {
    return fromLines;
  }

  const children = Array.isArray(tool.children) ? tool.children : [];
  const fromChildren: AgentProgressGroup[] = [];
  children.forEach((child, index) => {
    if (isAgentDispatchTask(child)) {
      return;
    }
    const output = resolveTaskToolOutput(child);
    fromChildren.push({
      key: `c${index}`,
      call: callingLineForChild(child),
      output,
    });
  });

  // 心跳等非 Calling 行：挂到独立组，避免丢掉
  const heartbeatOrOther = fromLines.filter((g) => !g.call);
  if (fromChildren.length === 0) {
    return fromLines;
  }
  return [...fromChildren, ...heartbeatOrOther];
}

export function foldCount(group: AgentProgressGroup): number {
  return Math.max(0, group.output.length - OUTPUT_HEAD - OUTPUT_TAIL);
}

export function shouldFoldGroup(group: AgentProgressGroup): boolean {
  return group.output.length > OUTPUT_FOLD_THRESHOLD;
}

export function resolveAgentPhaseLabel(
  phase: AgentPhase | string | undefined,
  status: string
): string {
  const normalized = (phase || "").toLowerCase();
  switch (normalized) {
    case "queued":
      return "Queued";
    case "working":
      return "Working";
    case "suspended":
      return "Suspended";
    case "completed":
      return "Completed";
    case "failed":
      return "Failed";
    default:
      if (status === "running") return "Working";
      if (status === "failed") return "Failed";
      if (status === "completed") return "Completed";
      return "Queued";
  }
}

export function resolveAgentPhaseTone(
  phaseLabel: string
): "running" | "ok" | "error" | "neutral" {
  if (phaseLabel === "Working" || phaseLabel === "Queued") return "running";
  if (phaseLabel === "Failed") return "error";
  if (phaseLabel === "Completed") return "ok";
  return "neutral";
}

export function formatAgentElapsed(display: {
  elapsedMs?: number;
  totalDurationMs?: number;
}): string {
  return (
    formatSubAgentDuration(display.totalDurationMs) ||
    formatSubAgentDuration(display.elapsedMs) ||
    ""
  );
}
