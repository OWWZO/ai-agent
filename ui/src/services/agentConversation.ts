import api from "./index";
import { combineData, handleTaskData } from "@/utils/chat";
import { resolveDeepSearchStage } from "@/utils/deepSearch";
import { artifactRefsToFileInfo, normalizeHistoryFile } from "@/utils/historyArtifacts";

const DEFAULT_DEVICE_ID = "device-default";

let runtimeDeviceId: string | null = DEFAULT_DEVICE_ID;

/**
 * 获取默认设备标识。
 * 这里先收敛为固定值，保证匿名历史在不同浏览器中可见。
 */
export function getDeviceId(): string {
  if (!runtimeDeviceId) {
    runtimeDeviceId = DEFAULT_DEVICE_ID;
  }
  return runtimeDeviceId;
}

/**
 * 获取包含设备标识的请求头
 */
export function getDeviceHeaders(): Record<string, string> {
  return { "X-Device-Id": getDeviceId() };
}

// ---- 会话相关类型 ----

export interface ConversationListItem {
  id: number;
  sessionId: string;
  title: string;
  agentType: number;
  productType: string;
  messageCount: number;
  pinned: number;
  lastMessagePreview?: string;
  role?: CHAT.ConversationRole | null;
  createTime: string;
  updateTime: string;
}

export interface ArtifactReferenceItem {
  artifactType?: string;
  displayName?: string;
  resourceKey?: string;
  downloadUrl?: string | null;
  previewUrl?: string | null;
  fileSize?: number | null;
  mimeType?: string | null;
  missing?: boolean;
  missingReason?: string | null;
}

type ConversationPayload = Omit<Partial<MESSAGE.EventData>, "resultMap"> & {
  artifactRefs?: ArtifactReferenceItem[];
  resultMap?: Record<string, any>;
};

export interface ConversationEventItem {
  seqNo: number;
  eventType: string;
  eventSubType?: string | null;
  displayArea: string;
  taskId?: string | null;
  taskOrder?: number | null;
  messageIdExt?: string | null;
  title?: string | null;
  contentText?: string | null;
  status: string;
  isFinal: number;
  payload?: ConversationPayload;
}

export interface ConversationTurnItem {
  requestId: string;
  sortOrder: number;
  query: string;
  files?: CHAT.TFile[];
  generatedFiles?: CHAT.TFile[];
  agentType: number;
  status: number;
  forceStop: number;
  response?: string;
  metrics?: Record<string, any>;
  startedAt?: string;
  finishedAt?: string;
  events: ConversationEventItem[];
}

export interface ConversationDetail {
  conversation: ConversationListItem;
  turns: ConversationTurnItem[];
}

const mergeArtifactRefsIntoPayload = (
  payload?: ConversationPayload
): ConversationPayload | undefined => {
  if (!payload) {
    return payload;
  }

  const artifactRefs = extractArtifactRefs(payload);
  if (!artifactRefs.length) {
    return payload;
  }

  const nextResultMap = {...(payload.resultMap || {}),} as Record<string, any>;
  const nestedResultMap =
    nextResultMap.resultMap && typeof nextResultMap.resultMap === "object"
      ? { ...nextResultMap.resultMap }
      : undefined;
  const normalizedFileInfo = artifactRefsToFileInfo(artifactRefs);

  if (nestedResultMap || payload.messageType === "task") {
    nextResultMap.resultMap = {
      ...(nestedResultMap || {}),
      fileInfo: normalizedFileInfo,
    };
  } else {
    nextResultMap.fileInfo = normalizedFileInfo;
  }

  return {
    ...payload,
    artifactRefs,
    resultMap: nextResultMap,
  };
};

const ensureHistoryPlanShape = (planLike: any) => {
  if (!planLike || typeof planLike !== "object") {
    return planLike;
  }

  const steps = Array.isArray(planLike.steps) ? [...planLike.steps] : [];
  const hasStages = Array.isArray(planLike.stages) && planLike.stages.length > 0;
  const stages = hasStages ? [...planLike.stages] : [...steps];
  const stepStatus = Array.isArray(planLike.stepStatus)
    ? [...planLike.stepStatus]
    : Array.from({ length: stages.length }, () => "completed");

  return {
    ...planLike,
    stages,
    // 历史最终态如果只有步骤列表而没有 stage 名称，直接把步骤文案当作展示标题。
    steps: hasStages ? steps : Array.from({ length: stages.length }, () => ""),
    stepStatus,
  };
};

