import { shouldRenderDeepSearchWorkspace } from "@/utils/deepSearch";
import { buildAction } from "@/utils/chat";
import { isAgentDispatchTask } from "@/utils/chat/subagent";
import { getTaskFiles } from "@/utils/taskArtifacts";
import { isValidJSON } from "@/utils";
import type { ActiveRunState } from "./chatView.types";

export type RunPhase =
  | "idle"
  | "queued"
  | "thinking"
  | "planning"
  | "working"
  | "crafting"
  | "settling"
  | "failed"
  | "stopped";

export type RunAttention = "timeline" | "workspace" | "composer";

export type RunPresence = {
  phase: RunPhase;
  hint: string;
  attention: RunAttention;
  workspaceTitle?: string;
};

const WORKSPACE_HIDDEN_MESSAGE_TYPES = new Set([
  "task_summary",
  "result",
  "tool_thought",
  "llm_reasoning",
  // tool_thought = 过程回复；llm_reasoning = 原生 CoT
  "llm_retry",
  // 子 Agent 心跳，不进工作区/时间线注意力
  "subagent_progress",
  // 仅状态条提示，不进工作区
  "ask_user_question",
  "plan_approval",
  "plan_mode_entered",
  "session_tasks",
  "user_brief",
  "ui_patch",
  // GenUI 只在对话主回复区展示，不进工作区
  "ui_tree",
  // 上下文占用仅驱动 ContextRing
  "context_usage",
]);

/**
 * 只有真正“值得抢焦点”的产物才自动打开右侧工作区。
 * tool_call / 纯 tool_result 只留在时间线，避免每一步工具都把双栏拉开。
 */
const WORKSPACE_ATTENTION_MESSAGE_TYPES = new Set([
  "file",
  "html",
  "markdown",
  "ppt",
  "code",
  "browser",
  "knowledge",
  "data_analysis",
  "deep_search",
]);

const CRAFTING_MESSAGE_TYPES = new Set([
  "file",
  "html",
  "markdown",
  "ppt",
  "code",
  "data_analysis",
  "deep_search",
]);

function isTaskFinal(task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>) {
  if (!task) {
    return false;
  }
  return Boolean(task.finish || task.isFinal || task.resultMap?.isFinal);
}

function hasWorkspaceArtifacts(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task) {
    return false;
  }
  if (Array.isArray(task.artifactRefs) && task.artifactRefs.length > 0) {
    return true;
  }
  try {
    return getTaskFiles(task as CHAT.Task).length > 0;
  } catch {
    return false;
  }
}

export function isWorkspaceRenderableTask(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task) {
    return false;
  }

  if (WORKSPACE_HIDDEN_MESSAGE_TYPES.has(task.messageType || "")) {
    return false;
  }

  if (task.messageType === "deep_search") {
    return shouldRenderDeepSearchWorkspace(task.resultMap?.messageType);
  }

  return true;
}

/**
 * 自动打开 / 跟随工作区的注意力过滤：产物优先，工具调用噪声排除。
 */
export function isWorkspaceAttentionTask(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!isWorkspaceRenderableTask(task)) {
    return false;
  }

  const messageType = task?.messageType || "";
  if (WORKSPACE_ATTENTION_MESSAGE_TYPES.has(messageType)) {
    return true;
  }

  if (messageType === "tool_result" && hasWorkspaceArtifacts(task)) {
    return true;
  }

  return false;
}

/**
 * Structured data 入参/出参已隐藏：这类任务点击时间线时也不展开右侧面板。
 */
export function isStructuredDataOnlyTask(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task) {
    return false;
  }

  const messageType = task.messageType || "";
  if (messageType === "tool_call") {
    // 子智能体卡片本身也不打开右侧：嵌套工具在时间线展开查看，
    // 否则 JSON 出参隐藏后会弹出空白工作区。
    return true;
  }

  if (messageType !== "tool_result") {
    return false;
  }

  if (hasWorkspaceArtifacts(task)) {
    return false;
  }

  const toolName =
    (task as CHAT.Task).toolResult?.toolName ||
    (task.resultMap as { toolName?: string } | undefined)?.toolName ||
    "";
  if (toolName === "internal_search" || toolName === "web_search") {
    return false;
  }

  // Agent 派发结果：右侧当前会把 JSON 观察值收成 empty，禁止点开空白面板。
  // 子工具列表请在时间线嵌套卡片中查看。
  if (isAgentDispatchTask(task as CHAT.Task)) {
    return true;
  }

  const toolResultText = (task as CHAT.Task).toolResult?.toolResult;
  // 无文本、或纯 JSON 结构化出参：前端暂不渲染，禁止打开空白面板
  if (!toolResultText?.trim()) {
    return true;
  }
  return isValidJSON(toolResultText);
}

