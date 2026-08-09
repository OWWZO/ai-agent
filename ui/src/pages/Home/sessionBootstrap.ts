import type { ConversationSessionItem } from "@/services/agentConversation";

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
