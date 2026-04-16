import api from "./index";
import { combineData, handleTaskData } from "@/utils/chat";
import { artifactRefsToFileInfo } from "@/utils/historyArtifacts";

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

const normalizeHistoryPayload = (
  payload?: ConversationPayload
): ConversationPayload | undefined => {
  if (!payload) {
    return payload;
  }

  const mergedPayload = mergeArtifactRefsIntoPayload(payload);
  if (!mergedPayload) {
    return mergedPayload;
  }

  if (mergedPayload.messageType !== "plan") {
    return mergedPayload;
  }

  return {
    ...mergedPayload,
    resultMap: ensureHistoryPlanShape(mergedPayload.resultMap),
  };
};

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

const DIRECT_HISTORY_MESSAGE_TYPES = new Set([
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
]);

const cloneResultMap = (value: unknown) => {
  if (!value || typeof value !== "object") {
    return {} as Record<string, any>;
  }
  return { ...(value as Record<string, any>) };
};

const buildSyntheticTaskFromPayload = (
  turn: ConversationTurnItem,
  event: ConversationEventItem,
  payload: ConversationPayload
): MESSAGE.Task => {
  const messageType = payload.messageType || event.eventType;
  const payloadResultMap = cloneResultMap(payload.resultMap);
  const artifactFileInfo = artifactRefsToFileInfo(payload.artifactRefs);
  const resultMap: Record<string, any> =
    messageType === "deep_search" &&
    payloadResultMap.resultMap &&
    typeof payloadResultMap.resultMap === "object"
      ? {
          ...cloneResultMap(payloadResultMap.resultMap),
          fileInfo:
            payloadResultMap.resultMap.fileInfo || artifactFileInfo,
          isFinal: true,
        }
      : {
          ...payloadResultMap,
          fileInfo: payloadResultMap.fileInfo || artifactFileInfo,
          isFinal: true,
        };

  if (messageType === "task_summary" && !resultMap.taskSummary && event.contentText) {
    resultMap.taskSummary = event.contentText;
  }

  if (messageType === "result" && !resultMap.result && event.contentText) {
    resultMap.result = event.contentText;
  }

  return {
    messageTime:
      turn.finishedAt || turn.startedAt || String(event.seqNo),
    task: event.title || event.contentText || event.eventType,
    taskId: event.taskId || payload.taskId || undefined,
    messageType,
    resultMap: resultMap as MESSAGE.ResultMap,
    requestId: turn.requestId,
    messageId:
      payload.messageId || event.messageIdExt || `${turn.requestId}-${event.seqNo}`,
    finish: true,
    isFinal: true,
    toolThought:
      messageType === "tool_thought"
        ? event.contentText || resultMap.toolThought || ""
        : undefined,
    result:
      messageType === "task_summary" || messageType === "result"
        ? event.contentText || resultMap.taskSummary || resultMap.result || ""
        : undefined,
    id: payload.messageId || event.messageIdExt || `${turn.requestId}-${event.seqNo}`,
  } as MESSAGE.Task;
};

const appendDirectHistoryTask = (
  chatItem: CHAT.ChatItem,
  turn: ConversationTurnItem,
  event: ConversationEventItem,
  payload: ConversationPayload
) => {
  const messageType = payload.messageType || event.eventType;
  if (!DIRECT_HISTORY_MESSAGE_TYPES.has(messageType)) {
    return;
  }

  const nextTask = buildSyntheticTaskFromPayload(turn, event, payload);
  const groups = chatItem.multiAgent.tasks || (chatItem.multiAgent.tasks = []);

  if (!event.taskId) {
    groups.push([nextTask]);
    return;
  }

  const group = groups.find((items) => items[0]?.taskId === event.taskId);
  if (group) {
    group.push(nextTask);
    return;
  }

  groups.push([nextTask]);
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
  const chatItem = {
    sessionId,
    requestId: turn.requestId,
    query: turn.query,
    files: Array.isArray(turn.files) ? turn.files : [],
    response: turn.response || undefined,
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
      payload: normalizeHistoryPayload(event.payload),
    }));
  chatItem.timeline = buildTimelineEntries(orderedEvents);
  orderedEvents.forEach((event) => {
    const payload = event.payload;
    if (payload?.messageType) {
      combineData(payload as MESSAGE.EventData, chatItem);
      appendDirectHistoryTask(chatItem, turn, event, payload);
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
