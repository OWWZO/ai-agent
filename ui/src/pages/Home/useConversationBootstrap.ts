import { useEffect } from "react";
import type { MutableRefObject } from "react";

import { conversationHistoryApi } from "@/services/agentConversation";
import {
  hydrateConversationFromReplayFrames,
  isHistoryDetailEmpty,
} from "@/utils/conversationHistory";
import { shouldHydrateConversationHistory } from "./homeState";

export function useConversationBootstrap(params: {
  conversation: CHAT.ConversationHistory;
  hydratedSessionIdsRef: MutableRefObject<Set<string>>;
  onHydrated: (nextConversation: CHAT.ConversationHistory) => void;
}) {
  useEffect(() => {
    let disposed = false;
    const sessionId = params.conversation.sessionId;

    if (
      !shouldHydrateConversationHistory({
        conversation: params.conversation,
        hydratedSessionIds: params.hydratedSessionIdsRef.current,
      })
    ) {
      return;
    }

    params.hydratedSessionIdsRef.current.add(sessionId);

    conversationHistoryApi
      .getSessionDetail(sessionId)
      .then((detail) => {
        if (disposed || !detail || isHistoryDetailEmpty(detail)) {
          return;
        }
        params.onHydrated(hydrateConversationFromReplayFrames(detail));
      })
      .catch(() => {
        // 当前 session 没有历史时保持空白/初始态，不自动切换到其他会话。
      });

    return () => {
      disposed = true;
    };
  }, [params]);
}
