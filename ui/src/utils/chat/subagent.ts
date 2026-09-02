import {
  resolveTaskResultMap,
  resolveTaskToolResult,
  resolveTaskToolResultText,
} from "./toolCalls";

/**
 * 同步 SubAgent（Agent 工具）展示解析。
 * 对齐后端 AgentDispatchTool 的输入/输出约定。
 */

export const AGENT_DISPATCH_TOOL_NAME = "Agent";

export type SubAgentDisplay = {
  isAgent: boolean;
  description: string;
  subagentType: string;
  prompt: string;
  status: "running" | "completed" | "failed" | "unknown";
  agentId: string;
  content: string;
  totalToolUseCount?: number;
  totalDurationMs?: number;
  errorMsg: string;
  runInBackground: boolean;
  /** 子 Agent 直播文本（SSE subagent_progress kind=text） */
  liveText: string;
  /** Calling… / heartbeat 进度行 */
  progressLines: string[];
  elapsedMs?: number;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function asText(value: unknown): string {
  if (typeof value !== "string") {
    return "";
  }
  return value.trim();
}

function nestedResultMap(resultMap?: Record<string, unknown>) {
  const nested = resultMap?.resultMap;
  return isRecord(nested) ? nested : undefined;
}

function pickInput(task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>) {
  const resultMap = resolveTaskResultMap(task) as Record<string, unknown>;
  const nested = nestedResultMap(resultMap);
  const fromResultMap =
    resultMap?.input ??
    resultMap?.toolParam ??
    nested?.input ??
    nested?.toolParam;
  if (isRecord(fromResultMap)) {
    return fromResultMap;
  }
  const toolParam = resolveTaskToolResult(task)?.toolParam || task?.toolResult?.toolParam;
  if (isRecord(toolParam)) {
    return toolParam;
  }
  // tool_call 经 buildTaskFromEventData spread 后，input 可能在 task 顶层
  const top = task as Record<string, unknown> | undefined;
  if (isRecord(top?.input)) {
    return top.input as Record<string, unknown>;
  }
  if (isRecord(top?.toolParam)) {
    return top.toolParam as Record<string, unknown>;
  }
  return {};
}

function resolveToolName(task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>) {
  const top = task as Record<string, unknown> | undefined;
  const resultMap = resolveTaskResultMap(task) as Record<string, unknown>;
  const nested = nestedResultMap(resultMap);
  const toolResult = resolveTaskToolResult(task);
  return asText(
    toolResult?.toolName ||
      resultMap?.toolName ||
      nested?.toolName ||
      // tool_call spread 后 toolName 常在顶层
      top?.toolName
  );
}

export function isAgentDispatchTask(task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>) {
  if (!task) {
    return false;
  }
  const toolName = resolveToolName(task);
  if (toolName === AGENT_DISPATCH_TOOL_NAME) {
    return true;
  }
  // tool_call 阶段也可能只在 resultMap.toolName
  if (
    (task.messageType === "tool_call" || task.messageType === "tool_result") &&
    toolName === AGENT_DISPATCH_TOOL_NAME
  ) {
    return true;
  }
  return false;
}

/** 是否后台子 Agent（Dock Tasks；前台仍走内联 Agent 卡） */
export function isRunInBackgroundAgent(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task || !isAgentDispatchTask(task)) {
    return false;
  }
  const input = pickInput(task) as Record<string, unknown>;
  if (input.run_in_background === true || input.runInBackground === true) {
    return true;
  }
  const resultMap = resolveTaskResultMap(task) as Record<string, unknown>;
  const nested = isRecord(resultMap.resultMap)
    ? (resultMap.resultMap as Record<string, unknown>)
    : {};
  if (
    resultMap.run_in_background === true ||
    resultMap.runInBackground === true ||
    nested.run_in_background === true ||
    nested.runInBackground === true
  ) {
    return true;
  }
  const observation = asText(resolveTaskToolResultText(task));
  if (/run_in_background\s*[:=]\s*true/i.test(observation)) {
    return true;
  }
  try {
    const parsed = JSON.parse(observation) as Record<string, unknown>;
    if (isRecord(parsed) && (parsed.run_in_background === true || parsed.runInBackground === true)) {
      return true;
    }
  } catch {
    // ignore
  }
  return false;
}

