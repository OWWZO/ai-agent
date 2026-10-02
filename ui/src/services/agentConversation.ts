import api from "./index";

const DEFAULT_DEVICE_ID = "device-default";

let runtimeDeviceId: string | null = DEFAULT_DEVICE_ID;

/**
 * 当前前端已经移除历史对话持久化，只保留一个轻量设备标识，
 * 兼容仍然需要该请求头的上传和流式接口。
 */
export function getDeviceId(): string {
  if (!runtimeDeviceId) {
    runtimeDeviceId = DEFAULT_DEVICE_ID;
  }
  return runtimeDeviceId;
}

export function getDeviceHeaders(): Record<string, string> {
  return { "X-Device-Id": getDeviceId() };
}

export interface VisitorBootstrapInfo {
  visitorId: string;
  username?: string;
  named: boolean;
}

export interface ConversationSessionItem {
  sessionId: string;
  title: string;
  status: string;
  latestQueryText: string;
  runCount: number;
  finishedRunCount: number;
  failedRunCount: number;
  startedAt: string;
  lastActiveAt: string;
}

export interface ConversationReplayFrame {
  reqId: string;
  status: string;
  finished: boolean;
  resultMap: {
    agentType?: string;
    multiAgent?: Record<string, unknown>;
    eventData?: MESSAGE.EventData;
  };
}

export interface ConversationContextUsage {
  max: number;
  promptTokens?: number;
}

export interface ConversationRunSummary {
  requestId: string;
  status: string;
  queryPreview?: string;
  finalSummaryPreview?: string;
  finalSummaryText?: string;
  entryAgent?: string;
  llmCallCount?: number;
  toolCallCount?: number;
  artifactCount?: number;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number | null;
  hasReplay: boolean;
}

export interface ConversationRunReplay {
  runUid?: string;
  requestId: string;
  sessionId?: string;
  entryAgent?: string;
  status: string;
  queryText?: string;
  finalSummaryText?: string;
  llmCallCount?: number;
  toolCallCount?: number;
  artifactCount?: number;
  errorCode?: string;
  errorMsg?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number | null;
  contextUsage?: ConversationContextUsage;
  replayFrames: ConversationReplayFrame[];
}

export interface ConversationHistoryPage {
  sessionId: string;
  title: string;
  status: string;
  deepThink: boolean;
  runCount: number;
  finishedRunCount: number;
  failedRunCount: number;
  latestRequestId?: string;
  latestQueryPreview?: string;
  latestSummaryPreview?: string;
  startedAt?: string;
  lastActiveAt?: string;
  runs: ConversationRunSummary[];
  nextCursor?: string | null;
  hasMore?: boolean;
}

export type ConversationHistoryRunDetail = ConversationRunReplay;

/** 旧 rich detail 的类型别名，保留给已有 replay 测试和调用方。 */
export interface ConversationHistoryDetail
  extends Omit<ConversationHistoryPage, "runs"> {
  runs: ConversationHistoryRunDetail[];
}

export interface ConversationHistoryQuery {
  limit?: number;
  after?: string | null;
}

export const visitorApi = {
  bootstrap: () =>
    api.get<VisitorBootstrapInfo>(`/api/agent/visitor/bootstrap`) as unknown as Promise<VisitorBootstrapInfo>,
  naming: (username: string) =>
    api.post<VisitorBootstrapInfo>(`/api/agent/visitor/naming`, { username }) as unknown as Promise<VisitorBootstrapInfo>,
};

export const conversationHistoryApi = {
  listSessions: (limit = 20) =>
    api.get<ConversationSessionItem[]>(
      `/api/agent/conversation/sessions?limit=${limit}`
    ) as unknown as Promise<ConversationSessionItem[]>,
  getSessionDetail: (sessionId: string, params: ConversationHistoryQuery = {}) =>
    api.get<ConversationHistoryPage>(
      `/api/agent/conversation/sessions/${encodeURIComponent(sessionId)}`,
      params
    ) as unknown as Promise<ConversationHistoryPage>,
  getRunReplay: (requestId: string) =>
    api.get<ConversationRunReplay>(
      `/api/agent/conversation/runs/${encodeURIComponent(requestId)}/replay`
    ) as unknown as Promise<ConversationRunReplay>,
};
