import {
  getPrimaryTaskFile,
  getPrimaryTaskFileName,
  isFileListOnlyTask,
  normalizeTaskFile,
} from "@/utils/taskArtifacts";
import {
  formatDeepSearchQueryText,
  isDeepSearchStage,
  resolveDeepSearchActionText,
  resolveDeepSearchStage,
} from "@/utils/deepSearch";
import { parseEventData } from "@/utils/sseParsers";
import {
  handlePlanMessage,
  handlePlanThoughtMessage,
} from "./chat/planner";
import {
  clonePlanForRender,
  cloneTaskSnapshot,
  processTaskForRender,
} from "./chat/renderTasks";
import {
  ensureTimelineTaskContainer,
  ensureTimelineTaskGroup,
  upsertTimelineTaskContainer,
  type TimelineTaskContainer,
} from "./chat/timeline";
import {
  identityKeys,
  identityRank,
  isDistinctToolCallId,
  pickBestTaskByKey,
  readTaskIdentity,
} from "./chat/taskIdentity";
import {
  findLastTaskIndex,
  findTaskIndexByToolCallId,
  findToolCallPlaceholderIndex,
  isImageGenerationFileTask,
  isImageGenerationToolResultTask,
  mergeHtmlPreviewIntoToolCall,
  mergeImageGenerationToolTask,
  mergeTaskArtifactRefs,
  resolveTaskToolCallId,
  resolveTaskToolResult,
  resolveToolCallActionText,
  resolveToolCallArgumentsText,
  resolveToolCallInput,
  resolveToolCallStreamKey,
  resolveToolCallTargetName,
} from "./chat/toolCalls";
import { ensureOriginalTree, mergeUiPatchIntoTasks } from "@/utils/chat/genuiState";
import {
  AGENT_DISPATCH_TOOL_NAME,
  buildSubAgentAction,
  isAgentDispatchTask,
  resolveParentToolUseId,
} from "./chat/subagent";
import type { SubAgentProgressKind } from "@/types/agentRuntime";

type NestedTaskResultMap = MESSAGE.ResultMap & {
  resultMap?: MESSAGE.ResultMap;
};

function toNestedResultMap(resultMap?: MESSAGE.ResultMap): NestedTaskResultMap {
  return (resultMap || {}) as NestedTaskResultMap;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function normalizeEventData(eventData: unknown): MESSAGE.EventData | undefined {
  try {
    return parseEventData(eventData);
  } catch (error) {
    console.warn("忽略无法识别的 SSE eventData", error);
    return undefined;
  }
}

export const combineData = (
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) => {
  // 所有实时与历史事件都从这里进入状态模型；具体 messageType 处理分散在小函数中，避免两套回放逻辑漂移。
  const innerType = eventData.resultMap?.messageType || eventData.messageType;
  if (innerType === "context_usage") {
    applyContextUsage(eventData, currentChat);
    return currentChat;
  }
  switch (eventData.messageType) {
    case "plan": {
      handlePlanMessage(eventData, currentChat);
      break;
    }
    case "plan_thought": {
      handlePlanThoughtMessage(eventData, currentChat);
      break;
    }
    case "task": {
      handleTaskMessage(eventData, currentChat);
      break;
    }
    default:
      break;
  }
  return currentChat;
};

/** 从 SSE resultMap 提取上下文占用，供 ContextRing 使用（不进任务时间线）。 */
function applyContextUsage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  // context_usage 经 BaseAgentResponseHandler default 分支会再包一层 resultMap，
  // 同时兼容扁平结构（历史/测试）与嵌套结构（实时 SSE）。
  const outer = (eventData.resultMap || {}) as unknown as Record<string, unknown>;
  const map: Record<string, unknown> = { ...outer };
  let current = outer;
  // 实时链路可能同时经过 AgentSessionPrinter 和响应 handler，resultMap 会被包两层。
  // 逐层合并，避免能识别事件类型但取不到实际 token 数值。
  for (let depth = 0; depth < 4; depth += 1) {
    if (!isRecord(current.resultMap)) {
      break;
    }
    current = current.resultMap;
    Object.assign(map, current);
  }
  const num = (k: string) => {
    const v = map[k];
    return typeof v === "number" && Number.isFinite(v) ? v : 0;
  };
  const promptTokens = map.promptTokens;
  if (
    typeof promptTokens !== "number" ||
    !Number.isFinite(promptTokens) ||
    promptTokens < 0
  ) {
    // 请求开始时的 estimate 事件不能覆盖上一轮已经确认的真实快照。
    return;
  }
  currentChat.contextUsage = {
    max: Math.max(1, num("max") || 100000),
    promptTokens,
  };
}

/**
 * 实时 SSE 的文件类事件会把 artifactRefs 放在 eventData 顶层，
 * 这里统一折叠进任务对象，保证工作区始终走同一套取文件逻辑。
 */
export function buildTaskFromEventData(eventData: MESSAGE.EventData): MESSAGE.Task {
  const artifactRefs = Array.isArray(eventData.artifactRefs)
    ? [...eventData.artifactRefs]
    : undefined;

  const task = {
    taskId: eventData.taskId,
    ...(artifactRefs?.length ? { artifactRefs } : {}),
    ...eventData.resultMap,
  } as MESSAGE.Task;
  const toolResult = resolveTaskToolResult(task);
  if (toolResult && !task.toolResult) {
    task.toolResult = toolResult as MESSAGE.ToolResult;
  }
  return task;
}

/**
 * deep_search 的 search/report 可能复用同一个 messageId，
 * 工作区同步时优先使用前端派生的 render id，避免不同卡片互相串位。
 */
export function getStableTaskIdentity(
  task?: Partial<CHAT.Task> | Partial<MESSAGE.Task>
) {
  if (!task) {
    return "";
  }

  return (
    task.id ||
    task.messageId ||
    (task.taskId && task.messageTime ? `${task.taskId}:${task.messageTime}` : "") ||
    ""
  );
}

/**
 * 处理任务类型的消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天对象
 */
function handleTaskMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  if (!currentChat.multiAgent.tasks) {
    currentChat.multiAgent.tasks = [];
  }
  const taskIndex = findTaskIndex(currentChat.multiAgent.tasks, eventData.taskId);
  if (eventData.resultMap?.messageType) {
    handleTaskMessageByType(eventData, currentChat, taskIndex);
  }
}

/**
 * 查找工具在指定任务中的索引
 * @param tasks 任务数组
 * @param taskIndex 任务索引
 * @param messageId 消息ID
 * @returns 工具索引，如果未找到则返回-1
 */
function findToolIndex(
  tasks: MESSAGE.Task[][],
  taskIndex: number,
  messageId: string | undefined,
  messageType: string | undefined
): number {
  if (taskIndex === -1) return -1;

  return tasks[taskIndex]?.findIndex(
    // 同一个工具在流式过程中会复用 messageId，但像 multimodalagent_tool 会在同一 messageId 下
    // 先发 knowledge 增量、再发 markdown 成果；这里需要把 messageType 一起纳入主键，避免串并项。
    (item: MESSAGE.Task) =>
      item.messageId === messageId && item.messageType === messageType
  );
}

/**
 * 根据消息类型处理任务消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天对象
 * @param taskIndex 任务索引
 */
function handleTaskMessageByType(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number
) {
  const messageType = eventData.resultMap.messageType;
  // messageId 在流式增量中可能复用，因此先以 messageType 参与定位，再选择对应的合并策略。
  if (messageType === "plan") {
    handlePlanMessage({
      ...eventData,
      messageType: "plan",
    }, currentChat);
    return;
  }

  const toolIndex = findToolIndex(
    currentChat.multiAgent.tasks!,
    taskIndex,
    eventData.messageId,
    messageType
  );

  switch (messageType) {
    case "agent_stream":
      handleAgentStreamMessage(eventData, currentChat);
      break;
    case "llm_retry":
      // 仅更新状态条，不进入任务时间线
      handleLlmRetryMessage(eventData, currentChat);
      break;
    case "stream_settle":
      // 后台任务收口信号：只驱动 finished，不进时间线
      break;
    case "subagent_progress":
      // 不进时间线/工作区；进度挂到父 Agent 卡片事实字段，供详情面板投影
      handleSubAgentProgressMessage(eventData, currentChat);
      break;
    case "tool_thought":
      // 助手过程 content（有 tool 时），不是思考
      handleToolThoughtMessage(eventData, currentChat, taskIndex, toolIndex);
      break;
    case "llm_reasoning":
      // 原生 CoT only
      handleLlmReasoningMessage(eventData, currentChat, taskIndex, toolIndex);
      break;
    case "html":
    case "markdown":
    case "ppt":
    case "knowledge":
    case "data_analysis":
    case "code":
      handleContentMessage(eventData, currentChat, taskIndex, toolIndex);
      break;
    case "ui_tree":
    case "ui_patch":
      handleNonStreamingMessage(eventData, currentChat, taskIndex);
      break;
    case "deep_search":
      handleDeepSearchMessage(eventData, currentChat, taskIndex);
      break;
    case "tool_call":
    case "tool_call_delta":
      // tool_call_delta：参数生成增量；tool_call：running/success/failed
      handleToolCallMessage(eventData, currentChat, taskIndex, toolIndex);
      break;
    case "ask_user_question":
      handleNonStreamingMessage(eventData, currentChat, taskIndex);
      break;
    case "plan_mode_entered":
      // Plan mode 进入事件仅供运行时控制，不在界面渲染。
      break;
    case "plan_approval":
    case "session_tasks":
    case "user_brief":
      handleNonStreamingMessage(eventData, currentChat, taskIndex);
      break;
    default:
      handleNonStreamingMessage(eventData, currentChat, taskIndex);
      break;
  }
}

