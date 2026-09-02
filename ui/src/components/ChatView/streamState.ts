import {
  resolveChapterSummary,
  resolveDeepSearchStage,
  shouldRenderDeepSearchWorkspace,
} from "@/utils/deepSearch";
import { buildAction } from "@/utils/chat";
import { isAgentDispatchTask } from "@/utils/chat/subagent";
import {
  resolveTaskResultMap,
  resolveTaskToolResult,
  resolveTaskToolResultText,
} from "@/utils/chat/toolCalls";
import { getTaskFiles, isFileListOnlyTask } from "@/utils/taskArtifacts";
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
  "plan_approval",
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
 * tool_call / 纯 tool_result 不自动抢焦点，但时间线点击后仍可查看结果。
 * ask_user_question / plan_approval 交互在底部 Dock，不抢右侧工作区。
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

function resolveTaskToolName(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  return String(
    resolveTaskToolResult(task)?.toolName ||
      resolveTaskResultMap(task).toolName ||
      ""
  ).toLowerCase();
}

/** canvas_publish 实时是 tool_call，历史回放是 html；两者都应打开右侧预览。 */
export function isCanvasPublishPreviewTask(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task) {
    return false;
  }
  if (task.messageType === "html") {
    return true;
  }
  return resolveTaskToolName(task) === "canvas_publish";
}

