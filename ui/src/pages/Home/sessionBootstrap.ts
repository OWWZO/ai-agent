import type { ConversationSessionItem } from "@/services/agentConversation";

/**
 * 首页首屏始终保持欢迎态，不自动恢复历史会话。
 * 历史会话仅在用户主动点击侧边栏时再加载。
 */
export function resolveInitialSessionId(params: {
  recentSessions: ConversationSessionItem[];
  storedSessionId?: string | null;
}) {
  void params;
  return null;
}