/**
 * 助手过程文 / 总结一旦开始渲染，立刻收口仍在流式中的原生思考。
 * 对齐 deep_search 进入 report 后不再拖着前置阶段慢播的体验：
 * 已有思考全文马上定格展示，后续迟到的 reasoning 增量只补字、不重开流式态。
 */
function finalizeOpenNativeReasoning(currentChat: CHAT.ChatItem) {
  const groups = currentChat.multiAgent?.tasks || [];
  for (const group of groups) {
    for (const tool of group || []) {
      if (
        tool.messageType !== "llm_reasoning" &&
        tool.messageType !== "plan_thought"
      ) {
        continue;
      }
      if (tool.finish || tool.isFinal || tool.resultMap?.isFinal) {
        continue;
      }
      tool.finish = true;
      tool.isFinal = true;
      tool.resultMap = {
        ...(tool.resultMap || {}),
        isFinal: true,
      };
    }
  }
}

function asTextField(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function resolveSubAgentProgressKind(
  map: Record<string, unknown>
): SubAgentProgressKind {
  const kind = asTextField(map.kind || map.subAgentProgressKind).toLowerCase();
  if (kind === "text" || kind === "line" || kind === "heartbeat") {
    return kind;
  }
  if (asTextField(map.text)) {
    return "text";
  }
  if (asTextField(map.line)) {
    return "line";
  }
  return "heartbeat";
}

type ChatWithPendingSubAgentProgress = CHAT.ChatItem & {
  pendingSubAgentProgress?: Record<string, MESSAGE.EventData[]>;
};

function enqueuePendingSubAgentProgress(
  currentChat: CHAT.ChatItem,
  parentToolUseId: string,
  eventData: MESSAGE.EventData
) {
  const chat = currentChat as ChatWithPendingSubAgentProgress;
  const bucket = { ...(chat.pendingSubAgentProgress || {}) };
  const list = bucket[parentToolUseId] ? [...bucket[parentToolUseId]] : [];
  list.push(eventData);
  bucket[parentToolUseId] = list.slice(-80);
  chat.pendingSubAgentProgress = bucket;
}

function flushPendingSubAgentProgress(
  currentChat: CHAT.ChatItem,
  parentIds: Array<string | undefined>
) {
  const chat = currentChat as ChatWithPendingSubAgentProgress;
  const bucket = chat.pendingSubAgentProgress;
  if (!bucket) {
    return;
  }
  const keys = parentIds.map((id) => asTextField(id)).filter(Boolean);
  if (!keys.length) {
    return;
  }
  const next = { ...bucket };
  for (const key of keys) {
    const events = next[key];
    if (!events?.length) {
      continue;
    }
    const leftover: MESSAGE.EventData[] = [];
    for (const event of events) {
      if (!applySubAgentProgress(event, currentChat)) {
        leftover.push(event);
      }
    }
    if (leftover.length) {
      next[key] = leftover;
    } else {
      delete next[key];
    }
  }
  chat.pendingSubAgentProgress = Object.keys(next).length ? next : undefined;
}

/**
 * 将 subagent_progress 挂到父 Agent 工具卡（按 parentToolUseId），不插入新时间线条目。
 */
function handleSubAgentProgressMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  if (applySubAgentProgress(eventData, currentChat)) {
    return;
  }
  const resultMap = toNestedResultMap(eventData.resultMap);
  const nested = toNestedResultMap(resultMap.resultMap);
  const parentToolUseId = asTextField(
    resultMap.parentToolUseId ||
      nested.parentToolUseId ||
      resolveParentToolUseId(eventData.resultMap) ||
      resolveParentToolUseId(eventData as unknown as Record<string, unknown>)
  );
  if (parentToolUseId) {
    enqueuePendingSubAgentProgress(currentChat, parentToolUseId, eventData);
  }
}

function applySubAgentProgress(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
): boolean {
  const resultMap = toNestedResultMap(eventData.resultMap);
  const nested = toNestedResultMap(resultMap.resultMap);
  const merged: Record<string, unknown> = {
    ...resultMap,
    ...nested,
  };
  const parentToolUseId = asTextField(
    merged.parentToolUseId ||
      resolveParentToolUseId(eventData.resultMap) ||
      resolveParentToolUseId(eventData as unknown as Record<string, unknown>)
  );
  if (!parentToolUseId || !currentChat.multiAgent?.tasks?.length) {
    return false;
  }

  const kind = resolveSubAgentProgressKind(merged);
  const line = asTextField(merged.line);
  const textChunk = asTextField(merged.text);
  const elapsedMs =
    typeof merged.elapsedMs === "number"
      ? merged.elapsedMs
      : typeof merged.subAgentElapsedMs === "number"
        ? merged.subAgentElapsedMs
        : undefined;
  const phase = asTextField(merged.phase || merged.subAgentPhase);
  const status = asTextField(merged.status);
  const subAgentId = asTextField(merged.agentId || merged.subAgentId);
  const slot = findSubAgentProgressTarget(
    currentChat,
    parentToolUseId,
    subAgentId
  );
  if (!slot) {
    return false;
  }
  const task = slot.group[slot.index];

  const prev = (task.resultMap || {}) as MESSAGE.ResultMap;
  const prevLines = Array.isArray(prev.subAgentProgressLines)
    ? [...prev.subAgentProgressLines]
    : [];
  let nextLines = prevLines;
  let nextLiveText = asTextField(prev.subAgentLiveText);

  if (kind === "line" && line) {
    nextLines = [...prevLines, line].slice(-80);
  } else if (kind === "heartbeat") {
    const agentType = asTextField(merged.agentType || merged.subAgentType);
    const desc = asTextField(merged.description || merged.subAgentDescription);
    const elapsedLabel =
      typeof elapsedMs === "number" && Number.isFinite(elapsedMs)
        ? `${Math.round(elapsedMs / 1000)}s`
        : "";
    const heartbeat = [
      "running",
      agentType,
      desc,
      elapsedLabel,
    ]
      .filter(Boolean)
      .join(" · ");
    if (heartbeat) {
      // 心跳只保留最新一行，避免刷屏
      const withoutOldHeartbeat = prevLines.filter(
        (item) => !item.startsWith("running ·")
      );
      nextLines = [...withoutOldHeartbeat, heartbeat].slice(-80);
    }
  } else if (kind === "text" && textChunk) {
    nextLiveText = `${nextLiveText}${textChunk}`;
  }

  const topToolCallId = asTextField(
    (task as { toolCallId?: string }).toolCallId || resolveTaskToolCallId(task)
  );
  const topToolName = asTextField(
    task.toolResult?.toolName ||
      (task as { toolName?: string }).toolName ||
      task.resultMap?.toolName
  );

  // 父 Agent 卡只收进度投影字段；绝不能写入 parentToolUseId，
  // 否则 handleTaskData 会把它当成“子事件”踢出 taskList / 时间线。
  slot.group[slot.index] = {
    ...task,
    resultMap: {
      ...prev,
      toolCallId: prev.toolCallId || topToolCallId || undefined,
      toolName: prev.toolName || topToolName || undefined,
      input: prev.input || (task as { input?: Record<string, unknown> }).input,
      subAgentProgressKind: kind,
      subAgentPhase: phase || prev.subAgentPhase || (status === "running" ? "working" : prev.subAgentPhase),
      subAgentElapsedMs: elapsedMs ?? prev.subAgentElapsedMs,
      subAgentLiveText: nextLiveText || prev.subAgentLiveText,
      subAgentProgressLines: nextLines,
      subAgentId:
        asTextField(merged.agentId || merged.subAgentId) || prev.subAgentId,
      subAgentType:
        asTextField(merged.agentType || merged.subAgentType) || prev.subAgentType,
      subAgentDescription:
        asTextField(merged.description || merged.subAgentDescription) ||
        prev.subAgentDescription,
    },
  };
  return true;
}

type TaskSlot = {
  group: MESSAGE.Task[];
  index: number;
};

function findSubAgentProgressTarget(
  currentChat: CHAT.ChatItem,
  parentToolUseId: string,
  subAgentId?: string
): TaskSlot | undefined {
  const candidates: Array<TaskSlot & { task: MESSAGE.Task }> = [];
  for (const group of currentChat.multiAgent?.tasks || []) {
    if (!group?.length) continue;
    for (let index = 0; index < group.length; index += 1) {
      const candidate = group[index];
      if (identityRank(readTaskIdentity(candidate as CHAT.Task), parentToolUseId) === 0) {
        continue;
      }
      const toolName = asTextField(
        candidate.toolResult?.toolName || candidate.resultMap?.toolName
      );
      const isAgent = isAgentDispatchTask(candidate as CHAT.Task);
      if (!isAgent && toolName && toolName !== AGENT_DISPATCH_TOOL_NAME) {
        continue;
      }
      const candidateSubId = asTextField(
        candidate.resultMap?.subAgentId ||
          (candidate.resultMap as { agentId?: string } | undefined)?.agentId
      );
      if (subAgentId && candidateSubId && candidateSubId !== subAgentId) {
        continue;
      }
      candidates.push({ group, index, task: candidate });
    }
  }
  const best = pickBestTaskByKey(
    candidates,
    parentToolUseId,
    (item) => item.task as CHAT.Task,
    (item) => isAgentDispatchTask(item.task as CHAT.Task)
  );
  return best ? { group: best.group, index: best.index } : undefined;
}

