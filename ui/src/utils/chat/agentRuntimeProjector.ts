/**
 * multiAgent.tasks / chat.tasks → Kimi 对齐 TurnBlock / AgentMember 投影。
 * 只读派生，不改事实层。
 */

import {
  formatSubAgentDuration,
  isAgentDispatchTask,
  isRunInBackgroundAgent,
  resolveSubAgentDisplay,
} from "./subagent";
import { resolveTaskToolCallId } from "./toolCalls";
import {
  formatDurationLabel,
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
} from "@/components/Dialogue/tools/toolTaskAdapter";
import type {
  AgentMember,
  AgentPhase,
  ChatTurn,
  DockTaskItem,
  ToolCall,
  TurnBlock,
} from "@/types/agentRuntime";

function asText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function isUserBriefTask(tool?: CHAT.Task): boolean {
  if (!tool) return false;
  if (tool.messageType === "user_brief") return true;
  const toolName = asText(
    tool.toolResult?.toolName ||
      (tool.resultMap as Record<string, unknown> | undefined)?.toolName
  ).toLowerCase();
  return (
    toolName === "sendusermessage" ||
    toolName === "brief" ||
    toolName === "send_user_message"
  );
}

function resolveUserBriefText(tool: CHAT.Task): string {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  return asText(
    nested.message || resultMap.message || toolAny.message || tool.toolThought
  );
}

function flattenTimelineTasks(chat: CHAT.ChatItem): CHAT.Task[] {
  const groups = chat.tasks || [];
  const out: CHAT.Task[] = [];
  for (const group of groups) {
    for (const container of group || []) {
      const children = (container as CHAT.Task).children;
      if (Array.isArray(children) && children.length > 0) {
        out.push(...children.filter((task) => task.messageType !== "plan_mode_entered"));
      } else if (
        container &&
        (container as CHAT.Task).messageType &&
        (container as CHAT.Task).messageType !== "task" &&
        (container as CHAT.Task).messageType !== "plan_mode_entered"
      ) {
        out.push(container as CHAT.Task);
      }
    }
  }
  return out;
}

function flattenFactTasks(chat: CHAT.ChatItem): CHAT.Task[] {
  const groups = chat.multiAgent?.tasks || [];
  const out: CHAT.Task[] = [];
  for (const group of groups) {
    for (const task of group || []) {
      if (task.messageType !== "plan_mode_entered") {
        out.push(task as CHAT.Task);
      }
    }
  }
  return out;
}

export function taskToToolCall(task: CHAT.Task): ToolCall {
  const id =
    resolveTaskToolCallId(task) ||
    task.messageId ||
    task.id ||
    `${task.messageType || "tool"}-${task.messageTime || ""}`;
  const status = resolveTaskToolStatus(task);
  const timing = formatDurationLabel(
    resolveSubAgentDisplay(task).totalDurationMs
  );
  return {
    id,
    name: resolveTaskToolName(task),
    arg: resolveTaskToolArg(task),
    status,
    timing: timing || undefined,
    output: resolveTaskToolOutput(task),
    runInBackground: isRunInBackgroundAgent(task),
    sourceTaskId: task.id || task.messageId,
  };
}

function mapAgentPhase(
  status: ReturnType<typeof resolveSubAgentDisplay>["status"],
  resultMap?: MESSAGE.ResultMap
): AgentPhase {
  const phase = asText(
    (resultMap as Record<string, unknown> | undefined)?.subAgentPhase
  ).toLowerCase();
  if (
    phase === "queued" ||
    phase === "working" ||
    phase === "suspended" ||
    phase === "completed" ||
    phase === "failed"
  ) {
    return phase;
  }
  if (status === "running") return "working";
  if (status === "failed") return "failed";
  if (status === "completed") return "completed";
  return "queued";
}