/**
 * 时间线点击是否允许打开右侧工作区。
 * plan 等特殊类型由调用方单独处理。
 */
export function canOpenTaskWorkspacePanel(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task) {
    return false;
  }
  if (!isWorkspaceRenderableTask(task)) {
    return false;
  }
  return !isStructuredDataOnlyTask(task);
}

export function shouldRefreshWorkspaceTask(eventData?: MESSAGE.EventData) {
  if (!eventData) {
    return false;
  }

  // 最终总结流和思考流不属于右侧工作区内容，不要触发工作区跟随刷新。
  if (eventData.messageType === "plan_thought") {
    return false;
  }

  if (
    eventData.messageType === "task" &&
    ["agent_stream", "tool_thought"].includes(
      eventData.resultMap?.messageType || ""
    )
  ) {
    return false;
  }

  const innerType = eventData.resultMap?.messageType || eventData.messageType;
  if (innerType && WORKSPACE_HIDDEN_MESSAGE_TYPES.has(innerType)) {
    return false;
  }

  if (innerType === "tool_call") {
    return false;
  }

  if (innerType === "tool_result") {
    return hasWorkspaceArtifacts({
      messageType: "tool_result",
      artifactRefs: eventData.artifactRefs,
      resultMap: eventData.resultMap,
    } as unknown as CHAT.Task);
  }

  if (innerType && WORKSPACE_ATTENTION_MESSAGE_TYPES.has(innerType)) {
    return true;
  }

  // plan 本身不自动抢工作区焦点（左侧 PlanSection 已承接）。
  if (eventData.messageType === "plan" || innerType === "plan") {
    return false;
  }

  return isWorkspaceAttentionTask({
    messageType: innerType,
    resultMap: eventData.resultMap,
    artifactRefs: eventData.artifactRefs,
  } as unknown as CHAT.Task);
}

export function getLatestRenderableTask(chat: CHAT.ChatItem): CHAT.Task | undefined {
  const groups = chat.multiAgent?.tasks || [];
  for (let groupIndex = groups.length - 1; groupIndex >= 0; groupIndex -= 1) {
    const group = groups[groupIndex] || [];
    for (let taskIndex = group.length - 1; taskIndex >= 0; taskIndex -= 1) {
      const task = group[taskIndex] as CHAT.Task | undefined;
      if (!isWorkspaceAttentionTask(task)) {
        continue;
      }
      return task;
    }
  }
  return undefined;
}

export function cloneWorkspaceTask(task: CHAT.Task): CHAT.Task {
  return {
    ...task,
    resultMap: task.resultMap ? { ...task.resultMap } : task.resultMap,
  } as CHAT.Task;
}

export function resolveActionPanelVisibility(params: {
  plan?: CHAT.Plan;
  taskList: CHAT.Task[];
}) {
  // 右侧工作区只在有值得观看的产物时自动展开；plan 单独出现时保持单栏。
  void params.plan;
  return params.taskList.some((task) => isWorkspaceAttentionTask(task));
}

export function resolveLatestRunState(
  chat?: Pick<CHAT.ChatItem, "metrics" | "finishedAt">
): ActiveRunState | undefined {
  if (!chat) {
    return undefined;
  }

  return {
    status: chat.metrics?.status,
    finishedAt: chat.finishedAt,
  };
}

export function isTimelineToolActive(tool?: CHAT.Task) {
  if (!tool) {
    return false;
  }
  // 终答/总结类永不作为“进行中”闪动
  if (
    tool.messageType === "task_summary" ||
    tool.messageType === "result" ||
    tool.messageType === "session_tasks"
  ) {
    return false;
  }
  if (tool.messageType === "ask_user_question" || tool.messageType === "plan_approval") {
    return !isTaskFinal(tool);
  }
  if (tool.messageType === "tool_thought") {
    return !isTaskFinal(tool);
  }
  if (tool.messageType === "llm_reasoning" || tool.messageType === "plan_thought") {
    return !isTaskFinal(tool);
  }
  if (tool.messageType === "tool_call") {
    return !isTaskFinal(tool) && tool.resultMap?.status !== "success";
  }
  if (CRAFTING_MESSAGE_TYPES.has(tool.messageType || "")) {
    return !isTaskFinal(tool);
  }
  return !isTaskFinal(tool);
}

function collectTimelineTools(chat?: CHAT.ChatItem): CHAT.Task[] {
  if (!chat) {
    return [];
  }
  const fromRendered = (chat.tasks || []).flatMap((group) =>
    (group || []).flatMap((container) => container.children || [])
  );
  if (fromRendered.length) {
    return fromRendered;
  }
  return (chat.multiAgent?.tasks || []).flatMap((group) => group || []) as CHAT.Task[];
}