export function isWorkspaceRenderableTask(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task) {
    return false;
  }

  if (isFileListOnlyTask(task)) {
    return false;
  }

  if (WORKSPACE_HIDDEN_MESSAGE_TYPES.has(task.messageType || "")) {
    return false;
  }

  if (task.messageType === "deep_search") {
    return shouldRenderDeepSearchWorkspace(resolveTaskResultMap(task).messageType);
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
  if (messageType === "deep_search") {
    return true;
  }

  if (isCanvasPublishPreviewTask(task)) {
    return hasWorkspaceArtifacts(task);
  }

  if (
    WORKSPACE_ATTENTION_MESSAGE_TYPES.has(messageType) ||
    messageType === "tool_result"
  ) {
    return hasWorkspaceArtifacts(task);
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
    if (isCanvasPublishPreviewTask(task) && hasWorkspaceArtifacts(task)) {
      return false;
    }
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

  const toolName = String(
    resolveTaskToolResult(task)?.toolName ||
      resolveTaskResultMap(task).toolName ||
      ""
  ).toLowerCase();
  if (
    toolName === "internal_search" ||
    toolName === "web_search" ||
    toolName === "websearch" ||
    toolName === "search"
  ) {
    return false;
  }

  // Agent 派发结果：右侧不渲染 JSON 观察值，禁止点开空白面板。
  if (isAgentDispatchTask(task as CHAT.Task)) {
    return true;
  }

  const toolResultText = resolveTaskToolResultText(task);
  // 无文本、或纯 JSON 结构化出参：前端不渲染，禁止打开空白面板
  if (!toolResultText.trim()) {
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
  if (isFileListOnlyTask({
    messageType: innerType,
    resultMap: eventData.resultMap,
    artifactRefs: eventData.artifactRefs,
  } as unknown as CHAT.Task)) {
    return false;
  }

  if (innerType && WORKSPACE_HIDDEN_MESSAGE_TYPES.has(innerType)) {
    return false;
  }

  if (innerType === "tool_call") {
    return isCanvasPublishPreviewTask({
      messageType: "tool_call",
      artifactRefs: eventData.artifactRefs,
      resultMap: eventData.resultMap,
    } as unknown as CHAT.Task) && hasWorkspaceArtifacts({
      messageType: "tool_call",
      artifactRefs: eventData.artifactRefs,
      resultMap: eventData.resultMap,
    } as unknown as CHAT.Task);
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
  if (isFileListOnlyTask(tool)) {
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
  if (tool.messageType === "deep_search") {
    const resultMap = resolveTaskResultMap(tool);
    const stage = resolveDeepSearchStage(resultMap.messageType);
    if (stage === "report") {
      return !isTaskFinal(tool);
    }
    if (stage === "extend") {
      return true;
    }
    if (resultMap.chapterStreaming) {
      return true;
    }
    if (stage === "chapter_summary") {
      return false;
    }
    return !resolveChapterSummary(resultMap);
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
  const isUiTask = (task: { messageType?: string }) =>
    task.messageType !== "plan_mode_entered" && !isFileListOnlyTask(task as CHAT.Task);
  const fromRendered = (chat.tasks || []).flatMap((group) =>
    (group || [])
      .flatMap((container) => container.children || [])
      .filter(isUiTask)
  );
  if (fromRendered.length) {
    return fromRendered;
  }
  return (chat.multiAgent?.tasks || [])
    .flatMap((group) => group || [])
    .filter(isUiTask) as CHAT.Task[];
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

  if (status === "WAITING_INPUT" || hasPendingAskUserQuestion(chat)) {
    return {
      phase: "idle",
      hint: WAITING_USER_HELP_HINT,
      attention: "workspace",
      workspaceTitle: attentionTask ? workspaceTitle : undefined,
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
    hint: resolveRunPhaseHint(phase, {
      deepThink,
      workspaceTitle,
    }),
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

/** AskUserQuestion 挂起：顶栏 / tip 文案 */
export const WAITING_USER_HELP_HINT = "需要你的帮助";

function readAskUserStatus(
  tool?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
): string {
  if (!tool) {
    return "";
  }
  const resultMap = (tool.resultMap || {}) as unknown as Record<string, unknown>;
  const nested = (resultMap.resultMap || resultMap) as Record<string, unknown>;
  return String(nested.status || resultMap.status || "").trim().toLowerCase();
}

/** 会话是否仍有未决 HITL（AskUserQuestion / PlanApproval） */
export function hasPendingAskUserQuestion(chat?: CHAT.ChatItem | null): boolean {
  if (!chat) {
    return false;
  }
  const runStatus = String(chat.metrics?.status || "").toUpperCase();
  // 续跑中 / 已成功时不再因历史 WAITING 标记误判
  if (runStatus === "RUNNING" || runStatus === "SUCCESS" || runStatus === "FAILED") {
    // fall through to card status only
  } else if (runStatus === "WAITING_INPUT") {
    return true;
  }
  const groups = chat.multiAgent?.tasks || chat.tasks || [];
  for (const group of groups) {
    for (const tool of group || []) {
      if (
        tool?.messageType !== "ask_user_question" &&
        tool?.messageType !== "plan_approval"
      ) {
        continue;
      }
      const status = readAskUserStatus(tool);
      if (status === "pending") {
        return true;
      }
      if (status) {
        continue;
      }
      // 缺 status：已 finish/isFinal 视为已决，避免续跑收尾后误判回 WAITING
      const resultMap = (tool.resultMap || {}) as unknown as Record<
        string,
        unknown
      >;
      const nested = (resultMap.resultMap || resultMap) as Record<
        string,
        unknown
      >;
      const settled = Boolean(
        tool.finish ||
          tool.isFinal ||
          resultMap.isFinal === true ||
          nested.isFinal === true
      );
      if (!settled) {
        return true;
      }
    }
  }
  return false;
}

/** 用户已提交答案后，把本地卡片标成 answered，避免 UI 仍停在等待态 */
export function markAskUserQuestionsAnswered(
  chat: CHAT.ChatItem,
  questionId?: string,
  answers?: Record<string, string>
): CHAT.ChatItem {
  const tasks = (chat.multiAgent?.tasks || []).map((group) =>
    (group || []).map((tool) => {
      if (tool?.messageType !== "ask_user_question") {
        return tool;
      }
      const prevMap = (tool.resultMap || {}) as Record<string, unknown>;
      const nestedMap = (prevMap.resultMap as Record<string, unknown> | undefined) || {};
      const currentQuestionId = String(
        nestedMap.questionId || prevMap.questionId || tool.messageId || ""
      );
      if (questionId && currentQuestionId && currentQuestionId !== questionId) {
        return tool;
      }
      const nested = {
        ...nestedMap,
        status: "answered",
        ...(answers ? { answers } : {}),
      };
      return {
        ...tool,
        finish: true,
        isFinal: true,
        resultMap: {
          ...prevMap,
          status: "answered",
          isFinal: true,
          ...(answers ? { answers } : {}),
          resultMap: nested,
        },
      } as CHAT.Task;
    })
  );
  return {
    ...chat,
    multiAgent: {
      ...(chat.multiAgent || { tasks: [] }),
      tasks: tasks as unknown as MESSAGE.Task[][],
    },
  };
}

/** 用户已提交计划审批后，把本地卡片标成 decided */
export function markPlanApprovalsDecided(
  chat: CHAT.ChatItem,
  approvalId?: string,
  _answers?: Record<string, string>,
  approved?: boolean
): CHAT.ChatItem {
  const tasks = (chat.multiAgent?.tasks || []).map((group) =>
    (group || []).map((tool) => {
      if (tool?.messageType !== "plan_approval") {
        return tool;
      }
      const prevMap = (tool.resultMap || {}) as Record<string, unknown>;
      const nestedMap = (prevMap.resultMap || {}) as Record<string, unknown>;
      const currentApprovalId = String(
        nestedMap.approvalId || prevMap.approvalId || tool.messageId || ""
      );
      if (approvalId && currentApprovalId !== approvalId) {
        return tool;
      }
      const nested = {
        ...nestedMap,
        status: "decided",
        ...(typeof approved === "boolean" ? { approved } : {}),
      };
      return {
        ...tool,
        finish: true,
        isFinal: true,
        resultMap: {
          ...prevMap,
          status: "decided",
          isFinal: true,
          ...(typeof approved === "boolean" ? { approved } : {}),
          resultMap: nested,
        },
      } as CHAT.Task;
    })
  );
  return {
    ...chat,
    multiAgent: {
      ...(chat.multiAgent || { tasks: [] }),
      tasks: tasks as unknown as MESSAGE.Task[][],
    },
  };
}

/** 进入等待用户输入终态：不 loading、无停止、tip=需要你的帮助 */
export function applyWaitingUserInputState(chat: CHAT.ChatItem): CHAT.ChatItem {
  return {
    ...chat,
    loading: false,
    tip: WAITING_USER_HELP_HINT,
    metrics: {
      ...(chat.metrics || {}),
      status: "WAITING_INPUT",
    },
  };
}
