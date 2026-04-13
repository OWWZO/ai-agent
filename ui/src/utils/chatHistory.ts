import type {
  ConversationDetail,
  ConversationListItem,
} from "@/services/agentConversation";
import { restoreTurns } from "@/services/agentConversation";
import { getUniqId } from "@/utils/utils";

export const CHAT_HISTORY_STORAGE_KEY = "reactor.chat.history.v1";
export const CHAT_HISTORY_VERSION = 1;
export const CHAT_HISTORY_MAX_COUNT = 20;

const buildSessionId = () => `session-${getUniqId()}`;

const toTimestamp = (value?: string, fallback = Date.now()) => {
  if (!value) {
    return fallback;
  }

  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) && timestamp > 0 ? timestamp : fallback;
};

const normalizeConversation = (item: Partial<CHAT.ConversationHistory>): CHAT.ConversationHistory => {
  const createdAt = typeof item.createdAt === "number" ? item.createdAt : Date.now();
  const updatedAt = typeof item.updatedAt === "number" ? item.updatedAt : createdAt;
  return {
    id: item.id || `conversation-${getUniqId()}`,
    sessionId: item.sessionId || buildSessionId(),
    title: item.title || "新对话",
    productType: item.productType || "chat",
    deepThink: Boolean(item.deepThink),
    role: item.role || null,
    createdAt,
    updatedAt,
    chatTitle: item.chatTitle || "",
    chatList: Array.isArray(item.chatList) ? item.chatList : [],
    dataChatList: Array.isArray(item.dataChatList) ? item.dataChatList : [],
  };
};

export const hasLocalConversationContent = (
  conversation: CHAT.ConversationHistory | undefined
) => {
  if (!conversation) return false;
  return conversation.chatList.length > 0 || conversation.dataChatList.length > 0;
};

export const isDraftConversation = (
  conversation: CHAT.ConversationHistory | undefined
) => {
  if (!conversation) return false;
  return !hasLocalConversationContent(conversation);
};

export const buildConversationFromSummary = (
  summary: ConversationListItem,
  fallbackRole?: CHAT.ConversationRole | null
): CHAT.ConversationHistory => {
  const createdAt = toTimestamp(summary.createTime);
  const updatedAt = toTimestamp(summary.updateTime, createdAt);

  return normalizeConversation({
    id: summary.sessionId,
    sessionId: summary.sessionId,
    title: summary.title || "新对话",
    chatTitle: summary.title || "新对话",
    productType: summary.productType || "chat",
    deepThink: summary.agentType === 1,
    role: summary.role ?? fallbackRole ?? null,
    createdAt,
    updatedAt,
    chatList: [],
    dataChatList: [],
  });
};

export const buildConversationFromDetail = (
  detail: ConversationDetail,
  cachedConversation?: CHAT.ConversationHistory | null,
  fallbackRole?: CHAT.ConversationRole | null
): CHAT.ConversationHistory => {
  const summaryConversation = buildConversationFromSummary(
    detail.conversation,
    fallbackRole
  );
  const restoredChatList = detail.turns?.length
    ? restoreTurns(detail.conversation.sessionId, detail.turns)
    : cachedConversation?.chatList || [];

  return normalizeConversation({
    ...summaryConversation,
    ...(cachedConversation || {}),
    id: cachedConversation?.id || detail.conversation.sessionId,
    sessionId: detail.conversation.sessionId,
    title:
      detail.conversation.title ||
      cachedConversation?.title ||
      summaryConversation.title,
    chatTitle:
      cachedConversation?.chatTitle ||
      detail.conversation.title ||
      summaryConversation.chatTitle,
    productType:
      detail.conversation.productType ||
      cachedConversation?.productType ||
      summaryConversation.productType,
    deepThink: detail.conversation.agentType === 1,
    role:
      detail.conversation.role ??
      cachedConversation?.role ??
      fallbackRole ??
      summaryConversation.role ??
      null,
    createdAt: summaryConversation.createdAt,
    updatedAt: Math.max(
      summaryConversation.updatedAt,
      cachedConversation?.updatedAt || 0
    ),
    chatList: restoredChatList,
    dataChatList: cachedConversation?.dataChatList || [],
  });
};