/**
 * 处理总结阶段的流式增量文本（agent_stream）。
 * 多智能体模式下先写入临时 conclusion，等待最终 result 覆盖。
 */
function handleAgentStreamMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  const chunk = eventData.resultMap?.result || "";
  if (!chunk) {
    return;
  }
  // 总结开始即收口深度思考，避免与 conclusion 并行慢播。
  finalizeOpenNativeReasoning(currentChat);

  const streamConclusion = currentChat.conclusion;
  if (!streamConclusion || streamConclusion.messageType !== "agent_stream") {
    currentChat.conclusion = {
      taskId: eventData.taskId,
      messageId: eventData.messageId,
      messageType: "agent_stream",
      messageTime: eventData.resultMap?.messageTime || String(Date.now()),
      requestId: eventData.resultMap?.requestId || "",
      finish: false,
      isFinal: false,
      id: eventData.messageId || String(Date.now()),
      result: chunk,
      resultMap: {
        taskSummary: chunk,
        fileList: [],
      },
    } as unknown as CHAT.Task;
    return;
  }

  streamConclusion.result = `${streamConclusion.result || ""}${chunk}`;
  if (!streamConclusion.resultMap) {
    streamConclusion.resultMap = {};
  }
  streamConclusion.resultMap.taskSummary = `${streamConclusion.resultMap.taskSummary || ""}${chunk}`;
}

/**
 * 查找任务在任务数组中的索引
 * @param tasks 任务数组
 * @param taskId 任务ID
 * @returns 任务索引，如果未找到则返回-1
 */
function findTaskIndex(tasks: MESSAGE.Task[][], taskId: string | undefined): number {
  return tasks.findIndex(
    (item: MESSAGE.Task[]) => item[0]?.taskId === taskId
  );
}

/**
 * 处理工具思考消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天项
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 */
function handleToolThoughtMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number
) {
  const { tasks } = currentChat.multiAgent;
  const { taskId, resultMap } = eventData;
  // 仅过程 content，不用 reasoningContent 冒充
  const thoughtText = resultMap?.toolThought || "";
  const { isFinal } = resultMap;

  // 助手回复一开始有字，就把未完成的深度思考定格展示（剩余字等迟到包补齐）。
  if (thoughtText) {
    finalizeOpenNativeReasoning(currentChat);
  }

  if (taskIndex === -1) {
    tasks.push([
      createNewTask(taskId, {
        ...resultMap,
        messageType: "tool_thought",
        toolThought: thoughtText,
      }),
    ]);
    return;
  }

  if (toolIndex === -1) {
    tasks[taskIndex].push(
      createNewTask(taskId, {
        ...resultMap,
        messageType: "tool_thought",
        toolThought: thoughtText,
      })
    );
    return;
  }

  tasks[taskIndex][toolIndex] = nextToolThought(
    tasks[taskIndex][toolIndex],
    thoughtText || "",
    isFinal
  );
}

/** 模型瞬态失败重试：只更新 tip，不进任务列表 */
function handleLlmRetryMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  const nested = toNestedResultMap(eventData.resultMap);
  const detail = (isRecord(nested.resultMap) ? nested.resultMap : nested) as Record<
    string,
    unknown
  >;
  const attempt = Number(detail.attempt);
  const maxAttempts = Number(detail.maxAttempts);
  const message =
    typeof detail.message === "string" && detail.message.trim()
      ? detail.message.trim()
      : Number.isFinite(attempt) && Number.isFinite(maxAttempts) && maxAttempts > 0
        ? `模型请求失败，正在重试（第 ${attempt}/${maxAttempts} 次）…`
        : "模型请求失败，正在重试…";
  currentChat.tip = message;
}

/** 原生 CoT：只写 llm_reasoning，不与 tool_thought 混用 */
function handleLlmReasoningMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number
) {
  const { tasks } = currentChat.multiAgent;
  const { taskId, resultMap } = eventData;
  const reasoningText =
    (resultMap as { reasoningContent?: string } | undefined)?.reasoningContent ||
    "";
  const { isFinal } = resultMap;

  // 字段统一落在 toolThought 仅作展示载体，messageType 必须是 llm_reasoning
  if (taskIndex === -1) {
    tasks.push([
      createNewTask(taskId, {
        ...resultMap,
        messageType: "llm_reasoning",
        toolThought: reasoningText,
      }),
    ]);
    return;
  }

  if (toolIndex === -1) {
    tasks[taskIndex].push(
      createNewTask(taskId, {
        ...resultMap,
        messageType: "llm_reasoning",
        toolThought: reasoningText,
      })
    );
    return;
  }

  tasks[taskIndex][toolIndex] = nextToolThought(
    tasks[taskIndex][toolIndex],
    reasoningText || "",
    isFinal
  );
}

/**
 * 创建新任务对象
 * @param taskId 任务ID
 * @param resultMap 结果映射
 * @returns 新任务对象
 */
function createNewTask(taskId: string, resultMap: MESSAGE.Task): MESSAGE.Task {
  return {
    taskId,
    ...resultMap,
  };
}

/**
 * 更新工具思考内容
 * @param tool 工具对象
 * @param newThought 新的思考内容
 * @param isFinal 是否为最终结果
 */
function nextToolThought(tool: MESSAGE.Task, newThought: string, isFinal: boolean): MESSAGE.Task {
  const wasFinal = Boolean(tool.finish || tool.isFinal || tool.resultMap?.isFinal);
  const current = tool.toolThought || "";
  let toolThought = current;

  if (isFinal) {
    // 终包多为全文快照：以前缀关系取更长者，避免重复拼接或被短帧回退。
    if (!current) {
      toolThought = newThought;
    } else if (newThought.startsWith(current) || current.startsWith(newThought)) {
      toolThought = newThought.length >= current.length ? newThought : current;
    } else {
      toolThought = newThought;
    }
  } else {
    toolThought = `${current}${newThought}`;
  }

  // 已被助手回复收口的思考：迟到增量只补全文，不重新打开流式态。
  const final = Boolean(isFinal) || wasFinal;
  return {
    ...tool,
    toolThought,
    finish: final,
    isFinal: final,
    resultMap: {
      ...(tool.resultMap || {}),
      isFinal: final,
    },
  };
}

/**
 * 处理内容消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 */
function handleContentMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number
) {
  const nextTask = buildTaskFromEventData(eventData);
  const placeholderIndex =
    taskIndex === -1
      ? -1
      : findToolCallPlaceholderIndex(
        currentChat.multiAgent.tasks[taskIndex] || [],
        resolveTaskToolCallId(nextTask)
      );

  if (taskIndex !== -1) {
    // 更新
    if (toolIndex !== -1) {
      const targetTool = currentChat.multiAgent.tasks[taskIndex][toolIndex];
      // 已完成
      if (eventData.resultMap.resultMap.isFinal) {
        targetTool.resultMap = {
          ...eventData.resultMap.resultMap,
          codeOutput:
            eventData.resultMap.resultMap.data ||
            eventData.resultMap.resultMap.codeOutput ||
            targetTool.resultMap?.codeOutput ||
            "",
        };
        mergeTaskArtifactRefs(targetTool, eventData);
      } else {
        // 进行中
        targetTool.resultMap.isFinal = false;
        targetTool.resultMap.codeOutput += eventData.resultMap.resultMap?.data || "";
        mergeTaskArtifactRefs(targetTool, eventData);
      }
    } else {
      eventData.resultMap.resultMap = initializeResultMap(eventData.resultMap.resultMap);

      if (placeholderIndex !== -1) {
        const placeholder = currentChat.multiAgent.tasks[taskIndex][placeholderIndex];
        // canvas_publish 的 html 预览并入原工具卡，避免把 canvas_publish 卡片替换成无法点击的 html 任务。
        if (nextTask.messageType === "html") {
          currentChat.multiAgent.tasks[taskIndex][placeholderIndex] =
            mergeHtmlPreviewIntoToolCall(placeholder, nextTask);
          mergeTaskArtifactRefs(
            currentChat.multiAgent.tasks[taskIndex][placeholderIndex],
            eventData
          );
        } else {
          currentChat.multiAgent.tasks[taskIndex][placeholderIndex] =
            buildTaskFromEventData(eventData);
        }
      } else {
        // 添加tool
        currentChat.multiAgent.tasks[taskIndex].push(buildTaskFromEventData(eventData));
      }
    }
  } else {

    eventData.resultMap.resultMap = initializeResultMap(eventData.resultMap.resultMap);

    // 添加任务及tool
    currentChat.multiAgent.tasks.push([
      buildTaskFromEventData(eventData),
    ]);
  }
}

/**
 * 初始化结果映射
 * @param originalResultMap 原始结果映射
 * @returns 初始化后的结果映射
 */
export function initializeResultMap(originalResultMap: unknown) {
  const nextResultMap = isRecord(originalResultMap)
    ? (originalResultMap as MESSAGE.ResultMap)
    : {};
  return {
    ...nextResultMap,
    codeOutput: nextResultMap.codeOutput || nextResultMap.data || '',
    fileInfo: nextResultMap.fileInfo || [],
  };
}

/**
 * 处理现有任务
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 * @param eventData 事件数据
 * @param resultMap 结果映射
 */
export function handleExistingTask(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number,
  eventData: MESSAGE.EventData,
  resultMap: MESSAGE.ResultMap
) {
  if (toolIndex !== -1) {
    updateExistingTool(currentChat, taskIndex, toolIndex, resultMap, eventData);
  } else {
    addNewTool(currentChat, taskIndex, eventData, resultMap);
  }
}

/**
 * 更新现有工具
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 * @param resultMap 结果映射
 */