/** 子 Agent 工具事件上的父 Agent tool_use id */
export function resolveParentToolUseId(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task> | Record<string, unknown>
) {
  if (!task) {
    return "";
  }
  const record = task as Record<string, unknown>;
  const resultMap = (record.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolResult = (record.toolResult || {}) as Record<string, unknown>;
  return asText(
    resultMap.parentToolUseId ||
      nested.parentToolUseId ||
      record.parentToolUseId ||
      toolResult.parentToolUseId
  );
}

/**
 * 解析 AgentDispatchTool 返回的 observation 文本：
 * status=completed
 * agentType=Explore
 * ...
 *
 * 报告正文
 */
export function parseAgentObservation(text?: string) {
  const raw = asText(text);
  if (!raw) {
    return {
      status: "" as string,
      agentType: "",
      agentId: "",
      totalToolUseCount: undefined as number | undefined,
      totalDurationMs: undefined as number | undefined,
      content: "",
      errorMsg: "",
    };
  }

  // 优先按「元数据头 + 空行 + 正文」解析
  const parts = raw.split(/\n\n/);
  const header = parts[0] || "";
  const body = parts.slice(1).join("\n\n").trim();

  const meta: Record<string, string> = {};
  for (const line of header.split("\n")) {
    const idx = line.indexOf("=");
    if (idx <= 0) {
      continue;
    }
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim();
    if (key) {
      meta[key] = value;
    }
  }

  if (meta.status || meta.agentType || meta.agentId) {
    const count = meta.totalToolUseCount ? Number(meta.totalToolUseCount) : undefined;
    const duration = meta.totalDurationMs ? Number(meta.totalDurationMs) : undefined;
    return {
      status: meta.status || "",
      agentType: meta.agentType || "",
      agentId: meta.agentId || "",
      totalToolUseCount: Number.isFinite(count) ? count : undefined,
      totalDurationMs: Number.isFinite(duration) ? duration : undefined,
      content: body || (meta.status ? "" : raw),
      errorMsg: meta.errorMsg || "",
    };
  }

  // JSON fallback
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (isRecord(parsed)) {
      return {
        status: asText(parsed.status),
        agentType: asText(parsed.agentType),
        agentId: asText(parsed.agentId),
        totalToolUseCount:
          typeof parsed.totalToolUseCount === "number" ? parsed.totalToolUseCount : undefined,
        totalDurationMs:
          typeof parsed.totalDurationMs === "number" ? parsed.totalDurationMs : undefined,
        content: asText(parsed.content),
        errorMsg: asText(parsed.errorMsg),
      };
    }
  } catch {
    // ignore
  }

  return {
    status: "",
    agentType: "",
    agentId: "",
    totalToolUseCount: undefined,
    totalDurationMs: undefined,
    content: raw,
    errorMsg: "",
  };
}

export function resolveSubAgentDisplay(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
): SubAgentDisplay {
  const input = pickInput(task) as Record<string, unknown>;
  const description = asText(input.description);
  const subagentType = asText(input.subagent_type) || "general-purpose";
  const prompt = asText(input.prompt);

  const observationText = asText(
    resolveTaskToolResultText(task)
  );
  const parsed = parseAgentObservation(observationText);

  let status: SubAgentDisplay["status"] = "unknown";
  const resultMap = resolveTaskResultMap(task) as Record<string, unknown>;
  const nested = nestedResultMap(resultMap);
  const resultStatus = asText(
    resultMap.status || nested?.status
  );
  if (parsed.status === "running") {
    status = "running";
  } else if (parsed.status === "failed" || resultStatus === "failed") {
    status = "failed";
  } else if (parsed.status === "completed") {
    status = "completed";
  } else if (
    task?.messageType === "tool_call" &&
    !resultMap.isFinal &&
    nested?.isFinal !== true &&
    resultStatus !== "success"
  ) {
    status = "running";
  } else if (resultStatus === "success" || resultMap.isFinal || nested?.isFinal === true) {
    status = parsed.content || observationText ? "completed" : "unknown";
  } else if (task?.messageType === "tool_call") {
    status = "running";
  }

  const progressLines = Array.isArray(resultMap.subAgentProgressLines)
    ? (resultMap.subAgentProgressLines as unknown[]).filter(
      (line): line is string => typeof line === "string" && line.trim().length > 0
    )
    : [];
  const liveText = asText(resultMap.subAgentLiveText);
  const elapsedMs =
    typeof resultMap.subAgentElapsedMs === "number"
      ? resultMap.subAgentElapsedMs
      : undefined;

  return {
    isAgent: isAgentDispatchTask(task),
    description,
    subagentType: parsed.agentType || subagentType,
    prompt,
    status,
    agentId: parsed.agentId,
    content: parsed.content,
    totalToolUseCount: parsed.totalToolUseCount,
    totalDurationMs: parsed.totalDurationMs,
    errorMsg: parsed.errorMsg,
    runInBackground: isRunInBackgroundAgent(task),
    liveText,
    progressLines,
    elapsedMs,
  };
}