const LEGACY_TASK_MESSAGE_TYPES = new Set([
  "tool_result",
  "browser",
  "code",
  "html",
  "file",
  "knowledge",
  "deep_search",
  "markdown",
  "ppt",
  "data_analysis",
  "task_summary",
  "result",
  "agent_stream",
  "tool_thought",
]);

const extractArtifactRefs = (payload: ConversationPayload): ArtifactReferenceItem[] => {
  const directRefs = normalizeArtifactRefs(payload.artifactRefs);
  if (directRefs.length) {
    return directRefs;
  }

  const outerResultMap = (payload.resultMap || {}) as Record<string, any>;
  const nestedResultMap =
    outerResultMap.resultMap && typeof outerResultMap.resultMap === "object"
      ? (outerResultMap.resultMap as Record<string, any>)
      : undefined;
  const legacyFiles =
    ensureArray(nestedResultMap?.fileInfo) ||
    ensureArray(nestedResultMap?.fileList) ||
    ensureArray(outerResultMap.fileInfo) ||
    ensureArray(outerResultMap.fileList);

  return normalizeArtifactRefs(legacyFiles);
};

const ensureArray = (value: unknown): any[] | undefined => {
  return Array.isArray(value) && value.length ? value : undefined;
};

const normalizeArtifactRefs = (artifactRefs?: any[]): ArtifactReferenceItem[] => {
  if (!artifactRefs?.length) {
    return [];
  }

  return artifactRefs.map((artifact) => {
    const previewUrl =
      artifact.previewUrl || artifact.domainUrl || artifact.url || artifact.ossUrl || artifact.downloadUrl || null;
    const downloadUrl =
      artifact.downloadUrl || artifact.ossUrl || artifact.domainUrl || artifact.url || null;
    const resourceKey =
      artifact.resourceKey || artifact.ossUrl || artifact.downloadUrl || artifact.domainUrl || artifact.fileName || artifact.name || "";
    const missing = Boolean(artifact.missing) || (!previewUrl && !downloadUrl);

    return {
      artifactType: artifact.artifactType || artifact.type,
      displayName: artifact.displayName || artifact.fileName || artifact.name || resourceKey || "未命名文件",
      resourceKey,
      previewUrl,
      downloadUrl,
      fileSize: typeof artifact.fileSize === "number" ? artifact.fileSize : Number(artifact.fileSize) || null,
      mimeType: artifact.mimeType || null,
      missing,
      missingReason: artifact.missingReason || (missing ? "引用资源不存在或已失效" : null),
    };
  });
};

const normalizeTurnFiles = (files?: unknown[]): CHAT.TFile[] => {
  if (!Array.isArray(files) || !files.length) {
    return [];
  }

  return files
    .map((file) => normalizeHistoryFile(file))
    .filter((file): file is CHAT.TFile => Boolean(file));
};

const cloneRecord = (value: unknown): Record<string, any> => {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return { ...(value as Record<string, any>) };
};

const isAgentResponseLikeTaskResultMap = (value: Record<string, any>) => {
  return Boolean(
    value &&
      typeof value.messageType === "string" &&
      (
        value.finish !== undefined ||
        value.resultMap !== undefined ||
        value.task !== undefined ||
        value.toolThought !== undefined ||
        value.taskSummary !== undefined ||
        value.result !== undefined ||
        value.plan !== undefined
      )
  );
};

const resolveTaskMessageType = (
  payload: ConversationPayload,
  event: ConversationEventItem,
  payloadResultMap: Record<string, any>
) => {
  if (
    typeof payloadResultMap.messageType === "string" &&
    payloadResultMap.messageType
  ) {
    return payloadResultMap.messageType;
  }

  const nestedResultMap =
    payloadResultMap.resultMap && typeof payloadResultMap.resultMap === "object"
      ? (payloadResultMap.resultMap as Record<string, any>)
      : undefined;
  if (
    typeof nestedResultMap?.messageType === "string" &&
    nestedResultMap.messageType
  ) {
    return nestedResultMap.messageType;
  }

  if (payload.messageType && payload.messageType !== "task") {
    return payload.messageType;
  }

  return event.eventType;
};

const normalizeSearchResult = (value: unknown) => {
  const searchResult = cloneRecord(value);
  const queries = Array.isArray(searchResult.query)
    ? searchResult.query
    : searchResult.query != null
      ? [searchResult.query]
      : [];
  const docs = Array.isArray(searchResult.docs)
    ? searchResult.docs
    : searchResult.docs != null
      ? [searchResult.docs]
      : [];

  return {
    ...searchResult,
    query: queries.map((item) => String(item ?? "").trim()).filter(Boolean),
    docs,
  };
};