function resolveWorkspaceTitle(task?: CHAT.Task) {
  if (!task) {
    return undefined;
  }
  try {
    const action = buildAction(task);
    const label = [action.action, action.name].filter(Boolean).join(" · ");
    return label || task.messageType;
  } catch {
    return task.messageType;
  }
}

const PHASE_HINTS: Record<RunPhase, string> = {
  idle: "",
  queued: "正在理解问题…",
  thinking: "正在梳理思路…",
  planning: "正在制定计划…",
  working: "正在推进任务…",
  crafting: "正在整理产出…",
  settling: "正在汇总结果…",
  failed: "本轮未能完成",
  stopped: "已停止",
};

export function resolveRunPhaseHint(
  phase: RunPhase,
  options?: { deepThink?: boolean; workspaceTitle?: string }
) {
  if (phase === "queued" && options?.deepThink) {
    return "正在制定计划…";
  }
  if (phase === "crafting" && options?.workspaceTitle) {
    return options.workspaceTitle;
  }
  if (phase === "working" && options?.workspaceTitle) {
    return options.workspaceTitle;
  }
  return PHASE_HINTS[phase];
}

export function resolveRunPresence(params: {
  loading: boolean;
  chat?: CHAT.ChatItem;
  deepThink?: boolean;
  plan?: CHAT.Plan;
  taskList?: CHAT.Task[];
}): RunPresence {
  const { loading, chat, deepThink, plan, taskList } = params;
  const status = String(chat?.metrics?.status || "").toUpperCase();
  const attentionTask =
    (taskList || []).find((task) => isWorkspaceAttentionTask(task) && isTimelineToolActive(task)) ||
    (chat ? getLatestRenderableTask(chat) : undefined);
  const workspaceTitle = resolveWorkspaceTitle(attentionTask);

  if (status === "FAILED") {
    return {
      phase: "failed",
      hint: chat?.tip || PHASE_HINTS.failed,
      attention: "timeline",
    };
  }

  if (status === "STOPPED" || chat?.forceStop) {
    return {
      phase: "stopped",
      hint: PHASE_HINTS.stopped,
      attention: "timeline",
    };
  }

  if (!loading) {
    return {
      phase: "idle",
      hint: "",
      attention: "composer",
      workspaceTitle: attentionTask ? workspaceTitle : undefined,
    };
  }

  const tools = collectTimelineTools(chat);
  const hasThought = Boolean(
    chat?.thought?.trim() ||
      chat?.multiAgent?.plan_thought?.trim()
  );
  const hasPlan = Boolean(plan || chat?.plan || chat?.multiAgent?.plan);
  const hasActiveTool = tools.some((tool) => isTimelineToolActive(tool));
  const hasCrafting = tools.some(
    (tool) =>
      CRAFTING_MESSAGE_TYPES.has(tool.messageType || "") ||
      isWorkspaceAttentionTask(tool)
  );
  const conclusionStreaming =
    chat?.conclusion?.messageType === "agent_stream" ||
    (Boolean(chat?.conclusion) && !isTaskFinal(chat?.conclusion));

  let phase: RunPhase = "queued";
  if (conclusionStreaming || (chat?.conclusion && loading)) {
    phase = "settling";
  } else if (
    attentionTask &&
    CRAFTING_MESSAGE_TYPES.has(attentionTask.messageType || "")
  ) {
    phase = "crafting";
  } else if (hasCrafting && hasActiveTool) {
    phase = "crafting";
  } else if (hasActiveTool || tools.length > 0) {
    phase = "working";
  } else if (hasPlan && deepThink) {
    phase = "planning";
  } else if (hasThought) {
    phase = "thinking";
  } else if (deepThink) {
    // 深度研究首包前仍属 queued，文案走“制定计划”
    phase = "queued";
  }

  const attention: RunAttention =
    phase === "crafting" && attentionTask
      ? "workspace"
      : phase === "settling"
        ? "timeline"
        : "timeline";

  return {
    phase,
    hint: resolveRunPhaseHint(phase, { deepThink, workspaceTitle }),
    attention,
    workspaceTitle,
  };
}

export function resolveWorkspaceCaption(task?: CHAT.Task, loading?: boolean) {
  if (!task) {
    return undefined;
  }
  const title = resolveWorkspaceTitle(task);
  if (!title) {
    return undefined;
  }
  if (loading && isTimelineToolActive(task)) {
    return `正在产出：${title}`;
  }
  return title;
}