export function formatSubAgentDuration(ms?: number) {
  if (ms == null || !Number.isFinite(ms) || ms < 0) {
    return "";
  }
  if (ms < 1000) {
    return `${Math.round(ms)}ms`;
  }
  if (ms < 60_000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  const minutes = Math.floor(ms / 60_000);
  const seconds = Math.round((ms % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
}

export function buildSubAgentAction(task: CHAT.Task) {
  const display = resolveSubAgentDisplay(task);
  if (display.status === "running") {
    return {
      action: "派发子智能体",
      tool: "Agent",
      name: display.description
        ? `${display.subagentType} · ${display.description}`
        : display.subagentType,
    };
  }
  if (display.status === "failed") {
    return {
      action: "子智能体失败",
      tool: "Agent",
      name: display.description
        ? `${display.subagentType} · ${display.description}`
        : display.subagentType,
    };
  }
  const metaParts: string[] = [display.subagentType];
  if (display.description) {
    metaParts.push(display.description);
  }
  if (display.totalToolUseCount != null) {
    metaParts.push(`${display.totalToolUseCount} tools`);
  }
  const duration = formatSubAgentDuration(display.totalDurationMs);
  if (duration) {
    metaParts.push(duration);
  }
  return {
    action: "子智能体完成",
    tool: "Agent",
    name: metaParts.join(" · "),
  };
}

export function buildSubAgentMarkdown(task?: CHAT.Task) {
  const display = resolveSubAgentDisplay(task);
  if (!display.isAgent) {
    return "";
  }

  const lines: string[] = [];
  lines.push(`### SubAgent · ${display.subagentType}`);
  if (display.description) {
    lines.push(`**任务**：${display.description}`);
  }
  if (display.status === "running") {
    lines.push("");
    lines.push("> 子智能体运行中，请稍候…");
    if (display.prompt) {
      lines.push("");
      lines.push("**指令**");
      lines.push("");
      lines.push(display.prompt);
    }
    return lines.join("\n");
  }

  const badges: string[] = [];
  if (display.status === "completed") {
    badges.push("状态：完成");
  }
  if (display.status === "failed") {
    badges.push("状态：失败");
  }
  if (display.agentId) {
    badges.push(`ID：\`${display.agentId}\``);
  }
  if (display.totalToolUseCount != null) {
    badges.push(`工具次数：${display.totalToolUseCount}`);
  }
  const duration = formatSubAgentDuration(display.totalDurationMs);
  if (duration) {
    badges.push(`耗时：${duration}`);
  }
  if (badges.length) {
    lines.push(badges.join(" · "));
  }

  if (display.errorMsg) {
    lines.push("");
    lines.push(`**错误**：${display.errorMsg}`);
  }

  if (display.content) {
    lines.push("");
    lines.push("---");
    lines.push("");
    lines.push(display.content);
  } else if (display.prompt) {
    lines.push("");
    lines.push("**指令**");
    lines.push("");
    lines.push(display.prompt);
  }

  return lines.join("\n");
}
