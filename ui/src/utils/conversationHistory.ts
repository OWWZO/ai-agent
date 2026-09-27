import type {
  ConversationHistoryDetail,
  ConversationHistoryPage,
  ConversationReplayFrame,
  ConversationRunReplay,
  ConversationRunSummary,
} from "@/services/agentConversation";
import { GENERIC_TASK_PRODUCT } from "@/utils/constants";

import { buildConversationTaskData, buildTaskFromEventData, combineData } from "./chat";

type ConversationHistoryTitleDetail = Pick<
  ConversationHistoryPage,
  "title" | "sessionId"
> & {
  runs?: Array<ConversationRunSummary | ConversationRunReplay>;
};

/**
 * 会话详情为空时，首页应保持当前空白/初始态，不自动切到其他会话。
 */
export function isHistoryDetailEmpty(
  detail?: ConversationHistoryPage | ConversationHistoryDetail | null
) {
  return !detail || !Array.isArray(detail.runs) || detail.runs.length === 0;
}

export function toConversationHistoryTitle(detail?: ConversationHistoryTitleDetail | null) {
  if (!detail) {
    return "新对话";
  }
  const normalizedTitle = String(detail.title || "").trim();
  if (normalizedTitle && normalizedTitle !== "新对话") {
    return normalizedTitle;
  }

  // 兼容旧的 Plan continuation：session head 可能被空 query 覆盖，但首个 run 仍保留原问题。
  const firstQuery = detail.runs?.find(
    (run) => getRunQuery(run).trim()
  );
  const firstQueryText = firstQuery ? getRunQuery(firstQuery) : "";
  const normalizedQuery = firstQueryText.trim();
  if (normalizedQuery) {
    return normalizedQuery.length <= 30
      ? normalizedQuery
      : normalizedQuery.slice(0, 30);
  }

  return normalizedTitle || (detail.sessionId ? `会话 ${detail.sessionId}` : "新对话");
}

/**
 * 首次 session 请求只还原 run 的轻量摘要壳，不读取响应中的 replayFrames。
 */
export function hydrateConversationFromSummaryPage(
  page: ConversationHistoryPage
): CHAT.ConversationHistory {
  const chatList = (page.runs || []).map((run) => hydrateRunSummary(page, run));
  return buildConversationHistory(page, chatList);
}

/**
 * 兼容已有 rich detail 调用方：有 replayFrames 的 run 走完整回放，没有的 run
 * 仍保持为 summary shell。
 */
export function hydrateConversationFromReplayFrames(
  detail: ConversationHistoryPage | ConversationHistoryDetail
): CHAT.ConversationHistory {
  const chatList = (detail.runs || []).map((run) => {
    if (hasReplayFrames(run)) {
      return hydrateRunReplay(detail, run as ConversationRunReplay);
    }
    return hydrateRunSummary(detail, run);
  });
  return buildConversationHistory(detail, chatList);
}

export function hydrateRunSummary(
  detail: ConversationHistoryPage | ConversationHistoryDetail,
  run: ConversationRunSummary | ConversationRunReplay
): CHAT.ChatItem {
  const currentChat = createRunShell(detail, run);
  applyFallbackConclusion(currentChat, run, false);
  applyRunMetadata(currentChat, run);
  return currentChat;
}

export function hydrateRunReplay(
  detail: ConversationHistoryPage | ConversationHistoryDetail,
  run: ConversationRunReplay
): CHAT.ChatItem {
  // 历史 run 先还原为与实时流相同的空状态，再逐帧复用 combineData，确保历史和实时使用同一事件语义。
  const currentChat = createRunShell(detail, run);

  for (const frame of run.replayFrames || []) {
    const eventData = readEventData(frame);
    if (!eventData) {
      continue;
    }
    combineData(eventData, currentChat);
    syncConclusionFromEventData(currentChat, eventData);
  }

  applyFallbackConclusion(currentChat, run, true);
  applyRunMetadata(currentChat, run);

  return {
    ...buildConversationTaskData(currentChat, detail.deepThink).currentChat,
    replayLoaded: true,
  };
}

/**
 * 把下一页 summary 合并到现有会话。已有 requestId 的 chat 保持原对象，避免
 * 分页响应把已经 replay 或仍在流式更新的 run 覆盖掉。
 */
export function mergeConversationHistoryPage(
  conversation: CHAT.ConversationHistory,
  page: ConversationHistoryPage
): CHAT.ConversationHistory {
  const incoming = (page.runs || []).map((run) => hydrateRunSummary(page, run));
  const chatList = mergeSummaryChatsByRequestId(conversation.chatList, incoming);
  const createdAt = toTimestamp(page.startedAt, conversation.createdAt);
  const updatedAt = toTimestamp(page.lastActiveAt, conversation.updatedAt);
  const title =
    conversation.title && conversation.title !== "新对话"
      ? conversation.title
      : conversation.chatTitle || toConversationHistoryTitle(page);

  return {
    ...conversation,
    sessionId: page.sessionId || conversation.sessionId,
    title,
    deepThink: Boolean(page.deepThink),
    createdAt,
    updatedAt,
    chatTitle: title,
    chatList,
    historyNextCursor: page.nextCursor ?? null,
    historyHasMore: page.hasMore ?? Boolean(page.nextCursor),
  };
}