type ResolveConversationHistoriesArgs = {
  summaries: ConversationListItem[];
  detailCache: CHAT.ConversationDetailCache;
  drafts: CHAT.ConversationHistory[];
  fallbackChatRole?: CHAT.ConversationRole | null;
};

export const resolveConversationHistories = ({
  summaries,
  detailCache,
  drafts,
  fallbackChatRole,
}: ResolveConversationHistoriesArgs): CHAT.ConversationHistory[] => {
  const mergedConversations = new Map<string, CHAT.ConversationHistory>();
  const draftMap = new Map(
    drafts.map((conversation) => [
      conversation.sessionId,
      normalizeConversation(conversation),
    ])
  );

  summaries.forEach((summary) => {
    const summaryConversation = buildConversationFromSummary(
      summary,
      summary.productType === "chat" ? fallbackChatRole ?? null : null
    );
    const cachedConversation = detailCache[summary.sessionId];
    const draftConversation = draftMap.get(summary.sessionId);
    const baseConversation =
      draftConversation || cachedConversation || summaryConversation;

    mergedConversations.set(
      summary.sessionId,
      normalizeConversation({
        ...summaryConversation,
        ...baseConversation,
        id: baseConversation.id || summary.sessionId,
        sessionId: summary.sessionId,
        title:
          summary.title || baseConversation.title || summaryConversation.title,
        chatTitle:
          baseConversation.chatTitle ||
          summary.title ||
          baseConversation.title ||
          summaryConversation.chatTitle,
        productType:
          summary.productType ||
          baseConversation.productType ||
          summaryConversation.productType,
        deepThink: summary.agentType === 1,
        role:
          summary.role ??
          baseConversation.role ??
          (summary.productType === "chat" ? fallbackChatRole ?? null : null),
        createdAt: summaryConversation.createdAt,
        updatedAt: Math.max(
          summaryConversation.updatedAt,
          baseConversation.updatedAt || 0
        ),
        chatList: Array.isArray(baseConversation.chatList)
          ? baseConversation.chatList
          : [],
        dataChatList: Array.isArray(baseConversation.dataChatList)
          ? baseConversation.dataChatList
          : [],
      })
    );
  });

  drafts.forEach((conversation) => {
    const normalized = normalizeConversation(conversation);
    if (!mergedConversations.has(normalized.sessionId)) {
      mergedConversations.set(normalized.sessionId, normalized);
    }
  });

  return pruneHistory(Array.from(mergedConversations.values()));
};

export const pruneHistory = (conversations: CHAT.ConversationHistory[]): CHAT.ConversationHistory[] => {
  return [...conversations]
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, CHAT_HISTORY_MAX_COUNT);
};

export const createConversation = (
  partial: Partial<CHAT.ConversationHistory> = {}
): CHAT.ConversationHistory => {
  const now = Date.now();
  return normalizeConversation({
    ...partial,
    id: partial.id || `conversation-${getUniqId()}`,
    sessionId: partial.sessionId || buildSessionId(),
    title: partial.title || "新对话",
    role: partial.role || null,
    createdAt: partial.createdAt ?? now,
    updatedAt: partial.updatedAt ?? now,
    chatTitle: partial.chatTitle || "",
    chatList: partial.chatList || [],
    dataChatList: partial.dataChatList || [],
  });
};

export const loadHistory = (): CHAT.ConversationHistoryStore => {
  return {
    version: CHAT_HISTORY_VERSION,
    conversations: [],
  };
};

export const saveHistory = (store: CHAT.ConversationHistoryStore): CHAT.ConversationHistoryStore => {
  return {
    version: CHAT_HISTORY_VERSION,
    conversations: pruneHistory(store.conversations.map((item) => normalizeConversation(item))),
  };
};