export function projectAgentMember(task: CHAT.Task): AgentMember | null {
  if (!isAgentDispatchTask(task)) {
    return null;
  }
  const display = resolveSubAgentDisplay(task);
  const resultMap = (task.resultMap || {}) as Record<string, unknown>;
  const toolCallId =
    resolveTaskToolCallId(task) || task.messageId || task.id || "";
  const progressLines = Array.isArray(resultMap.subAgentProgressLines)
    ? (resultMap.subAgentProgressLines as unknown[]).filter(
        (line): line is string => typeof line === "string"
      )
    : [];
  const liveText = asText(resultMap.subAgentLiveText);
  const status =
    display.status === "running"
      ? "running"
      : display.status === "failed"
        ? "failed"
        : display.status === "completed"
          ? "completed"
          : "running";

  return {
    id: display.agentId || toolCallId,
    toolCallId: toolCallId || undefined,
    name: display.description || display.subagentType || "Agent",
    subagentType: display.subagentType,
    phase: mapAgentPhase(display.status, task.resultMap),
    status,
    prompt: display.prompt || undefined,
    summary: display.description || undefined,
    outputLines: progressLines.length ? progressLines : undefined,
    text: liveText || display.content || undefined,
    runInBackground: isRunInBackgroundAgent(task),
    elapsedMs:
      typeof resultMap.subAgentElapsedMs === "number"
        ? resultMap.subAgentElapsedMs
        : undefined,
    totalToolUseCount: display.totalToolUseCount,
    totalDurationMs: display.totalDurationMs,
    errorMsg: display.errorMsg || undefined,
  };
}

function isThinkingTask(task: CHAT.Task): boolean {
  return (
    task.messageType === "llm_reasoning" || task.messageType === "plan_thought"
  );
}

function isAssistantTextTask(task: CHAT.Task): boolean {
  if (isUserBriefTask(task)) {
    return true;
  }
  return (
    task.messageType === "tool_thought" ||
    task.messageType === "agent_stream" ||
    task.messageType === "result"
  );
}

function resolveThinkingText(task: CHAT.Task): string {
  return asText(
    task.toolThought ||
      task.planThought ||
      task.resultMap?.answer ||
      (task.resultMap as Record<string, unknown> | undefined)?.data
  );
}

function resolveAssistantText(task: CHAT.Task): string {
  if (isUserBriefTask(task)) {
    return resolveUserBriefText(task);
  }
  return asText(
    task.toolThought ||
      task.result ||
      task.resultMap?.answer ||
      task.resultMap?.taskSummary ||
      (task.resultMap as Record<string, unknown> | undefined)?.data
  );
}

function isToolishTask(task: CHAT.Task): boolean {
  if (isThinkingTask(task) || isAssistantTextTask(task)) {
    return false;
  }
  const type = task.messageType || "";
  if (
    type === "tool_call" ||
    type === "tool_result" ||
    type === "ask_user_question" ||
    type === "plan_approval" ||
    type === "browser" ||
    type === "code" ||
    type === "html" ||
    type === "markdown" ||
    type === "file" ||
    type === "deep_search" ||
    type === "ppt" ||
    type === "knowledge" ||
    type === "data_analysis" ||
    type === "ui_tree" ||
    type === "session_tasks"
  ) {
    return true;
  }
  return Boolean(resolveTaskToolCallId(task) || task.toolResult?.toolName);
}

/**
 * 从派生时间线（chat.tasks）投影有序 TurnBlock。
 * 规则：保持事件到达顺序；thinking / text / tool 交错，不把 tools 提到文末。
 */
export function projectTurnBlocks(chat: CHAT.ChatItem): TurnBlock[] {
  const tasks = flattenTimelineTasks(chat);
  const blocks: TurnBlock[] = [];

  for (const task of tasks) {
    if (isThinkingTask(task)) {
      const thinking = resolveThinkingText(task);
      if (thinking) {
        blocks.push({ kind: "thinking", thinking });
      }
      continue;
    }
    if (isAssistantTextTask(task) && !isToolishTask(task)) {
      const text = resolveAssistantText(task);
      if (text) {
        blocks.push({ kind: "text", text });
      }
      continue;
    }
    if (isToolishTask(task)) {
      // 后台子 Agent：主时间线仍可保留卡，但标记 runInBackground 供 Dock 消费
      blocks.push({ kind: "tool", tool: taskToToolCall(task) });
    }
  }

  return blocks;
}