const normalizeTaskInnerResultMap = (
  taskMessageType: string,
  rawInnerResultMap: Record<string, any>,
  event: ConversationEventItem,
  artifactFileInfo: ReturnType<typeof artifactRefsToFileInfo>
) => {
  const nextInnerResultMap = cloneRecord(rawInnerResultMap);
  nextInnerResultMap.isFinal = true;

  if (taskMessageType === "deep_search") {
    nextInnerResultMap.messageType = resolveDeepSearchStage(
      nextInnerResultMap.messageType,
      event.eventSubType
    );
    nextInnerResultMap.searchResult = normalizeSearchResult(
      nextInnerResultMap.searchResult
    );
  } else {
    nextInnerResultMap.messageType =
      nextInnerResultMap.messageType || taskMessageType;
  }

  if (artifactFileInfo.length && !Array.isArray(nextInnerResultMap.fileInfo)) {
    nextInnerResultMap.fileInfo = artifactFileInfo;
  }

  if (
    taskMessageType === "task_summary" &&
    !nextInnerResultMap.taskSummary &&
    event.contentText
  ) {
    nextInnerResultMap.taskSummary = event.contentText;
  }

  if (
    ["result", "agent_stream"].includes(taskMessageType) &&
    !nextInnerResultMap.result &&
    event.contentText
  ) {
    nextInnerResultMap.result = event.contentText;
  }

  if (
    taskMessageType === "tool_thought" &&
    !nextInnerResultMap.toolThought &&
    event.contentText
  ) {
    nextInnerResultMap.toolThought = event.contentText;
  }

  return nextInnerResultMap;
};

const normalizeTaskOuterResultMap = (
  taskMessageType: string,
  rawOuterResultMap: Record<string, any>,
  event: ConversationEventItem,
  messageId: string,
  artifactFileInfo: ReturnType<typeof artifactRefsToFileInfo>
) => {
  const nextOuterResultMap = {
    ...cloneRecord(rawOuterResultMap),
    messageType: taskMessageType,
    messageId: rawOuterResultMap.messageId || messageId,
    finish: rawOuterResultMap.finish ?? true,
    isFinal: true,
    messageTime: rawOuterResultMap.messageTime || String(event.seqNo),
  } as Record<string, any>;

  const nestedResultMap = normalizeTaskInnerResultMap(
    taskMessageType,
    cloneRecord(nextOuterResultMap.resultMap),
    event,
    artifactFileInfo
  );
  nextOuterResultMap.resultMap = nestedResultMap;

  if (taskMessageType === "tool_thought") {
    nextOuterResultMap.toolThought =
      nextOuterResultMap.toolThought ||
      nestedResultMap.toolThought ||
      event.contentText ||
      "";
  }

  if (taskMessageType === "task_summary") {
    nextOuterResultMap.taskSummary =
      nextOuterResultMap.taskSummary ||
      nestedResultMap.taskSummary ||
      event.contentText ||
      "";
  }

  if (["result", "agent_stream"].includes(taskMessageType)) {
    nextOuterResultMap.result =
      nextOuterResultMap.result ||
      nestedResultMap.result ||
      event.contentText ||
      "";
  }

  if (taskMessageType === "task" && !nextOuterResultMap.task) {
    nextOuterResultMap.task = event.title || event.contentText || event.eventType;
  }

  return nextOuterResultMap;
};

const buildLegacyTaskOuterResultMap = (
  taskMessageType: string,
  rawResultMap: Record<string, any>,
  event: ConversationEventItem,
  messageId: string,
  artifactFileInfo: ReturnType<typeof artifactRefsToFileInfo>
) => {
  const outerResultMap: Record<string, any> = {
    messageType: taskMessageType,
    messageId,
    finish: true,
    isFinal: true,
    messageTime: rawResultMap.messageTime || String(event.seqNo),
  };

  const innerResultMap = normalizeTaskInnerResultMap(
    taskMessageType,
    rawResultMap,
    event,
    artifactFileInfo
  );
  outerResultMap.resultMap = innerResultMap;

  if (taskMessageType === "tool_thought") {
    outerResultMap.toolThought = innerResultMap.toolThought || event.contentText || "";
  }

  if (taskMessageType === "task_summary") {
    outerResultMap.taskSummary =
      innerResultMap.taskSummary || event.contentText || "";
  }

  if (["result", "agent_stream"].includes(taskMessageType)) {
    outerResultMap.result = innerResultMap.result || event.contentText || "";
  }

  if (taskMessageType === "task") {
    outerResultMap.task = rawResultMap.task || event.title || event.contentText || event.eventType;
  }

  return outerResultMap;
};

