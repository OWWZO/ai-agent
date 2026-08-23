import type {
  ConversationHistoryDetail,
  ConversationHistoryRunDetail,
  ConversationReplayFrame,
} from "@/services/agentConversation";
import { GENERIC_TASK_PRODUCT } from "@/utils/constants";

import { buildConversationTaskData, buildTaskFromEventData, combineData } from "./chat";
import { artifactRefsToFileInfo } from "./taskArtifacts";

/**
 * 会话详情为空时，首页应保持当前空白/初始态，不自动切到其他会话。
 */
export function isHistoryDetailEmpty(detail?: ConversationHistoryDetail | null) {
  return !detail || !Array.isArray(detail.runs) || detail.runs.length === 0;
}

export function toConversationHistoryTitle(detail?: Pick<ConversationHistoryDetail, "title" | "sessionId"> | null) {
  if (!detail) {
    return "新对话";
  }
  const normalizedTitle = String(detail.title || "").trim();
  if (normalizedTitle) {
    return normalizedTitle;
  }
  return detail.sessionId ? `会话 ${detail.sessionId}` : "新对话";
}

/**
 * 将后端 session detail 还原成当前前端会话快照。
 * 这里严格复用 combineData，不为历史单独维护第二套事件分支。
 */
export function hydrateConversationFromReplayFrames(
  detail: ConversationHistoryDetail
): CHAT.ConversationHistory {
  const chatList = (detail.runs || []).map((run) => hydrateRun(detail, run));
  const createdAt = toTimestamp(detail.startedAt);
  const updatedAt = toTimestamp(detail.lastActiveAt, createdAt);
  const title = toConversationHistoryTitle(detail);

  return {
    id: `conversation-${detail.sessionId}`,
    sessionId: detail.sessionId,
    title,
    productType: detail.outputStyle || GENERIC_TASK_PRODUCT.type,
    deepThink: Boolean(detail.deepThink),
    role: detail.role || null,
    createdAt,
    updatedAt,
    chatTitle: title,
    chatList,
    dataChatList: [],
  };
}

function hydrateRun(
  detail: ConversationHistoryDetail,
  run: ConversationHistoryRunDetail
): CHAT.ChatItem {
  // 历史 run 先还原为与实时流相同的空状态，再逐帧复用 combineData，确保历史和实时使用同一事件语义。
  const runStatus = normalizeRunStatus(run.status);
  const isRunning = runStatus === "RUNNING";
  const currentChat: CHAT.ChatItem = {
    sessionId: detail.sessionId,
    requestId: run.requestId,
    query: run.queryText || "",
    files: [],
    responseType: "txt",
    agentType: resolveConversationAgentType(detail.outputStyle, detail.deepThink),
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
    metrics: { status: runStatus },
  } as CHAT.ChatItem;

  for (const frame of run.replayFrames || []) {
    const eventData = readEventData(frame);
    if (!eventData) {
      continue;
    }
    combineData(eventData, currentChat);
    syncConclusionFromEventData(currentChat, eventData);
  }

  if (!isRunning && !currentChat.conclusion && run.finalSummaryText) {
    // 旧数据或失败 run 可能没有 result frame，用 run 终态摘要补一条与实时 result 同构的事件。
    const fallbackEventData = buildFallbackConclusionEventData(run);
    combineData(fallbackEventData, currentChat);
    syncConclusionFromEventData(currentChat, fallbackEventData);
  }

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
  if (run.contextUsage) {
    currentChat.contextUsage = { ...run.contextUsage };
  }

  return buildConversationTaskData(currentChat, detail.deepThink).currentChat;
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

function resolveConversationAgentType(outputStyle?: string, deepThink?: boolean) {
  if (outputStyle === "chat") {
    return 1;
  }
  return deepThink ? 2 : 1;
}

function normalizeRunStatus(status?: string | null) {
  const normalized = String(status || "").trim().toUpperCase();
  return normalized || "RUNNING";
}

function buildFallbackConclusionEventData(
  run: ConversationHistoryRunDetail
): MESSAGE.EventData {
  const resolvedSummary = resolveFallbackSummary(run.finalSummaryText);
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
    };
  }

  const summaryText = normalized.slice(0, delimiterIndex).trim();
  const artifactSection = normalized.slice(delimiterIndex + delimiter.length).trim();
  const artifactKeys = artifactSection
    .split(/[、,\r\n，]+/)
    .map((item) => item.trim())
    .filter(Boolean);

  const artifactRefs = artifactKeys.map((artifactKey) => {
    const [toolCallId, ...fileNameParts] = artifactKey.split("::");
    const fileName = fileNameParts.join("::").trim();
    return {
      resourceKey: artifactKey,
      displayName: fileName || artifactKey,
      downloadUrl: null,
      previewUrl: null,
      missing: true,
      missingReason: "history_summary_artifact_key_only",
      toolCallId: toolCallId || undefined,
    } as unknown as MESSAGE.ArtifactReference;
  });

  return {
    summaryText,
    fileList: artifactRefsToFileInfo(artifactRefs),
    artifactRefs,
  };
}
