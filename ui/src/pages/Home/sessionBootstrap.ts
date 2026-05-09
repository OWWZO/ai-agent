import type { ConversationSessionItem } from "@/services/agentConversation";

/**
 * 仅从当前 visitor 的可访问会话列表里解析首屏默认会话。
 */
export function resolveInitialSessionId(params: {
  recentSessions: ConversationSessionItem[];
  storedSessionId?: string | null;
}) {
  if (params.recentSessions.length === 0) {
    return null;
  }

  if (
    params.storedSessionId &&
    params.recentSessions.some(
      (session) => session.sessionId === params.storedSessionId
    )
  ) {
    return params.storedSessionId;
  }

  return params.recentSessions[0].sessionId;
}
