/**
 * 流式/后台更新是否应写入当前主视图。
 * 会话切换后，非当前会话的更新只进本地缓存，不抢占界面。
 */
export function shouldApplyConversationToView(
  currentConversationId: string | undefined,
  nextConversationId: string | undefined
) {
  return Boolean(
    currentConversationId &&
      nextConversationId &&
      currentConversationId === nextConversationId
  );
}

export function deriveConversationMetaFromInput(
  info: Pick<CHAT.TInputInfo, "outputStyle" | "deepThink">,
  params: {
    productType: string;
    currentRole: CHAT.ConversationRole | null;
  }
) {
  const outputStyle = info.outputStyle || params.productType;
  const isChatMode = outputStyle === "chat";
  const deepThink =
    isChatMode || outputStyle === "dataAgent" ? false : Boolean(info.deepThink);

  return {
    productType: outputStyle,
    deepThink,
    role: isChatMode ? params.currentRole : null,
  };
}

export function shouldHydrateConversationHistory(params: {
  conversation: CHAT.ConversationHistory;
  hydratedSessionIds: Set<string>;
}) {
  return Boolean(
    params.conversation.sessionId &&
      params.conversation.chatList.length === 0 &&
      params.conversation.dataChatList.length === 0 &&
      !params.hydratedSessionIds.has(params.conversation.sessionId)
  );
}

function resolveConversationStatus(conversation: CHAT.ConversationHistory) {
  const latestChat = conversation.chatList[conversation.chatList.length - 1];
  if (latestChat?.metrics?.status) {
    return latestChat.metrics.status;
  }
  const latestDataChat =
    conversation.dataChatList[conversation.dataChatList.length - 1];
  if (latestDataChat?.error) {
    return "FAILED";
  }
  if (latestChat && !latestChat.loading) {
    return "SUCCESS";
  }
  if (latestDataChat && !latestDataChat.loading) {
    return "SUCCESS";
  }
  return "RUNNING";
}

function resolveLatestQueryText(conversation: CHAT.ConversationHistory) {
  const latestChat = conversation.chatList[conversation.chatList.length - 1];
  if (latestChat?.query) {
    return latestChat.query;
  }
  const latestDataChat =
    conversation.dataChatList[conversation.dataChatList.length - 1];
  return latestDataChat?.query || conversation.chatTitle || "";
}

/**
 * 将前端当前会话映射成侧边栏“最近”可渲染的数据结构。
 * 用户主动新建但尚未发送消息的会话也需要入口，避免切走后无法回到该界面。
 */
export function toRecentSessionItem(
  conversation: CHAT.ConversationHistory
): CHAT.ConversationSessionItem | null {
  if (!conversation.sessionId) {
    return null;
  }

  const startedAt = new Date(conversation.createdAt || Date.now()).toISOString();
  const lastActiveAt = new Date(
    conversation.updatedAt || conversation.createdAt || Date.now()
  ).toISOString();
  const failedRunCount = conversation.chatList.filter(
    (item) => item.metrics?.status === "FAILED"
  ).length;
  const finishedRunCount = conversation.chatList.filter(
    (item) => item.metrics?.status === "SUCCESS" || !item.loading
  ).length;

  return {
    sessionId: conversation.sessionId,
    title: conversation.chatTitle || conversation.title || "新对话",
    status: resolveConversationStatus(conversation),
    latestQueryText: resolveLatestQueryText(conversation),
    runCount: conversation.chatList.length + conversation.dataChatList.length,
    finishedRunCount,
    failedRunCount,
    startedAt,
    lastActiveAt,
  };
}

/**
 * 本地会话优先展示，服务端刷新回来后按 sessionId 去重，避免同一会话出现两个按钮。
 */
export function mergeRecentSessions(
  remoteSessions: CHAT.ConversationSessionItem[],
  localSessions: CHAT.ConversationSessionItem[]
) {
  const merged = new Map<string, CHAT.ConversationSessionItem>();

  localSessions.forEach((session) => {
    if (session.sessionId) {
      merged.set(session.sessionId, session);
    }
  });
  remoteSessions.forEach((session) => {
    if (session.sessionId && !merged.has(session.sessionId)) {
      merged.set(session.sessionId, session);
    }
  });

  return Array.from(merged.values()).slice(0, 20);
}

function isEmptyConversationDraft(conversation: CHAT.ConversationHistory) {
  return (
    conversation.chatList.length === 0 &&
    conversation.dataChatList.length === 0
  );
}

/**
 * 本地“新聊天”空草稿只保留一个；已有内容的会话不受影响。
 */
export function mergeLocalRecentConversations(
  currentLocalConversations: CHAT.ConversationHistory[],
  nextConversation: CHAT.ConversationHistory
) {
  const shouldReplaceEmptyDraft = isEmptyConversationDraft(nextConversation);

  return [
    nextConversation,
    ...currentLocalConversations.filter((item) => {
      if (item.sessionId === nextConversation.sessionId) {
        return false;
      }
      return !(shouldReplaceEmptyDraft && isEmptyConversationDraft(item));
    }),
  ].slice(0, 20);
}