function updateExistingTool(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number,
  resultMap: MESSAGE.ResultMap,
  eventData?: MESSAGE.EventData
) {
  const tool = currentChat.multiAgent.tasks[taskIndex][toolIndex];
  if (resultMap.isFinal) {
    tool.resultMap = {
      ...resultMap,
      codeOutput: resultMap.data || resultMap.codeOutput || tool.resultMap?.codeOutput || "",
    };
  } else {
    tool.resultMap.isFinal = false;
    tool.resultMap.codeOutput += resultMap.data || '';
  }
  mergeTaskArtifactRefs(tool, eventData);
}

/**
 * 添加新工具
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param eventData 事件数据
 * @param resultMap 结果映射
 */
function addNewTool(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  eventData: MESSAGE.EventData,
  resultMap: MESSAGE.ResultMap
) {
  currentChat.multiAgent.tasks[taskIndex].push({
    ...buildTaskFromEventData(eventData),
    resultMap: resultMap,
  } as MESSAGE.Task);
}

/**
 * 处理新任务
 * @param currentChat 当前聊天
 * @param eventData 事件数据
 * @param resultMap 结果映射
 */
export function handleNewTask(
  currentChat: CHAT.ChatItem,
  eventData: MESSAGE.EventData,
  resultMap: MESSAGE.ResultMap
) {
  currentChat.multiAgent.tasks.push([
    {
      ...buildTaskFromEventData(eventData),
      resultMap: resultMap,
    } as MESSAGE.Task,
  ]);
}

/**
 * 处理深度搜索消息
 * @param eventData 事件数据
 * @param currentChat 当前聊天
 * @param taskIndex 任务索引
 * @param toolIndex 工具索引
 */
function isDeepSearchStageMap(resultMap?: MESSAGE.ResultMap | null): boolean {
  if (!resultMap) {
    return false;
  }
  return Boolean(
    resultMap.searchResult ||
      resultMap.chapters ||
      resultMap.chapterSummary ||
      resultMap.chapterId ||
      isDeepSearchStage(resultMap.messageType)
  );
}

function resolveDeepSearchEventPayload(
  eventData: MESSAGE.EventData
): MESSAGE.ResultMap {
  const wrapper = toNestedResultMap(eventData.resultMap);
  // 新协议：阶段载荷在 resultMap.resultMap；旧扁平协议：searchResult 直接挂在外层。
  if (isDeepSearchStageMap(wrapper.resultMap)) {
    return wrapper.resultMap as MESSAGE.ResultMap;
  }
  if (isDeepSearchStageMap(wrapper)) {
    return wrapper;
  }
  const nested = wrapper.resultMap;
  if (isDeepSearchStageMap(nested?.resultMap)) {
    return nested?.resultMap as MESSAGE.ResultMap;
  }
  return nested || {};
}

function materializeDeepSearchTask(eventData: MESSAGE.EventData): MESSAGE.Task {
  const nextTask = buildTaskFromEventData(eventData);
  const stageMap = { ...resolveDeepSearchEventPayload(eventData) };
  stageMap.answer = stageMap.answer || "";
  ensureSearchResult(stageMap);
  nextTask.messageType = "deep_search";
  nextTask.resultMap = stageMap;
  return nextTask;
}

function handleDeepSearchMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number
) {
  const resultMap = resolveDeepSearchEventPayload(eventData);
  const nextTask = buildTaskFromEventData(eventData);
  const placeholderIndex =
    taskIndex === -1
      ? -1
      : findToolCallPlaceholderIndex(
        currentChat.multiAgent.tasks[taskIndex] || [],
        resolveTaskToolCallId(nextTask)
      );
  const rawStage = String(resultMap?.messageType || "");
  // 分章总结并入既有 search 工具的 chapters 缓存，避免覆盖多 query 检索结果。
  if (rawStage === "chapter_summary" && taskIndex !== -1) {
    if (mergeDeepSearchChapterSummary(currentChat, taskIndex, eventData.messageId, resultMap)) {
      return;
    }
  }
  const stage = resolveDeepSearchStage(resultMap?.messageType);
  const toolIndex =
    taskIndex === -1
      ? -1
      : findDeepSearchToolIndex(
        currentChat.multiAgent.tasks[taskIndex] || [],
        eventData.messageId,
        stage
      );

  if (taskIndex !== -1) {
    if (toolIndex !== -1) {
      updateExistingTaskTool(currentChat, taskIndex, toolIndex, resultMap);
    } else if (placeholderIndex !== -1) {
      currentChat.multiAgent.tasks[taskIndex][placeholderIndex] =
        materializeDeepSearchTask(eventData);
    } else {
      addNewToolToExistingTask(currentChat, taskIndex, eventData);
    }
  } else {
    addNewTask(currentChat, eventData);
  }
}

/**
 * 将 chapter_summary 合并进同一 messageId 的非 report deep_search 工具。
 * 返回 true 表示已处理，调用方无需再走通用更新路径。
 */
function mergeDeepSearchChapterSummary(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  messageId: string | undefined,
  resultMap: MESSAGE.ResultMap
): boolean {
  const taskGroup = currentChat.multiAgent.tasks[taskIndex] || [];
  const toolIndex = findDeepSearchToolIndex(taskGroup, messageId, "search");
  if (toolIndex === -1) {
    return false;
  }

  const target = taskGroup[toolIndex];
  if (!target.resultMap) {
    target.resultMap = {} as MESSAGE.ResultMap;
  }
  ensureSearchResult(target.resultMap);
  if (!target.resultMap.chapters) {
    target.resultMap.chapters = {};
  }

  const chapterTitle =
    String(resultMap.chapterTitle || "").trim() ||
    String(resultMap.searchResult?.query?.[0] || "").trim() ||
    "未命名章节";
  const chapterId =
    String(resultMap.chapterId || "").trim() || chapterTitle;
  const summary = String(
    resultMap.chapterSummary || resultMap.answer || ""
  ).trim();

  const previous = target.resultMap.chapters[chapterId];
  const streaming = resultMap.chapterStreaming === true;
  const nextSummary =
    streaming && previous?.summary && summary.startsWith(previous.summary)
      ? summary
      : summary || previous?.summary || "";

  target.resultMap.chapters[chapterId] = {
    chapterId,
    chapterTitle,
    chapterContent: String(resultMap.chapterContent || previous?.chapterContent || chapterTitle).trim(),
    chapterOrder: resultMap.chapterOrder ?? previous?.chapterOrder,
    summary: nextSummary,
    streaming,
    queries: [...(resultMap.searchResult?.query || previous?.queries || [chapterTitle])],
    docs: (resultMap.searchResult?.docs || previous?.docs || []).map((bucket) =>
      Array.isArray(bucket) ? [...bucket] : []
    ),
  };

  // 若主 searchResult 尚无该章节 docs，则按标题对齐补齐，保证分卡能展示来源。
  const queries = target.resultMap.searchResult?.query || [];
  const docs = target.resultMap.searchResult?.docs || [];
  const chapterQueries = resultMap.searchResult?.query || previous?.queries || [];
  const matchedIndex = queries.findIndex(
    (item) => item === chapterTitle || chapterQueries.includes(item)
  );
  if (matchedIndex >= 0) {
    while (docs.length <= matchedIndex) {
      docs.push([]);
    }
    const incomingDocs = resultMap.searchResult?.docs?.[0];
    if (Array.isArray(incomingDocs) && incomingDocs.length && (!docs[matchedIndex] || docs[matchedIndex].length === 0)) {
      docs[matchedIndex] = [...incomingDocs];
    }
    target.resultMap.searchResult!.docs = docs;
  }

  return true;
}

/**
 * tool_call 需要立即在左侧时间线和右侧工作区可见，
 * 同一 messageId 的后续终态包则应原位覆盖，避免重复插入占位卡片。
 */
function handleToolCallMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number
) {
  // 工具一开始生成，深度思考就应收口，避免一直展开/闪光。
  finalizeOpenNativeReasoning(currentChat);
  const nextTask = normalizeIncomingToolCallTask(buildTaskFromEventData(eventData));
  const toolCallId = resolveTaskToolCallId(nextTask);
  const streamKey = resolveToolCallStreamKey(nextTask);

  if (taskIndex === -1) {
    currentChat.multiAgent.tasks.push([nextTask]);
    flushPendingSubAgentProgress(currentChat, [
      toolCallId,
      asTextField(nextTask.messageId),
      streamKey,
    ]);
    return;
  }

  const taskGroup = currentChat.multiAgent.tasks[taskIndex];
  const existingResultIndex = findTaskIndexByToolCallId(taskGroup, toolCallId, { excludeMessageType: "tool_call" });
  if (existingResultIndex !== -1) {
    const existing = taskGroup[existingResultIndex];
    if (existing?.messageType === "html") {
      taskGroup[existingResultIndex] = mergeHtmlPreviewIntoToolCall(nextTask, existing);
    }
    return;
  }

  // messageId 命中，或 streamToolKey/toolCallId 命中同一流式占位卡（真实 id 晚到时 messageId 仍是 streamKey）
  let targetIndex = toolIndex;
  if (targetIndex === -1 && streamKey) {
    targetIndex = findLastTaskIndex(taskGroup, (item) => {
      if (item.messageType !== "tool_call") {
        return false;
      }
      const existingKey = resolveToolCallStreamKey(item);
      const existingCallId = resolveTaskToolCallId(item);
      if (isDistinctToolCallId(existingCallId, toolCallId, existingKey, streamKey)) {
        return false;
      }
      return (
        existingKey === streamKey ||
        (!!toolCallId && existingCallId === toolCallId) ||
        (!!toolCallId && existingKey === toolCallId) ||
        (!!existingCallId && existingCallId === streamKey)
      );
    });
  }
  // running 事件通常 messageId=真实 toolCallId，且可能不带 streamToolKey；
  // 若仍有一张未完成的同名 streaming 卡，并入它，避免双卡。
  // 已有不同 toolCallId 的卡不能并入，否则并行 Agent 会合成一张。
  if (targetIndex === -1) {
    const nextName = String(
      nextTask.resultMap?.toolName ||
        (nextTask.resultMap as { resultMap?: { toolName?: string } })?.resultMap
          ?.toolName ||
        ""
    );
    const nextStatus = String(nextTask.resultMap?.status || "").toLowerCase();
    if (nextName && nextStatus && nextStatus !== "streaming") {
      targetIndex = findLastTaskIndex(taskGroup, (item) => {
        if (item.messageType !== "tool_call") return false;
        const existingCallId = resolveTaskToolCallId(item);
        if (isDistinctToolCallId(
          existingCallId,
          toolCallId,
          resolveToolCallStreamKey(item),
          streamKey
        )) {
          return false;
        }
        const st = String(item.resultMap?.status || "").toLowerCase();
        const stillStreamingArgs = item.resultMap?.argsStreaming === true;
        if (st !== "streaming" && st !== "preparing" && !stillStreamingArgs) return false;
        if (item.resultMap?.isFinal || item.isFinal) return false;
        const existingName = String(item.resultMap?.toolName || "");
        return !existingName || existingName === nextName;
      });
    }
  }

  if (targetIndex !== -1) {
    const currentTask = taskGroup[targetIndex];
    if (currentTask?.messageType !== "tool_call") {
      return;
    }
    taskGroup[targetIndex] = mergeToolCallStreamingTask(currentTask, nextTask);
    flushPendingSubAgentProgress(currentChat, [
      toolCallId,
      asTextField(nextTask.messageId),
      streamKey,
    ]);
    return;
  }

  taskGroup.push(nextTask);
  flushPendingSubAgentProgress(currentChat, [
    toolCallId,
    asTextField(nextTask.messageId),
    streamKey,
  ]);
}

function nestedToolPayload(resultMap?: MESSAGE.ResultMap): MESSAGE.ResultMap | undefined {
  const nested = toNestedResultMap(resultMap).resultMap;
  if (!nested || typeof nested !== "object") {
    return undefined;
  }
  return nested as MESSAGE.ResultMap;
}

/**
 * tool_call_delta → 统一成 tool_call 卡片语义，并标记 argsStreaming。
 * 实时 SSE 经 AgentSessionPrinter 把真实载荷放在 resultMap.resultMap；
 * 这里提升 toolName/input/toolCallId，让 live 与 history 共用同一套父卡识别。
 */
function normalizeIncomingToolCallTask(task: MESSAGE.Task): MESSAGE.Task {
  const outerType = String(task.messageType || "").toLowerCase();
  const resultMap = (task.resultMap || {}) as MESSAGE.ResultMap;
  const nested = nestedToolPayload(resultMap);
  const nestedType = String(
    resultMap.messageType || nested?.messageType || ""
  ).toLowerCase();
  const isDelta =
    outerType === "tool_call_delta" || nestedType === "tool_call_delta";
  const status = String(
    resultMap.status || nested?.status || ""
  ).toLowerCase();
  const args =
    resolveToolCallArgumentsText(resultMap) ||
    resolveToolCallArgumentsText(nested) ||
    (typeof resultMap.argumentsRaw === "string" ? resultMap.argumentsRaw : "") ||
    (typeof resultMap.argumentsText === "string" ? resultMap.argumentsText : "");
  const hoistedInput =
    resolveToolCallInput(resultMap) ||
    resolveToolCallInput(nested);

  const nextMap: MESSAGE.ResultMap = {
    ...(nested || {}),
    ...resultMap,
    messageType: "tool_call",
    status: status || (isDelta ? "streaming" : status),
    toolName: resultMap.toolName || nested?.toolName,
    toolCallId: resultMap.toolCallId || nested?.toolCallId,
    streamToolKey:
      (resultMap as { streamToolKey?: string }).streamToolKey ||
      (nested as { streamToolKey?: string } | undefined)?.streamToolKey,
    input:
      Object.keys(hoistedInput).length > 0
        ? hoistedInput
        : resultMap.input ?? nested?.input,
    argumentsText: args || resultMap.argumentsText || nested?.argumentsText,
    argumentsRaw:
      args ||
      resultMap.argumentsRaw ||
      nested?.argumentsRaw ||
      resultMap.argumentsText ||
      nested?.argumentsText,
    argsStreaming:
      isDelta ||
      status === "streaming" ||
      status === "preparing" ||
      resultMap.argsStreaming === true ||
      nested?.argsStreaming === true,
    parentToolUseId:
      (resultMap as { parentToolUseId?: string }).parentToolUseId ||
      (nested as { parentToolUseId?: string } | undefined)?.parentToolUseId,
  };
  if (nested) {
    nextMap.resultMap = nested;
  }

  return {
    ...task,
    messageType: "tool_call",
    resultMap: nextMap,
  } as MESSAGE.Task;
}

/**
 * 合并流式/执行态 tool_call(_delta)：
 * - 保留更长的 argumentsRaw/argumentsText
 * - 状态按 streaming→running→success 升级
 * - argsStreaming 与 status 解耦：delta 期间 true，running 后仍可短暂保留已生成的完整 args 展示
 */
function mergeToolCallStreamingTask(
  previous: MESSAGE.Task,
  next: MESSAGE.Task
): MESSAGE.Task {
  const prevMap = (previous.resultMap || {}) as MESSAGE.ResultMap;
  const nextMap = (next.resultMap || {}) as MESSAGE.ResultMap;
  const prevNested = toNestedResultMap(prevMap.resultMap);
  const nextNested = toNestedResultMap(nextMap.resultMap);

  const prevArgs =
    resolveToolCallArgumentsText(prevMap) ||
    resolveToolCallArgumentsText(prevNested as MESSAGE.ResultMap);
  const nextArgs =
    resolveToolCallArgumentsText(nextMap) ||
    resolveToolCallArgumentsText(nextNested as MESSAGE.ResultMap);
  const mergedArgs =
    nextArgs.length >= prevArgs.length ? nextArgs : prevArgs || nextArgs;

  const statusRank = (status?: string) => {
    const s = String(status || "").toLowerCase();
    if (s === "success" || s === "failed" || s === "error") return 3;
    if (s === "running") return 2;
    if (s === "streaming" || s === "preparing") return 1;
    return 0;
  };
  const prevStatus = String(prevMap.status || prevNested.status || "");
  const nextStatus = String(nextMap.status || nextNested.status || "");
  const mergedStatus =
    statusRank(nextStatus) >= statusRank(prevStatus) ? nextStatus || prevStatus : prevStatus;

  const nextMessageType = String(
    next.messageType || nextMap.messageType || nextNested.messageType || ""
  ).toLowerCase();
  const prevArgsStreaming =
    prevMap.argsStreaming === true ||
    (prevNested as { argsStreaming?: boolean }).argsStreaming === true;
  const nextArgsStreamingExplicit =
    nextMap.argsStreaming === true ||
    (nextNested as { argsStreaming?: boolean }).argsStreaming === true;
  const nextArgsStreamingFalse =
    nextMap.argsStreaming === false ||
    (nextNested as { argsStreaming?: boolean }).argsStreaming === false;
  // delta 事件或显式 true → 参数仍在涨；终态 isFinal / 显式 false → 停
  let mergedArgsStreaming = false;
  if (nextArgsStreamingFalse || Boolean(nextMap.isFinal) || Boolean(next.isFinal)) {
    mergedArgsStreaming = false;
  } else if (
    nextMessageType === "tool_call_delta" ||
    nextArgsStreamingExplicit ||
    String(mergedStatus).toLowerCase() === "streaming" ||
    String(mergedStatus).toLowerCase() === "preparing"
  ) {
    mergedArgsStreaming = true;
  } else if (prevArgsStreaming && String(mergedStatus).toLowerCase() === "running") {
    // running 刚到：参数已完整，停止逐字，但仍保留完整 args 文本供展示
    mergedArgsStreaming = false;
  } else {
    mergedArgsStreaming = prevArgsStreaming;
  }

  const mergedResultMap: MESSAGE.ResultMap = {
    ...prevMap,
    ...nextMap,
    // 卡片语义统一为 tool_call，避免 delta 类型把渲染分流打乱
    messageType: "tool_call",
    status: mergedStatus || nextMap.status || prevMap.status,
    toolName: nextMap.toolName || prevMap.toolName,
    toolCallId: nextMap.toolCallId || prevMap.toolCallId,
    streamToolKey:
      (nextMap as { streamToolKey?: string }).streamToolKey ||
      (prevMap as { streamToolKey?: string }).streamToolKey,
    argumentsText: mergedArgs || nextMap.argumentsText || prevMap.argumentsText,
    argumentsRaw:
      mergedArgs ||
      nextMap.argumentsRaw ||
      prevMap.argumentsRaw ||
      nextMap.argumentsText ||
      prevMap.argumentsText,
    argsStreaming: mergedArgsStreaming,
    input: nextMap.input ?? prevMap.input,
    summary: nextMap.summary || prevMap.summary,
    isFinal: Boolean(nextMap.isFinal || prevMap.isFinal),
    errorMsg: nextMap.errorMsg || prevMap.errorMsg,
    fileInfo: nextMap.fileInfo || prevMap.fileInfo,
    previewUrl: nextMap.previewUrl || prevMap.previewUrl,
    downloadUrl: nextMap.downloadUrl || prevMap.downloadUrl,
    primaryFileName: nextMap.primaryFileName || prevMap.primaryFileName,
  };

  // 稳定 messageId：优先沿用已有流式卡 id，避免 React 列表闪烁
  const stableMessageId =
    previous.messageId ||
    (prevMap as { messageId?: string }).messageId ||
    next.messageId;

  return {
    ...previous,
    ...next,
    messageId: stableMessageId,
    messageType: "tool_call",
    resultMap: mergedResultMap,
  } as MESSAGE.Task;
}

