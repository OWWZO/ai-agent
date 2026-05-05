import type {
  ConversationHistoryDetail,
  ConversationHistoryRunDetail,
  ConversationReplayFrame,
} from "@/services/agentConversation";

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
    productType: detail.outputStyle || "chat",
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
  const runStatus = normalizeRunStatus(run.status);
  const currentChat: CHAT.ChatItem = {
    sessionId: detail.sessionId,
    requestId: run.requestId,
    query: run.queryText || "",
    files: [],
    responseType: "txt",
    agentType: resolveConversationAgentType(detail.outputStyle, detail.deepThink),
    loading: false,
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

  if (!currentChat.conclusion && run.finalSummaryText) {
    const fallbackEventData = buildFallbackConclusionEventData(run);
    combineData(fallbackEventData, currentChat);
    syncConclusionFromEventData(currentChat, fallbackEventData);
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