/**
 * 用单 run rich replay 替换目标 requestId；其它 run、已加载状态和顺序全部保留。
 * 若目标不在当前 summary 页，直接追加，供 follow_idle 恢复漏掉的 run。
 */
export function mergeRunReplayIntoConversation(
  conversation: CHAT.ConversationHistory,
  replay: ConversationRunReplay
): CHAT.ConversationHistory {
  const detail: ConversationHistoryPage = {
    sessionId: conversation.sessionId,
    title: conversation.title,
    status: "",
    deepThink: conversation.deepThink,
    runCount: conversation.chatList.length,
    finishedRunCount: 0,
    failedRunCount: 0,
    runs: [],
  };
  const replayChat = hydrateRunReplay(detail, replay);
  const nextChatList: CHAT.ChatItem[] = [];
  const seenRequestIds = new Set<string>();
  let replaced = false;

  for (const chat of conversation.chatList || []) {
    const requestId = chat.requestId;
    if (requestId && seenRequestIds.has(requestId)) {
      continue;
    }
    if (requestId) {
      seenRequestIds.add(requestId);
    }
    if (requestId === replay.requestId) {
      if (!replaced) {
        nextChatList.push(replayChat);
        replaced = true;
      }
      continue;
    }
    nextChatList.push(chat);
  }

  if (!replaced) {
    nextChatList.push(replayChat);
  }

  return {
    ...conversation,
    chatList: nextChatList,
  };
}

function buildConversationHistory(
  detail: ConversationHistoryPage | ConversationHistoryDetail,
  chatList: CHAT.ChatItem[]
): CHAT.ConversationHistory {
  const createdAt = toTimestamp(detail.startedAt);
  const updatedAt = toTimestamp(detail.lastActiveAt, createdAt);
  const title = toConversationHistoryTitle(detail);

  return {
    id: `conversation-${detail.sessionId}`,
    sessionId: detail.sessionId,
    title,
    productType: GENERIC_TASK_PRODUCT.type,
    deepThink: Boolean(detail.deepThink),
    createdAt,
    updatedAt,
    chatTitle: title,
    chatList,
    dataChatList: [],
    historyNextCursor: detail.nextCursor ?? null,
    historyHasMore: detail.hasMore ?? Boolean(detail.nextCursor),
  };
}

function createRunShell(
  detail: ConversationHistoryPage | ConversationHistoryDetail,
  run: ConversationRunSummary | ConversationRunReplay
): CHAT.ChatItem {
  const runStatus = normalizeRunStatus(run.status);
  const isRunning = runStatus === "RUNNING";
  return {
    sessionId: detail.sessionId,
    requestId: run.requestId,
    query: getRunQuery(run),
    files: [],
    responseType: "txt",
    agentType: resolveConversationAgentType(detail.deepThink),
    loading: isRunning,
    forceStop: runStatus === "STOPPED",
    tasks: [],
    thought: "",
    response: "",
    taskStatus: 0,
    tip: "",
    multiAgent: { tasks: [] },
    timeline: [],
    startedAt: run.startedAt,
    finishedAt: run.finishedAt,
    replayLoaded: false,
    replayAvailable: isRunReplay(run) ? true : run.hasReplay,
    metrics: { status: runStatus },
  } as CHAT.ChatItem;
}

function applyFallbackConclusion(
  currentChat: CHAT.ChatItem,
  run: ConversationRunSummary | ConversationRunReplay,
  includeTaskData: boolean
) {
  const runStatus = normalizeRunStatus(run.status);
  const summaryText = getRunSummary(run);
  if (runStatus === "RUNNING" || currentChat.conclusion || !summaryText) {
    return;
  }
  // 旧数据或失败 run 可能没有 result frame，用 run 终态摘要补一条与实时 result 同构的事件。
  const fallbackEventData = buildFallbackConclusionEventData(run, summaryText);
  if (includeTaskData) {
    combineData(fallbackEventData, currentChat);
    syncConclusionFromEventData(currentChat, fallbackEventData);
    return;
  }
  currentChat.conclusion = buildTaskFromEventData(fallbackEventData) as unknown as CHAT.Task;
}

function applyRunMetadata(
  currentChat: CHAT.ChatItem,
  run: ConversationRunSummary | ConversationRunReplay
) {
  const runStatus = normalizeRunStatus(run.status);
  const isRunning = runStatus === "RUNNING";

  // 历史接口可能只返回到账本已落盘的部分事件，RUNNING 状态必须覆盖事件中的
  // 暂态字段，避免页面刷新后把仍在后台执行的 run 错误显示成已完成。
  currentChat.loading = isRunning;
  currentChat.metrics = {
    ...(currentChat.metrics || {}),
    status: runStatus,
  };
  if (runStatus === "WAITING_INPUT") {
    currentChat.tip = "需要你的帮助";
    currentChat.loading = false;
  }
  const contextUsage = getRunContextUsage(run);
  if (contextUsage) {
    currentChat.contextUsage = {
      max: contextUsage.max,
      promptTokens: contextUsage.promptTokens,
    };
  }
  if (typeof run.durationMs === "number" && Number.isFinite(run.durationMs)) {
    currentChat.runDurationMs = run.durationMs;
    currentChat.runTimingSource = "ledger";
  }
}