/**
 * deep_search 的查询分解/搜索完成会复用同一条工具记录，
 * 但总结阶段必须单独占一条记录，否则会把左侧“搜索完成”卡片直接覆盖成“正在总结”。
 */
function findDeepSearchToolIndex(
  taskGroup: MESSAGE.Task[],
  messageId: string | undefined,
  stage: ReturnType<typeof resolveDeepSearchStage>
): number {
  return taskGroup.findIndex((item) => {
    if (item.messageType !== "deep_search" || item.messageId !== messageId) {
      return false;
    }

    const itemStage = resolveDeepSearchStage(item.resultMap?.messageType);
    if (stage === "report") {
      return itemStage === "report";
    }

    return itemStage !== "report";
  });
}

/**
 * 更新现有任务和工具的结果
 */
function updateExistingTaskTool(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  toolIndex: number,
  resultMap: MESSAGE.ResultMap
) {
  const targetTool = currentChat.multiAgent.tasks[taskIndex][toolIndex];
  if (!targetTool.resultMap) {
    targetTool.resultMap = {} as MESSAGE.ResultMap;
    ensureSearchResult(targetTool.resultMap);
  }
  targetTool.resultMap.isFinal = resultMap?.isFinal;
  if (resultMap?.messageType) {
    targetTool.resultMap.messageType = resultMap.messageType;
  }
  updateSearchResult(targetTool.resultMap, resultMap?.searchResult);
  const nextAnswer = resultMap?.answer || "";

  // 总结阶段的最终包通常会带完整 answer，不能继续追加，否则会把整段总结再拼一遍。
  if (!nextAnswer) {
    return;
  }

  if (resultMap?.isFinal) {
    const currentAnswer = targetTool.resultMap.answer || "";
    if (nextAnswer === currentAnswer) {
      return;
    }

    targetTool.resultMap.answer = nextAnswer.startsWith(currentAnswer)
      ? nextAnswer
      : `${currentAnswer}${nextAnswer}`;
    return;
  }

  targetTool.resultMap.answer += nextAnswer;
}

/**
 * 添加新工具到现有任务
 */
function addNewToolToExistingTask(
  currentChat: CHAT.ChatItem,
  taskIndex: number,
  eventData: MESSAGE.EventData
) {
  currentChat.multiAgent.tasks[taskIndex].push(materializeDeepSearchTask(eventData));
}

/**
 * 添加新任务
 */
function addNewTask(currentChat: CHAT.ChatItem, eventData: MESSAGE.EventData) {
  currentChat.multiAgent.tasks.push([
    materializeDeepSearchTask(eventData),
  ]);
}

/**
 * 更新搜索结果
 */
function updateSearchResult(target: MESSAGE.ResultMap, source?: MESSAGE.SearchResult) {
  if (source?.query?.length) {
    target.searchResult!.query = source.query;
  }
  if (source?.docs?.length) {
    target.searchResult!.docs = source.docs;
  }
  if (source?.chapters?.length) {
    target.searchResult!.chapters = source.chapters;
  }
}

/**
 * 确保搜索结果存在
 */
function ensureSearchResult(resultMap: MESSAGE.ResultMap) {
  if (resultMap.searchResult) {
    resultMap.searchResult.query = resultMap.searchResult?.query || [];
    resultMap.searchResult.docs = resultMap.searchResult?.docs || [];
  } else {
    resultMap.searchResult = {
      query: [],
      docs: []
    };
  }
}

function handleNonStreamingMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem,
  taskIndex: number,
) {
  const nextTask = buildTaskFromEventData(eventData);
  // 文件目录同步事件与普通 tool_call/tool_result 共用 toolCallId 时，不能参与
  // 占位卡合并；独立保留事实，避免覆盖普通工具的入参/出参展示。
  if (isFileListOnlyTask(nextTask)) {
    if (taskIndex !== -1) {
      currentChat.multiAgent.tasks[taskIndex].push(nextTask);
    } else {
      currentChat.multiAgent.tasks.push([nextTask]);
    }
    return;
  }
  // 冻结 GenUI 初始树，后续 patch 展示层可相对 originalTree 全量重放
  if (nextTask.messageType === "ui_tree") {
    ensureOriginalTree(nextTask as any);
  }

  if (taskIndex !== -1) {
    const taskGroup = currentChat.multiAgent.tasks[taskIndex];

    // GenUI patch: merge onto latest ui_tree (any task group; plan steps may differ).
    // 始终保留 patch 事件进 multiAgent.tasks，findFeaturedGenUi 才能在最终回复区重放。
    if (nextTask.messageType === "ui_patch") {
      mergeUiPatchIntoTasks(
        currentChat.multiAgent.tasks as any,
        nextTask as any
      );
      taskGroup.push(nextTask);
      return;
    }
    const placeholderIndex = findToolCallPlaceholderForTask(taskGroup, nextTask);
    const toolCallId = resolveTaskToolCallId(nextTask);

    if (isImageGenerationToolResultTask(nextTask)) {
      const fileTaskIndex = toolCallId
        ? findLastTaskIndex(
          taskGroup,
          (task) =>
            isImageGenerationFileTask(task) &&
            resolveTaskToolCallId(task) === toolCallId
        )
        : -1;
      if (fileTaskIndex !== -1) {
        taskGroup[fileTaskIndex] = mergeImageGenerationToolTask(
          nextTask,
          taskGroup[fileTaskIndex]
        );
        return;
      }
    }

    if (isImageGenerationFileTask(nextTask)) {
      const toolTaskIndex = toolCallId
        ? findLastTaskIndex(
          taskGroup,
          (task) =>
            isImageGenerationToolResultTask(task) &&
            resolveTaskToolCallId(task) === toolCallId
        )
        : -1;
      if (toolTaskIndex !== -1) {
        taskGroup[toolTaskIndex] = mergeImageGenerationToolTask(
          taskGroup[toolTaskIndex],
          nextTask
        );
        return;
      }
    }

    if (placeholderIndex !== -1) {
      const previous = taskGroup[placeholderIndex] as CHAT.Task;
      // Agent 卡片在 tool_call→tool_result 替换时保留已挂载的子工具树
      if (
        Array.isArray(previous?.children) &&
        previous.children.length > 0 &&
        !(nextTask as CHAT.Task).children
      ) {
        (nextTask as CHAT.Task).children = previous.children;
      }
      // 保留 subagent_progress 投影字段，避免终态覆盖丢掉直播进度
      if (previous?.resultMap && isAgentDispatchTask(previous)) {
        const prevMap = previous.resultMap;
        const nextMap = (nextTask.resultMap || {}) as MESSAGE.ResultMap;
        nextTask.resultMap = {
          ...nextMap,
          // Agent 终态包可能只带 messageId；保留占位卡身份，后续子事件仍能找到父卡。
          toolCallId: nextMap.toolCallId || prevMap.toolCallId,
          toolName: nextMap.toolName || prevMap.toolName,
          input: nextMap.input || prevMap.input,
          streamToolKey:
            nextMap.streamToolKey || prevMap.streamToolKey,
          subAgentLiveText: nextMap.subAgentLiveText || prevMap.subAgentLiveText,
          subAgentProgressLines:
            nextMap.subAgentProgressLines?.length
              ? nextMap.subAgentProgressLines
              : prevMap.subAgentProgressLines,
          subAgentElapsedMs:
            nextMap.subAgentElapsedMs ?? prevMap.subAgentElapsedMs,
          subAgentPhase: nextMap.subAgentPhase || prevMap.subAgentPhase,
          subAgentProgressKind:
            nextMap.subAgentProgressKind || prevMap.subAgentProgressKind,
          run_in_background:
            nextMap.run_in_background ??
            prevMap.run_in_background ??
            (resolveToolCallInput(
              prevMap as unknown as MESSAGE.ResultMap
            ).run_in_background as boolean | undefined),
          runInBackground:
            nextMap.runInBackground ??
            prevMap.runInBackground,
        };
      }
      taskGroup[placeholderIndex] = nextTask;
      return;
    }

    taskGroup.push(nextTask);
  } else if (nextTask.messageType === "ui_patch") {
    const merged = mergeUiPatchIntoTasks(
      currentChat.multiAgent.tasks as any,
      nextTask as any
    );
    currentChat.multiAgent.tasks.push([nextTask]);
    if (!merged) {
      // no prior ui_tree — breadcrumb only
    }
  } else {
    currentChat.multiAgent.tasks.push([
      nextTask,
    ]);
  }

}

/**
 * tool_result 的终态包在不同协议入口可能只保留 messageId 或 streamToolKey；
 * 同步 Agent 完成时必须仍能命中原始 tool_call，否则主工作区会出现“卡片消失”。
 * 后台 Agent 结算会再发一条同 toolCallId 的 tool_result，也需命中已替换的卡片。
 */
