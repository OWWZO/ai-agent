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

  const refreshRecentSessions = useCallback((enabled = true) => {
    if (!enabled) {
      return Promise.resolve([] as ConversationSessionItem[]);
    }
    setRecentSessionsLoading(true);
    return conversationHistoryApi
      .listSessions(20)
      .then((sessions) => {
        const nextSessions = sessions || [];
        setRecentSessions(nextSessions);
        return nextSessions;
      })
      .catch((error) => {
        console.error("加载近期会话失败", error);
        return [] as ConversationSessionItem[];
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
