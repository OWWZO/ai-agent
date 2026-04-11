import api from "./index";
import { handleTaskData } from "@/utils/chat";

let runtimeDeviceId: string | null = null;

/**
 * 获取或生成设备标识（仅内存，不落本地存储）
 */
export function getDeviceId(): string {
  if (!runtimeDeviceId) {
    runtimeDeviceId = `device-${crypto.randomUUID()}`;
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

export interface MessageItem {
  requestId: string;
  sessionId: string;
  sortOrder: number;
  query: string;
  agentType: number;
  status: number;
  forceStop: number;
  response?: string;
  thought?: string;
  planJson?: string;
  tasksJson?: string;
  multiAgentJson?: string;
  conclusionJson?: string;
  planListJson?: string;
  renderSnapshotJson?: string;
  metricsJson?: string;
  filesJson?: string;
  startedAt?: string;
  finishedAt?: string;
  createTime: string;
}

export interface ConversationDetail {
  conversation: ConversationListItem;
  messages: MessageItem[];
}

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
      { pageNo, pageSize, deviceId: getDeviceId() }
    ),

  /**
   * 会话详情(含所有消息)
   */
  detail: (sessionId: string) =>
    api.get<ConversationDetail>(
      `/api/agent/conversation/detail`,
      { sessionId, deviceId: getDeviceId() }
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
    api.put<boolean>(`/api/agent/conversation/rename`, { sessionId, title }),

  /**
   * 删除
   */
  delete: (sessionId: string) =>
    api.delete<boolean>(`/api/agent/conversation/${sessionId}`, {
      deviceId: getDeviceId(),
    }),

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

export const roleLibraryApi = {
  list: () => api.get<FixRoleItem[]>(`/api/agent/role-library/list`),
};

// ---- 消息还原工具函数 ----

/**
 * 将后端 MessageItem 还原为前端 CHAT.ChatItem 格式
 */
export function restoreMessage(msg: MessageItem): CHAT.ChatItem {
  if (msg.renderSnapshotJson) {
    const snapshot = JSON.parse(msg.renderSnapshotJson) as CHAT.RenderSnapshotV1;
    if (snapshot?.v === 1) {
      return {
        sessionId: msg.sessionId,
        requestId: msg.requestId,
        query: msg.query,
        files: msg.filesJson ? JSON.parse(msg.filesJson) : [],
        response: msg.response || undefined,
        thought: snapshot.thought || msg.thought || undefined,
        plan: snapshot.plan || undefined,
        tasks: [],
        multiAgent: {
          tasks: snapshot.tasks || [],
          plan: snapshot.plan,
          plan_thought: snapshot.thought,
        },
        conclusion: snapshot.conclusion || undefined,
        planList: undefined,
        loading: false,
        forceStop: msg.forceStop === 1,
        agentType: msg.agentType,
        renderSnapshot: snapshot,
        timeline: snapshot.timeline || [],
        metrics: msg.metricsJson ? JSON.parse(msg.metricsJson) : undefined,
        startedAt: msg.startedAt,
        finishedAt: msg.finishedAt,
      } as CHAT.ChatItem;
    }
  }

  return {
    sessionId: msg.sessionId,
    requestId: msg.requestId,
    query: msg.query,
    files: msg.filesJson ? JSON.parse(msg.filesJson) : [],
    response: msg.response || undefined,
    thought: msg.thought || undefined,
    plan: msg.planJson ? JSON.parse(msg.planJson) : undefined,
    tasks: msg.tasksJson ? JSON.parse(msg.tasksJson) : [],
    multiAgent: msg.multiAgentJson
      ? JSON.parse(msg.multiAgentJson)
      : { tasks: [] },
    conclusion: msg.conclusionJson
      ? JSON.parse(msg.conclusionJson)
      : undefined,
    planList: msg.planListJson ? JSON.parse(msg.planListJson) : undefined,
    loading: false,
    forceStop: msg.forceStop === 1,
    agentType: msg.agentType,
  } as CHAT.ChatItem;
}

/**
 * 将后端消息列表还原为前端 ChatItem 列表
 * 所有消息标记为已完成(loading=false, task.finish=true)
 */
export function restoreMessages(messages: MessageItem[]): CHAT.ChatItem[] {
  return [...messages]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((msg) => {
    const chatItem = restoreMessage(msg);
    const isPlanSolve = msg.agentType === 1;
    if (chatItem.multiAgent?.tasks?.length) {
      handleTaskData(chatItem, isPlanSolve, chatItem.multiAgent);
    }
    if (chatItem.tasks) {
      chatItem.tasks = chatItem.tasks.map((group: any[]) =>
        group.map((task: any) => ({ ...task, finish: true, isFinal: true }))
      );
    }
    return chatItem;
  });
}
