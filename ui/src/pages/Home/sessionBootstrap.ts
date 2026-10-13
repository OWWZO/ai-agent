import type {
  ConversationHistoryPage,
  ConversationRunReplay,
  ConversationSessionItem,
} from "@/services/agentConversation";
import {
  collectPendingReplayIds,
  groupChatsIntoTurns,
} from "@/lib/turnGroups";
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

/**
 * 首屏补齐历史回放。
 *
 * 历史接口只返回**轻量摘要壳**（`tasks` 为空），过程数据必须按 run 单独拉回放。
 * 这里按 **turn** 补：合并后的 `Dialogue` 只有一个 requestId（最后一个 run），
 * 只补它会漏掉 turn 内其它 run —— AskUserQuestion 之前那个 run 的工具卡就会整段消失。
 *
 * 目标 turn 的选择：
 * 1. 有 RUNNING run 的 turn（刷新时仍在后台执行的会话）；
 * 2. 否则退化为**最后一个 turn** —— 刷新后用户看到的就是它，不补的话过程区
 *    只剩被 AskUserQuestion 恢复逻辑注入的那张卡。
 */
export async function hydrateSessionWithRunningReplay(
  page: ConversationHistoryPage,
  loadReplay: (requestId: string) => Promise<ConversationRunReplay>,
  onReplayError?: (requestId: string, error: unknown) => void
): Promise<CHAT.ConversationHistory> {
  const conversation = hydrateConversationFromSummaryPage(page);
  const turns = groupChatsIntoTurns(conversation.chatList);
  const runningTurn = [...turns]
    .reverse()
    .find((turn) =>
      turn.chats.some(
        (chat) => String(chat.metrics?.status || "").toUpperCase() === "RUNNING"
      )
    );
  const target = runningTurn || turns[turns.length - 1];
  if (!target) {
    return conversation;
  }

  let next = conversation;
  for (const requestId of collectPendingReplayIds(target.chats)) {
    try {
      const replay = await loadReplay(requestId);
      next = mergeRunReplayIntoConversation(next, replay);
    } catch (error) {
      onReplayError?.(requestId, error);
    }
  }
  return next;
}