function findToolCallPlaceholderForTask(
  tasks: MESSAGE.Task[],
  task: MESSAGE.Task
) {
  const incoming = readTaskIdentity(task);
  const streamKey = resolveToolCallStreamKey(task);
  return findLastTaskIndex(tasks, (candidate) => {
    if (
      candidate.messageType !== "tool_call" &&
      candidate.messageType !== "tool_result"
    ) {
      return false;
    }
    const existing = readTaskIdentity(candidate);
    const candidateStreamKey = resolveToolCallStreamKey(candidate);
    return Boolean(
      (incoming.toolCallId && existing.toolCallId === incoming.toolCallId) ||
      (streamKey && candidateStreamKey === streamKey) ||
      (incoming.messageId && existing.messageId === incoming.messageId)
    );
  });
}

function upsertNestedChild(parent: CHAT.Task, child: CHAT.Task) {
  if (!parent.children) {
    parent.children = [];
  }
  const incomingKeys = identityKeys(readTaskIdentity(child));
  if (!incomingKeys.length) {
    parent.children = [...parent.children, child];
    return;
  }
  const existingIndex = parent.children.findIndex((item) =>
    identityKeys(readTaskIdentity(item)).some((key) => incomingKeys.includes(key))
  );
  if (existingIndex >= 0) {
    const next = [...parent.children];
    next[existingIndex] = child;
    parent.children = next;
  } else {
    parent.children = [...parent.children, child];
  }
}

function attachChildrenToParent(parent: CHAT.Task, children: CHAT.Task[]) {
  for (const child of children) {
    upsertNestedChild(parent, child);
  }
}

function registerAgentParentKeys(
  agentParentByToolCallId: Map<string, CHAT.Task>,
  pendingByParentId: Map<string, CHAT.Task[]>,
  item: CHAT.Task
) {
  if (!item.children) {
    item.children = [];
  }
  for (const key of identityKeys(readTaskIdentity(item))) {
    const existing = agentParentByToolCallId.get(key);
    if (existing && existing !== item) {
      const existingRank = identityRank(readTaskIdentity(existing), key);
      const nextRank = identityRank(readTaskIdentity(item), key);
      if (existingRank >= nextRank) {
        continue;
      }
    }
    agentParentByToolCallId.set(key, item);
    const pending = pendingByParentId.get(key);
    if (!pending?.length) {
      continue;
    }
    attachChildrenToParent(item, pending);
    pendingByParentId.delete(key);
  }
}

function nestChildrenUnderParent(
  agentParentByToolCallId: Map<string, CHAT.Task>,
  pendingByParentId: Map<string, CHAT.Task[]>,
  parentToolUseId: string,
  children: CHAT.Task[]
) {
  const parent = agentParentByToolCallId.get(parentToolUseId);
  if (parent) {
    attachChildrenToParent(parent, children);
    return;
  }
  // 父卡尚未登记：暂存，绝不平铺进主时间线（避免子工具卡泄漏）
  const pending = pendingByParentId.get(parentToolUseId) || [];
  pending.push(...children);
  pendingByParentId.set(parentToolUseId, pending);
}

/** 进度事件曾把父卡自身 id 写成 parentToolUseId，需识别并忽略 */
function isSelfReferentialParentId(
  task: Partial<CHAT.Task> | Partial<MESSAGE.Task> | Record<string, unknown>,
  parentToolUseId: string
) {
  if (!parentToolUseId) {
    return false;
  }
  return identityKeys(readTaskIdentity(task as CHAT.Task)).includes(parentToolUseId);
}

/**
 * 处理多智能体任务数据，整合聊天、计划和任务信息
 * @param currentChat 当前聊天对象
 * @param deepThink 深度思考
 * @param multiAgent 多智能体数据
 * @returns 处理后的数据对象
 */
export const handleTaskData = (
  currentChat: CHAT.ChatItem,
  deepThink?: boolean,
  multiAgent?: MESSAGE.MultiAgent
) => {
  const {
    plan: fullPlan,
    tasks: fullTasks,
    plan_thought: planThought,
  } = multiAgent ?? {};

  // 该函数是唯一的派生层：原始 multiAgent.tasks 保留事件事实，chat.tasks/taskList/conclusion 只从事实重建。
  const TOOL_TYPES = [
    "tool_call",
    "tool_result",
    "ask_user_question",
    "plan_approval",
    "session_tasks",
    "user_brief",
    "browser",
    "code",
    "html",
    "file",
    "knowledge",
    "result",
    "deep_search",
    "markdown",
    "ppt",
    "data_analysis",
    "ui_tree",
    "ui_patch",
  ];

  currentChat.thought = planThought || "";

  let requestConclusion: MESSAGE.Task | CHAT.Task | undefined;
  let fallbackTaskSummary: MESSAGE.Task | CHAT.Task | undefined;
  let plan = fullPlan;
  const taskList: CHAT.Task[] = [];

  const validTasks: MESSAGE.Task[][] = fullTasks?.filter(
    (item: MESSAGE.Task[]) => item && item?.length > 0
  ) ?? [];

  const chatList: TimelineTaskContainer[][] = !deepThink
    ? [
      [
        {
          hidden: false,
          task: "",
          children: [],
        },
      ],
    ]
    : Array.from({ length: validTasks?.length || 0 }, () => []);

  // 跨 taskGroup 共享父索引：子工具与父 Agent 可能落在不同组，禁止因组隔离而平铺泄漏。
  // pending：父卡尚未出现时暂存子工具；重建结束仍无父则不出主时间线（右侧靠 Agent.children）。
  const agentParentByToolCallId = new Map<string, CHAT.Task>();
  const pendingByParentId = new Map<string, CHAT.Task[]>();

  validTasks?.forEach((taskGroup, groupIndex) => {
    const timelineTaskGroup = ensureTimelineTaskGroup(chatList, groupIndex);

    // Pass 1：先登记本组全部 Agent 父卡（含多别名），再处理挂载，消除「子先于父」单遍 miss
    const processedGroup: Array<{
      task: MESSAGE.Task;
      processedInfo: CHAT.Task[];
      parentToolUseId: string;
    }> = [];

    taskGroup?.forEach((task, taskIndex) => {
      if (task?.messageType === "plan_mode_entered") {
        return;
      }
      const time = task.messageTime;
      const id = time?.concat(String(taskIndex));
      const processedInfo = processTaskForRender(task, id);
      // Agent 父卡若被误写入自身 parentToolUseId，按无父处理，避免自嵌套后从 taskList 消失。
      const rawParentId = resolveParentToolUseId(task);
      const parentToolUseId = isSelfReferentialParentId(task, rawParentId)
        ? ""
        : rawParentId;
      processedGroup.push({
        task,
        processedInfo,
        parentToolUseId
      });

      for (const item of processedInfo) {
        if (isAgentDispatchTask(item)) {
          registerAgentParentKeys(
            agentParentByToolCallId,
            pendingByParentId,
            item
          );
        }
      }
    });

    // Pass 2：挂载子工具 / 推入主时间线
    for (const { task, processedInfo, parentToolUseId } of processedGroup) {
      if (task.messageType === "task") {
        upsertTimelineTaskContainer(timelineTaskGroup, task);
      // 深度研究里的 task_summary 属于任务级总结，必须保留在时间线中；
      // 只有请求级 result 才应该落在底部最终结论区。
      } else if (task?.messageType === "result" && parentToolUseId) {
        nestChildrenUnderParent(
          agentParentByToolCallId,
          pendingByParentId,
          parentToolUseId,
          processedInfo
        );
      } else if (task?.messageType !== "result") {
        if (parentToolUseId) {
          nestChildrenUnderParent(
            agentParentByToolCallId,
            pendingByParentId,
            parentToolUseId,
            processedInfo
          );
        } else {
          ensureTimelineTaskContainer(timelineTaskGroup, task).children.push(
            ...processedInfo
          );
        }
      }

      if (TOOL_TYPES.includes(task?.messageType)) {
        // 工作区列表：子工具不进顶层 taskList，只挂在 Agent 下
        if (!parentToolUseId) {
          taskList.push(...processedInfo);
        }
      }

      if (task?.messageType === "plan") {
        plan = task.plan;
      }

      if (task?.messageType === "result" && !parentToolUseId) {
        requestConclusion = task;
      } else if (task?.messageType === "task_summary") {
        fallbackTaskSummary = task;
      }
    }
  });

  const streamConclusion =
    currentChat.conclusion?.messageType === "agent_stream"
      ? currentChat.conclusion
      : undefined;

  currentChat.tasks = chatList as unknown as CHAT.Task[][];
  currentChat.plan = plan;
  currentChat.conclusion =
    (requestConclusion as CHAT.Task | undefined) ||
    streamConclusion ||
    (!currentChat.loading
      ? (fallbackTaskSummary as CHAT.Task | undefined)
      : undefined);
  currentChat.planList = plan?.stages?.reduce(
    (result: CHAT.PlanItem[], stage: string, index: number) => {
      const group = result.find((item) => item.name === stage);
      if (group) {
        group.list.push(plan?.steps[index] || "");
      } else {
        result.push({
          name: stage,
          list: [plan?.steps[index] || ""],
        });
      }
      return result;
    },
    []
  );

  return {
    currentChat,
    plan,
    taskList,
    chatList: chatList as unknown as CHAT.Task[][],
  };
};

/**
 * 为当前会话快照重建工作区任务数据。
 * 这里统一把缓存下来的任务结果重新整理成界面消费结构，
 * 避免组件层直接依赖流式过程中产生的临时对象形态。
 */