export function projectAssistantChatTurn(
  chat: CHAT.ChatItem,
  options?: { id?: string; no?: number }
): ChatTurn {
  const blocks = projectTurnBlocks(chat);
  const thinkingParts = blocks
    .filter((b): b is Extract<TurnBlock, { kind: "thinking" }> => b.kind === "thinking")
    .map((b) => b.thinking);
  const textParts = blocks
    .filter((b): b is Extract<TurnBlock, { kind: "text" }> => b.kind === "text")
    .map((b) => b.text);
  const tools = blocks
    .filter((b): b is Extract<TurnBlock, { kind: "tool" }> => b.kind === "tool")
    .map((b) => b.tool);

  return {
    id: options?.id || chat.requestId || chat.sessionId || "turn",
    role: "assistant",
    no: options?.no ?? 1,
    text: textParts.join("\n\n"),
    thinking: thinkingParts.length ? thinkingParts.join("\n\n") : undefined,
    tools: tools.length ? tools : undefined,
    blocks,
  };
}

/** 按 toolCallId 取子 Agent 详情投影（右侧 AgentDetailPanel） */
export function projectAgentMemberByToolCallId(
  chat: CHAT.ChatItem,
  toolCallId: string
): AgentMember | null {
  if (!toolCallId) {
    return null;
  }
  const candidates = [
    ...flattenTimelineTasks(chat),
    ...flattenFactTasks(chat),
  ];
  for (const task of candidates) {
    if (!isAgentDispatchTask(task)) {
      continue;
    }
    const id = resolveTaskToolCallId(task) || task.messageId || task.id;
    if (id === toolCallId) {
      return projectAgentMember(task);
    }
    if (Array.isArray(task.children)) {
      for (const child of task.children) {
        if (!isAgentDispatchTask(child)) continue;
        const childId =
          resolveTaskToolCallId(child) || child.messageId || child.id;
        if (childId === toolCallId) {
          return projectAgentMember(child);
        }
      }
    }
  }
  return null;
}

/**
 * Dock 后台任务：run_in_background 的 Agent，以及将来可扩展的后台 bash。
 */
export function projectDockTasks(chat: CHAT.ChatItem): DockTaskItem[] {
  const seen = new Set<string>();
  const items: DockTaskItem[] = [];
  const candidates = [
    ...flattenTimelineTasks(chat),
    ...flattenFactTasks(chat),
  ];

  for (const task of candidates) {
    if (!isAgentDispatchTask(task) || !isRunInBackgroundAgent(task)) {
      continue;
    }
    const id =
      resolveTaskToolCallId(task) || task.messageId || task.id || "";
    if (!id || seen.has(id)) {
      continue;
    }
    seen.add(id);
    const display = resolveSubAgentDisplay(task);
    const state =
      display.status === "running"
        ? "run"
        : display.status === "failed"
          ? "fail"
          : "done";
    items.push({
      id,
      name: display.description || display.subagentType || "Agent",
      kind: "subagent",
      state,
      timing: formatSubAgentDuration(display.totalDurationMs) || "",
      meta: display.subagentType,
      output: display.content ? display.content.split("\n") : undefined,
      runInBackground: true,
      parentToolCallId: id,
    });
  }

  return items;
}

/** 调试/单测：比较两轮投影的块序列签名 */
export function turnBlockSignature(blocks: TurnBlock[]): string[] {
  return blocks.map((block) => {
    if (block.kind === "thinking") {
      return `thinking:${block.thinking.length}`;
    }
    if (block.kind === "text") {
      return `text:${block.text.length}`;
    }
    return `tool:${block.tool.name}:${block.tool.id}:${block.tool.status}`;
  });
}
