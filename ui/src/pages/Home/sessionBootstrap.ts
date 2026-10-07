import type {
  ConversationHistoryPage,
  ConversationRunReplay,
  ConversationSessionItem,
} from "@/services/agentConversation";
import {
  hydrateConversationFromSummaryPage,
  mergeRunReplayIntoConversation,
} from "@/utils/conversationHistory";

/**
 * 首页只自动恢复当前 tab 中仍在执行的会话。
 * 已完成历史仍保持原来的行为，由用户从侧栏主动打开，避免刷新后跳到旧会话。
 */
export function resolveInitialSessionId(params: {
  recentSessions: ConversationSessionItem[];
  storedSessionId?: string | null;
}) {
  const storedSessionId = params.storedSessionId?.trim();
  if (!storedSessionId) {
    return null;
  }
  const storedSession = params.recentSessions.find(
    (session) => session.sessionId === storedSessionId
  );
  return storedSession && String(storedSession.status).toUpperCase() === "RUNNING"
    ? storedSession.sessionId
    : null;
}

export async function resolveInitialSession(params: {
  recentSessions: ConversationSessionItem[];
  storedSessionId?: string | null;
  allowRemoteLookup?: boolean;
  loadSessionDetail: (
    sessionId: string
  ) => Promise<ConversationHistoryPage | null>;
}): Promise<{
  sessionId: string | null;
  detail: ConversationHistoryPage | null;
}> {
  const storedSessionId = params.storedSessionId?.trim();
  if (!storedSessionId) {
    return {
      sessionId: null,
      detail: null,
    };
  }

  const storedSession = params.recentSessions.find(
    (session) => session.sessionId === storedSessionId
  );
  if (storedSession) {
    return {
      sessionId: resolveInitialSessionId(params),
      detail: null,
    };
  }

  // 普通 sessionStorage 指针可能只对应前端尚未发送消息的空草稿。
  // 只有活动 run 才需要探测首批历史列表之外的服务端会话。
  if (!params.allowRemoteLookup) {
    return {
      sessionId: null,
      detail: null,
    };
  }

  const detail = await params.loadSessionDetail(storedSessionId);
  if (String(detail?.status || "").toUpperCase() === "RUNNING") {
    return {
      sessionId: storedSessionId,
      detail,
    };
  }
  return {
    sessionId: null,
    detail: null,
  };
}

export async function hydrateSessionWithRunningReplay(
  page: ConversationHistoryPage,
  loadReplay: (requestId: string) => Promise<ConversationRunReplay>,
  onReplayError?: (requestId: string, error: unknown) => void
): Promise<CHAT.ConversationHistory> {
  const conversation = hydrateConversationFromSummaryPage(page);
  const runningChat = [...conversation.chatList]
    .reverse()
    .find(
      (chat) =>
        chat.requestId &&
        String(chat.metrics?.status || "").toUpperCase() === "RUNNING"
    );

  if (!runningChat?.requestId) {
    return conversation;
  }

  try {
    const replay = await loadReplay(runningChat.requestId);
    return mergeRunReplayIntoConversation(conversation, replay);
  } catch (error) {
    onReplayError?.(runningChat.requestId, error);
    return conversation;
  }
}
