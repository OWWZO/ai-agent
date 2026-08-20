import {
  resolveToolCallArgumentsText,
  resolveToolCallInput,
} from "@/utils/chat/toolCalls";
import { isAgentDispatchTask } from "@/utils/chat/subagent";
import { isTimelineToolActive } from "@/components/ChatView/streamState";
import { resolveTaskSummaryText } from "../contentHelpers";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function pickStatus(...sources: Array<Record<string, unknown> | undefined>): string {
  for (const source of sources) {
    const status = source?.status;
    if (typeof status === "string" && status.trim()) {
      return status.trim().toLowerCase();
    }
  }
  return "";
}

function pickArgumentsText(
  ...sources: Array<Record<string, unknown> | MESSAGE.ResultMap | undefined>
): string {
  for (const source of sources) {
    if (!source) continue;
    const direct = resolveToolCallArgumentsText(source as MESSAGE.ResultMap);
    if (direct) return direct;
    if (typeof (source as { argumentsText?: unknown }).argumentsText === "string") {
      const text = (source as { argumentsText: string }).argumentsText;
      if (text) return text;
    }
  }
  return "";
}

export function resolveTaskToolName(tool: CHAT.Task): string {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = isRecord(resultMap.resultMap)
    ? (resultMap.resultMap as Record<string, unknown>)
    : {};
  const fromResult =
    (typeof resultMap.toolName === "string" && resultMap.toolName) ||
    (typeof nested.toolName === "string" && nested.toolName) ||
    (typeof (tool as { toolName?: string }).toolName === "string" &&
      (tool as { toolName?: string }).toolName) ||
    "";
  const fromToolResult =
    (typeof tool.toolResult?.toolName === "string" &&
      tool.toolResult.toolName) ||
    "";
  if (isAgentDispatchTask(tool)) {
    return fromResult || fromToolResult || "task";
  }
  return fromResult || fromToolResult || tool.messageType || "tool";
}

export function resolveTaskToolArg(tool: CHAT.Task): string {
  const top = tool as unknown as Record<string, unknown>;
  const resultMap = tool.resultMap as MESSAGE.ResultMap | undefined;
  const nested = isRecord(resultMap?.resultMap)
    ? (resultMap!.resultMap as MESSAGE.ResultMap)
    : undefined;
  // 执行态已有完整 input 时优先用 input（避免旧 argumentsText 盖住终态）
  const status = pickStatus(
    resultMap as Record<string, unknown> | undefined,
    nested as Record<string, unknown> | undefined,
    top
  );
  const argsStreamingFlag =
    (resultMap as { argsStreaming?: boolean } | undefined)?.argsStreaming ===
      true ||
    (nested as { argsStreaming?: boolean } | undefined)?.argsStreaming === true;
  // 参数流/running 早期优先 raw args（LeAgent：argumentsRaw 在 running 时仍展示）
  const preferRawArgs =
    argsStreamingFlag ||
    status === "streaming" ||
    status === "preparing" ||
    status === "running" ||
    !status;
  const argumentsText = pickArgumentsText(
    resultMap,
    nested,
    top,
    isRecord(top.resultMap) ? top.resultMap : undefined
  );
  if (preferRawArgs && argumentsText) {
    return argumentsText;
  }
  const input = resolveToolCallInput(resultMap);
  if (Object.keys(input).length > 0) {
    try {
      return JSON.stringify(input);
    } catch {
      /* fall through */
    }
  }

  // buildTaskFromEventData 可能把 input 摊到 task 顶层
  if (isRecord(top.input) && Object.keys(top.input).length > 0) {
    try {
      return JSON.stringify(top.input);
    } catch {
      /* fall through */
    }
  }

  const toolParam = tool.toolResult?.toolParam;
  if (isRecord(toolParam) && Object.keys(toolParam).length > 0) {
    try {
      return JSON.stringify(toolParam);
    } catch {
      /* fall through */
    }
  }

  if (nested) {
    const nestedInput = resolveToolCallInput(nested);
    if (Object.keys(nestedInput).length > 0) {
      try {
        return JSON.stringify(nestedInput);
      } catch {
        /* fall through */
      }
    }
  }

  if (argumentsText) {
    return argumentsText;
  }

  return "";
}

export function resolveTaskToolStatus(
  tool: CHAT.Task
): "running" | "ok" | "error" {
  const top = tool as unknown as Record<string, unknown>;
  const resultMap = tool.resultMap as MESSAGE.ResultMap | undefined;
  const nested = isRecord(resultMap?.resultMap)
    ? (resultMap!.resultMap as Record<string, unknown>)
    : undefined;
  const status = pickStatus(
    resultMap as Record<string, unknown> | undefined,
    nested,
    top
  );
  if (
    status === "failed" ||
    status === "error" ||
    status === "danger" ||
    Boolean((tool.resultMap as Record<string, unknown> | undefined)?.errorMsg)
  ) {
    return "error";
  }
  if (isTimelineToolActive(tool) || status === "streaming" || status === "preparing" || status === "running") {
    return "running";
  }
  if (
    tool.finish ||
    tool.isFinal ||
    tool.resultMap?.isFinal ||
    status === "success" ||
    status === "ok" ||
    status === "completed" ||
    status === "done"
  ) {
    return "ok";
  }
  return "ok";
}

export function resolveTaskToolOutput(tool: CHAT.Task): string[] {
  const chunks: string[] = [];
  const push = (value: unknown) => {
    if (typeof value !== "string") return;
    const trimmed = value.replace(/\r\n/g, "\n").trimEnd();
    if (!trimmed) return;
    for (const line of trimmed.split("\n")) {
      chunks.push(line);
    }
  };

  push(tool.toolResult?.toolResult);
  push(resolveTaskSummaryText(tool));
  push(tool.result);
  push(tool.resultMap?.codeOutput);
  push(tool.resultMap?.data);
  push(tool.resultMap?.summary);
  push((tool.resultMap as Record<string, unknown> | undefined)?.errorMsg);

  if (chunks.length === 0 && tool.resultMap?.code) {
    push(tool.resultMap.code);
  }

  return chunks;
}

export function formatDurationLabel(ms?: number): string {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return "";
  if (ms < 1000) return `${Math.round(ms)}ms`;
  const sec = ms / 1000;
  if (sec < 60) return `${sec.toFixed(sec < 10 ? 1 : 0)}s`;
  const min = Math.floor(sec / 60);
  const rem = Math.round(sec % 60);
  return `${min}m ${rem}s`;
}