export const buildConversationTaskData = (
  chat: CHAT.ChatItem,
  deepThink?: boolean
) => {
  const snapshotChat = {
    ...chat,
    files: [...(chat.files || [])],
    tasks: [],
    multiAgent: {
      ...chat.multiAgent,
      plan: clonePlanForRender(chat.multiAgent?.plan),
      tasks: (chat.multiAgent?.tasks || []).map((group) =>
        group.map((task) => cloneTaskSnapshot(task))
      ),
    },
    timeline: [...(chat.timeline || [])],
  } as CHAT.ChatItem;

  return handleTaskData(snapshotChat, deepThink, snapshotChat.multiAgent);
};

/**
 * 构建任务动作信息
 * @param task 任务对象
 * @returns 包含action、tool和name的动作信息对象
 */
export const buildAction = (task: CHAT.Task) => {
  // 定义消息类型常量
  const MESSAGE_TYPES = {
    TOOL_CALL: "tool_call",
    TOOL_RESULT: "tool_result",
    ASK_USER_QUESTION: "ask_user_question",
    PLAN_APPROVAL: "plan_approval",
    SESSION_TASKS: "session_tasks",
    CODE: "code",
    HTML: "html",
    UI_TREE: "ui_tree",
    UI_PATCH: "ui_patch",
    PLAN_THOUGHT: "plan_thought",
    PLAN: "plan",
    FILE: "file",
    KNOWLEDGE: "knowledge",
    DEEP_SEARCH: "deep_search",
    MARKDOWN: "markdown",
    DATA_ANALYSIS: "data_analysis"
  };

  const TOOL_NAMES = {
    WEB_SEARCH: "web_search",
    INTERNAL_SEARCH: "internal_search",
    CODE_INTERPRETER: "code_interpreter"
  };

  switch (task.messageType) {
    case MESSAGE_TYPES.TOOL_CALL:
      return handleToolCallTask(task);

    case MESSAGE_TYPES.TOOL_RESULT:
      return handleToolResult(task);

    case MESSAGE_TYPES.ASK_USER_QUESTION:
      return {
        action: "等待你的回答",
        tool: "AskUserQuestion",
        name: "选择题",
      };

    case MESSAGE_TYPES.PLAN_APPROVAL:
      return {
        action: "等待批准计划",
        tool: "ExitPlanMode",
        name: "计划批准",
      };

    case MESSAGE_TYPES.SESSION_TASKS:
      return {
        action: "任务列表已更新",
        tool: "TaskList",
        name: "Todo",
      };

    case MESSAGE_TYPES.CODE:
      return {
        action: "正在执行代码",
        tool: "编辑器",
        name: ""
      };

    case MESSAGE_TYPES.HTML:
      return {
        action: "正在生成web页面",
        tool: "编辑器",
        name: ""
      };

    case MESSAGE_TYPES.UI_TREE:
      return {
        action: "正在生成界面组件",
        tool: "GenUI",
        name: "画布"
      };

    case MESSAGE_TYPES.UI_PATCH:
      return {
        action: "正在更新界面组件",
        tool: "GenUI",
        name: "补丁"
      };

    case MESSAGE_TYPES.PLAN_THOUGHT:
      return {
        action: "正在思考下一步计划",
        tool: "",
        name: ""
      };

    case MESSAGE_TYPES.PLAN:
      return {
        action: "更新任务列表",
        tool: "",
        name: ""
      };

    case MESSAGE_TYPES.FILE:
      return handleFileTask(task);

    case MESSAGE_TYPES.KNOWLEDGE:
      return {
        action: "正在调用知识库",
        tool: "文件编辑器",
        name: "查询知识库"
      };

    case MESSAGE_TYPES.DEEP_SEARCH:
      return handleDeepSearchTask(task);

    case MESSAGE_TYPES.MARKDOWN:
      return {
        action: "正在生成报告",
        tool: "markdown",
        name: ""
      };

    case MESSAGE_TYPES.DATA_ANALYSIS:
      return {
        action: "正在分析数据",
        tool: "数据分析工具",
        name: task.resultMap.task
      };

    default:
      return {
        action: "正在调用工具",
        tool: task?.messageType || "",
        name: ""
      };
  }

  /**
   * 处理工具结果类型的任务
   * @param task 任务对象
   * @returns 动作信息对象
   */
  function handleToolResult(task: CHAT.Task) {
    const toolResult = resolveTaskToolResult(task);
    const primaryFile = getPrimaryTaskFile(task);
    const resultMap = task?.resultMap || {};
    const nestedResultMap = (resultMap.resultMap || {}) as typeof resultMap;
    const toolName =
      toolResult?.toolName ||
      resultMap.toolName ||
      nestedResultMap.toolName ||
      "";
    const completed = Boolean(
      task.finish ||
      task.isFinal ||
      resultMap.isFinal ||
      nestedResultMap.isFinal ||
      resultMap.status === "success" ||
      nestedResultMap.status === "success"
    );

    if (completed) {
      return {
        action: "工具调用完成",
        tool: toolName || "",
        name: toolName || "",
      };
    }

    if (toolName === AGENT_DISPATCH_TOOL_NAME || isAgentDispatchTask(task)) {
      return buildSubAgentAction(task);
    }

    switch (toolName) {
      case TOOL_NAMES.WEB_SEARCH:
      case TOOL_NAMES.INTERNAL_SEARCH:
        return {
          action: "正在搜索",
          tool: "网络查询",
          name: String(toolResult?.toolParam?.query || "")
        };

      case TOOL_NAMES.CODE_INTERPRETER:
        return {
          action: "正在执行代码",
          tool: "编辑器",
          name: "执行代码"
        };

      case "image_generation_tool":
        return {
          action: "生成图片",
          tool: "图片生成",
          name: primaryFile?.name || toolName
        };

      default:
        return {
          action: "正在调用工具",
          tool: toolName || "",
          name: toolName || ""
        };
    }
  }

  /**
   * 工具下发阶段优先展示目标文件/路径，让用户立刻知道当前卡在“调用哪个工具做什么”。
   */
  function handleToolCallTask(task: CHAT.Task) {
    if (isAgentDispatchTask(task) || task?.resultMap?.toolName === AGENT_DISPATCH_TOOL_NAME) {
      return buildSubAgentAction(task);
    }
    return {
      action: resolveToolCallActionText(task),
      tool: task?.resultMap?.toolName || "",
      name: resolveToolCallTargetName(task?.resultMap as unknown as MESSAGE.ResultMap | undefined)
    };
  }

  /**
   * 处理文件类型的任务
   * @param task 任务对象
   * @returns 动作信息对象
   */
  function handleFileTask(task: CHAT.Task) {
    return {
      action: task?.resultMap?.command || "",
      tool: "文件编辑器",
      name: getPrimaryTaskFileName(task)
    };
  }

  /**
   * 处理深度搜索类型的任务
   * @param task 任务对象
   * @returns 动作信息对象
   */
  function handleDeepSearchTask(task: CHAT.Task) {
    const stage = resolveDeepSearchStage(task?.resultMap?.messageType);
    const queryText =
      stage === "report"
        ? formatDeepSearchQueryText(task?.resultMap?.query) ||
          formatDeepSearchQueryText(task?.resultMap?.searchResult?.query)
        : formatDeepSearchQueryText(task?.resultMap?.searchResult?.query);

    return {
      action: resolveDeepSearchActionText(stage, task?.resultMap?.isFinal),
      tool: "深度搜索",
      name: queryText
    };
  }
};

export enum IconType {
  PLAN = 'plan',
  PLAN_THOUGHT = 'plan_thought',
  TOOL_CALL = 'tool_call',
  TOOL_RESULT = 'tool_result',
  ASK_USER_QUESTION = 'ask_user_question',
  PLAN_APPROVAL = 'plan_approval',
  SESSION_TASKS = 'session_tasks',
  BROWSER = 'browser',
  FILE = 'file',
  DEEP_SEARCH = 'deep_search',
  CODE = 'code',
  HTML = 'html',
  AGENT = 'Agent',
}

/**
 * 图标映射表
 */
const ICON_MAP: Record<IconType, string> = {
  [IconType.PLAN]: 'icon-renwu',
  [IconType.PLAN_THOUGHT]: 'icon-juli',
  [IconType.TOOL_CALL]: 'icon-tiaoshi',
  [IconType.TOOL_RESULT]: 'icon-tiaoshi',
  [IconType.ASK_USER_QUESTION]: 'icon-juli',
  [IconType.PLAN_APPROVAL]: 'icon-renwu',
  [IconType.SESSION_TASKS]: 'icon-renwu',
  [IconType.BROWSER]: 'icon-sousuo',
  [IconType.FILE]: 'icon-bianji',
  [IconType.DEEP_SEARCH]: 'icon-sousuo',
  [IconType.CODE]: 'icon-daima',
  [IconType.HTML]: 'icon-daima',
  [IconType.AGENT]: 'icon-renwu',
};

/**
 * 默认图标
 */
const DEFAULT_ICON = 'icon-tiaoshi';

/**
 * 根据指定的类型获取对应的图标名称
 * @param type - 图标类型，可以是 IconType 枚举中的值或其他字符串
 * @returns 对应的图标名称，如果类型不存在则返回默认图标
 */
export const getIcon = (type: string): string => {
  if (type in ICON_MAP) {
    return ICON_MAP[type as IconType];
  }
  return DEFAULT_ICON;
};

export const buildAttachment = (fileList?: CHAT.FileList[]): CHAT.TFile[] => {
  if (!Array.isArray(fileList) || !fileList.length) {
    return [];
  }

  return fileList
    .map((item) => normalizeTaskFile(item))
    .filter((item): item is CHAT.TFile => Boolean(item));
};