const normalizeTaskPayload = (
  payload: ConversationPayload,
  event: ConversationEventItem,
  fallbackMessageId: string
): ConversationPayload => {
  const artifactRefs = extractArtifactRefs(payload);
  const artifactFileInfo = artifactRefsToFileInfo(artifactRefs);
  const payloadResultMap = cloneRecord(payload.resultMap);
  const taskMessageType = resolveTaskMessageType(payload, event, payloadResultMap);
  const messageId = payload.messageId || event.messageIdExt || fallbackMessageId;
  const outerResultMap = isAgentResponseLikeTaskResultMap(payloadResultMap)
    ? normalizeTaskOuterResultMap(taskMessageType, payloadResultMap, event, messageId, artifactFileInfo)
    : buildLegacyTaskOuterResultMap(taskMessageType, payloadResultMap, event, messageId, artifactFileInfo);

  return {
    ...payload,
    messageType: "task",
    messageId,
    taskId: payload.taskId ?? event.taskId ?? undefined,
    taskOrder: payload.taskOrder ?? event.taskOrder ?? undefined,
    artifactRefs,
    resultMap: outerResultMap,
  };
};

const normalizeHistoryPayload = (
  payload: ConversationPayload | undefined,
  event: ConversationEventItem,
  fallbackMessageId: string
): ConversationPayload | undefined => {
  if (!payload) {
    return payload;
  }

  const mergedPayload = mergeArtifactRefsIntoPayload(payload);
  if (!mergedPayload) {
    return mergedPayload;
  }

  const payloadMessageType = mergedPayload.messageType || event.eventType;
  let payloadWithMessageType = mergedPayload;
  if (mergedPayload.messageType !== payloadMessageType) {
    payloadWithMessageType = {
      ...mergedPayload,
      messageType: payloadMessageType,
    };
  }

  if (payloadMessageType === "plan") {
    return {
      ...payloadWithMessageType,
      messageId: payloadWithMessageType.messageId || event.messageIdExt || fallbackMessageId,
      taskId: undefined,
      taskOrder: undefined,
      resultMap: ensureHistoryPlanShape(payloadWithMessageType.resultMap),
    };
  }

  if (payloadMessageType === "plan_thought") {
    const nextResultMap = cloneRecord(payloadWithMessageType.resultMap);
    return {
      ...payloadWithMessageType,
      messageId: payloadWithMessageType.messageId || event.messageIdExt || fallbackMessageId,
      taskId: undefined,
      taskOrder: undefined,
      resultMap: {
        ...nextResultMap,
        planThought:
          nextResultMap.planThought || event.contentText || "",
        isFinal: true,
      },
    };
  }

  if (
    payloadMessageType === "task" ||
    LEGACY_TASK_MESSAGE_TYPES.has(
      payloadMessageType
    )
  ) {
    return normalizeTaskPayload(payloadWithMessageType, event, fallbackMessageId);
  }

  return payloadWithMessageType;
};

const buildTimelineEntries = (
  events: Array<ConversationEventItem & { payload?: ConversationPayload }>
): CHAT.TimelineEntry[] => {
  return events.map((event) => ({
    seq: event.seqNo,
    type: event.eventType,
    subType: event.eventSubType || undefined,
    area: event.displayArea,
    title: event.title || event.eventType,
    content: event.contentText || undefined,
    taskId: event.taskId || undefined,
    taskOrder: event.taskOrder || undefined,
    messageIdExt: event.messageIdExt || undefined,
    isFinal: event.isFinal === 1,
    status: event.status,
    payload: event.payload,
  }));
};

export interface PageResult<T> {
  total: number;
  list: T[];
}

export interface FixRoleItem {
  agentId: string;
  agentName: string;
  description?: string;
  defaultRole: boolean;
}

const buildHistoryConclusionFallback = (
  turn: ConversationTurnItem
): CHAT.Task | undefined => {
  const summary = turn.response?.trim();
  if (!summary) {
    return undefined;
  }

  const messageTime =
    turn.finishedAt || turn.startedAt || `${turn.sortOrder}`;
  const messageId = `${turn.requestId}-final-summary`;

  return {
    id: messageId,
    messageId,
    requestId: turn.requestId,
    messageTime,
    messageType: "task_summary",
    taskId: undefined,
    finish: true,
    isFinal: true,
    result: summary,
    resultMap: {
      taskSummary: summary,
      result: summary,
      isFinal: true,
      fileInfo: [],
    } as MESSAGE.ResultMap,
  } as CHAT.Task;
};

