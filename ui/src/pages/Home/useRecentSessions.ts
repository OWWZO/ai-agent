import { useCallback, useState } from "react";

import {
  conversationHistoryApi,
  type ConversationSessionItem,
} from "@/services/agentConversation";

export function useRecentSessions() {
  const [recentSessions, setRecentSessions] = useState<ConversationSessionItem[]>(
    []
  );
  const [recentSessionsLoading, setRecentSessionsLoading] = useState(false);

  const refreshRecentSessions = useCallback(() => {
    setRecentSessionsLoading(true);
    return conversationHistoryApi
      .listSessions(20)
      .then((sessions) => {
        setRecentSessions(sessions || []);
      })
      .catch((error) => {
        console.error("加载近期会话失败", error);
      })
      .finally(() => {
        setRecentSessionsLoading(false);
      });
  }, []);

  return {
    recentSessions,
    recentSessionsLoading,
    refreshRecentSessions,
  };
}