function mergeSummaryChatsByRequestId(
  existing: CHAT.ChatItem[],
  incoming: CHAT.ChatItem[]
) {
  const result: CHAT.ChatItem[] = [];
  const seenRequestIds = new Set<string>();

  for (const chat of existing || []) {
    const requestId = chat.requestId;
    if (requestId && seenRequestIds.has(requestId)) {
      continue;
    }
    if (requestId) {
      seenRequestIds.add(requestId);
    }
    result.push(chat);
  }

  for (const chat of incoming) {
    const requestId = chat.requestId;
    if (!requestId || seenRequestIds.has(requestId)) {
      continue;
    }
    seenRequestIds.add(requestId);
    result.push(chat);
  }

  return result;
}

function hasReplayFrames(run: ConversationRunSummary | ConversationRunReplay) {
  return isRunReplay(run) && run.replayFrames.length > 0;
}

function getRunQuery(run: ConversationRunSummary | ConversationRunReplay) {
  return isRunReplay(run)
    ? String(run.queryText || "")
    : String(run.queryPreview || "");
}

function getRunSummary(run: ConversationRunSummary | ConversationRunReplay) {
  return isRunReplay(run)
    ? String(run.finalSummaryText || "")
    : String(run.finalSummaryPreview || "");
}

function getRunContextUsage(run: ConversationRunSummary | ConversationRunReplay) {
  return isRunReplay(run) ? run.contextUsage : undefined;
}

function isRunReplay(
  run: ConversationRunSummary | ConversationRunReplay
): run is ConversationRunReplay {
  return "replayFrames" in run;
}

function readEventData(frame?: ConversationReplayFrame | null) {
  if (!frame || !frame.resultMap || typeof frame.resultMap !== "object") {
    return undefined;
  }
  return frame.resultMap.eventData as MESSAGE.EventData | undefined;
}

function syncConclusionFromEventData(
  currentChat: CHAT.ChatItem,
  eventData: MESSAGE.EventData
) {
  const nested = eventData?.resultMap;
  const nestedType = nested?.messageType;
  if (nestedType === "result" || nestedType === "task_summary") {
    currentChat.conclusion = buildTaskFromEventData(eventData) as unknown as CHAT.Task;
  }
}

function toTimestamp(value?: string | null, fallback = Date.now()) {
  if (!value) {
    return fallback;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function resolveConversationAgentType(deepThink?: boolean) {
  return deepThink ? 3 : 5;
}

function normalizeRunStatus(status?: string | null) {
  const normalized = String(status || "").trim().toUpperCase();
  return normalized || "RUNNING";
}

function buildFallbackConclusionEventData(
  run: Pick<ConversationRunSummary | ConversationRunReplay, "requestId" | "finishedAt" | "startedAt">,
  summaryText: string
): MESSAGE.EventData {
  const resolvedSummary = resolveFallbackSummary(summaryText);
  const taskId = run.requestId || `${Date.now()}`;
  return {
    taskId,
    taskOrder: 1,
    messageType: "task",
    messageOrder: 1,
    messageId: `${run.requestId}-summary`,
    ...(resolvedSummary.artifactRefs.length ? { artifactRefs: resolvedSummary.artifactRefs } : {}),
    resultMap: {
      requestId: run.requestId,
      messageId: `${run.requestId}-summary`,
      messageType: "result",
      messageTime: String(toTimestamp(run.finishedAt, toTimestamp(run.startedAt))),
      finish: true,
      isFinal: true,
      result: resolvedSummary.summaryText,
      taskSummary: resolvedSummary.summaryText,
      fileList: resolvedSummary.fileList,
      ...(resolvedSummary.artifactKeys.length
        ? { artifactKeys: resolvedSummary.artifactKeys }
        : {}),
    } as unknown as MESSAGE.Task,
  };
}

function resolveFallbackSummary(rawSummaryText?: string | null) {
  const normalized = String(rawSummaryText || "");
  const delimiter = "$$$";
  const delimiterIndex = normalized.indexOf(delimiter);
  if (delimiterIndex === -1) {
    return {
      summaryText: normalized,
      fileList: [] as MESSAGE.FileInfo[],
      artifactRefs: [] as MESSAGE.ArtifactReference[],
      artifactKeys: [] as string[],
    };
  }

  const artifactKeys = normalized
    .slice(delimiterIndex + delimiter.length)
    .trim()
    .split(/[、,\r\n，]+/)
    .map((item) => item.trim())
    .filter(Boolean);

  return {
    summaryText: normalized,
    fileList: [] as MESSAGE.FileInfo[],
    artifactRefs: [] as MESSAGE.ArtifactReference[],
    artifactKeys,
  };
}