// ---- API 方法 ----

export const conversationApi = {
  /**
   * 会话列表
   */
  list: (pageNo = 1, pageSize = 20) =>
    api.get<PageResult<ConversationListItem>>(
      `/api/agent/conversation/list`,
      {
        pageNo,
        pageSize,
        deviceId: getDeviceId()
      }
    ),

  /**
   * 会话详情(含所有消息)
   */
  detail: (sessionId: string) =>
    api.get<ConversationDetail>(
      `/api/agent/conversation/detail`,
      {
        sessionId,
        deviceId: getDeviceId()
      }
    ),

  /**
   * 创建会话
   */
  create: (data: {
    sessionId: string;
    title?: string;
    agentType: number;
    productType: string;
    aiAgentId?: string;
  }) => api.post<ConversationListItem>(`/api/agent/conversation/create`, data),

  /**
   * 重命名
   */
  rename: (sessionId: string, title: string) =>
    api.put<boolean>(`/api/agent/conversation/rename`, {
      sessionId,
      title
    }),

  /**
   * 删除
   */
  delete: (sessionId: string) =>
    api.delete<boolean>(`/api/agent/conversation/${sessionId}`, {deviceId: getDeviceId(),}),

  /**
   * 置顶/取消
   */
  pin: (sessionId: string, pinned: boolean) =>
    api.put<boolean>(
      `/api/agent/conversation/pin?sessionId=${sessionId}&pinned=${pinned}`
    ),

  /**
   * 匿名迁移到用户
   */
  migrate: (userId: number) =>
    api.post<number>(
      `/api/agent/conversation/migrate?userId=${userId}&deviceId=${getDeviceId()}`
    ),
};

export const roleLibraryApi = {list: () => api.get<FixRoleItem[]>(`/api/agent/role-library/list`),};

// ---- 消息还原工具函数 ----

/**
 * 将后端 turn 还原为前端 CHAT.ChatItem 格式
 */
export function restoreTurn(sessionId: string, turn: ConversationTurnItem): CHAT.ChatItem {
  const isStructuredTurn = turn.agentType === 1 || turn.agentType === 2;
  const chatItem = {
    sessionId,
    requestId: turn.requestId,
    query: turn.query,
    files: normalizeTurnFiles(turn.files),
    generatedFiles: normalizeTurnFiles(turn.generatedFiles),
    response: isStructuredTurn ? undefined : turn.response || undefined,
    loading: false,
    forceStop: turn.forceStop === 1,
    agentType: turn.agentType,
    tasks: [],
    multiAgent: { tasks: [] },
    metrics: turn.metrics,
    startedAt: turn.startedAt,
    finishedAt: turn.finishedAt,
  } as CHAT.ChatItem;

  const orderedEvents = [...(turn.events || [])]
    .sort((left, right) => left.seqNo - right.seqNo)
    .map((event) => ({
      ...event,
      payload: normalizeHistoryPayload(
        event.payload,
        event,
        `${turn.requestId}-${event.seqNo}`
      ),
    }));
  chatItem.timeline = buildTimelineEntries(orderedEvents);
  orderedEvents.forEach((event) => {
    const payload = event.payload;
    if (payload?.messageType) {
      combineData(payload as MESSAGE.EventData, chatItem);
    }
  });

  const isPlanSolve = turn.agentType === 1;
  if (
    chatItem.multiAgent?.tasks?.length ||
    chatItem.multiAgent?.plan ||
    chatItem.multiAgent?.plan_thought
  ) {
    handleTaskData(chatItem, isPlanSolve, chatItem.multiAgent);
  }

  if (chatItem.tasks) {
    chatItem.tasks = chatItem.tasks.map((group: any[]) =>
      group.map((task: any) => ({
        ...task,
        finish: true,
        isFinal: true,
        resultMap: {
          ...(task.resultMap || {}),
          isFinal: true,
        },
      }))
    );
  }

  if (isStructuredTurn && !chatItem.conclusion) {
    chatItem.conclusion = buildHistoryConclusionFallback(turn);
  }

  return chatItem;
}

/**
 * 将后端 turn 列表还原为前端 ChatItem 列表
 */
export function restoreTurns(sessionId: string, turns: ConversationTurnItem[]): CHAT.ChatItem[] {
  return [...turns]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((turn) => restoreTurn(sessionId, turn));
}
