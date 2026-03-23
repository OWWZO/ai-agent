import { getUniqId } from "@/utils/utils";

export const CHAT_HISTORY_STORAGE_KEY = "reactor.chat.history.v1";
export const CHAT_HISTORY_VERSION = 1;
export const CHAT_HISTORY_MAX_COUNT = 20;

const buildSessionId = () => `session-${getUniqId()}`;

const normalizeConversation = (item: Partial<CHAT.ConversationHistory>): CHAT.ConversationHistory => {
  const createdAt = typeof item.createdAt === "number" ? item.createdAt : Date.now();
  const updatedAt = typeof item.updatedAt === "number" ? item.updatedAt : createdAt;
  return {
    id: item.id || `conversation-${getUniqId()}`,
    sessionId: item.sessionId || buildSessionId(),
    title: item.title || "新对话",
    productType: item.productType || "chat",
    deepThink: Boolean(item.deepThink),
    createdAt,
    updatedAt,
    chatTitle: item.chatTitle || "",
    chatList: Array.isArray(item.chatList) ? item.chatList : [],
    dataChatList: Array.isArray(item.dataChatList) ? item.dataChatList : [],
  };
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
    createdAt: partial.createdAt ?? now,
    updatedAt: partial.updatedAt ?? now,
    chatTitle: partial.chatTitle || "",
    chatList: partial.chatList || [],
    dataChatList: partial.dataChatList || [],
  });
};

export const loadHistory = (): CHAT.ConversationHistoryStore => {
  const fallback: CHAT.ConversationHistoryStore = {
    version: CHAT_HISTORY_VERSION,
    conversations: [],
  };

  try {
    if (typeof window === "undefined" || !window.localStorage) {
      return fallback;
    }
    const raw = window.localStorage.getItem(CHAT_HISTORY_STORAGE_KEY);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as Partial<CHAT.ConversationHistoryStore>;
    if (!parsed || !Array.isArray(parsed.conversations)) return fallback;
    const conversations = pruneHistory(parsed.conversations.map((item) => normalizeConversation(item)));
    return {
      version: CHAT_HISTORY_VERSION,
      conversations,
    };
  } catch {
    return fallback;
  }
};

export const saveHistory = (store: CHAT.ConversationHistoryStore): CHAT.ConversationHistoryStore => {
  const normalizedStore: CHAT.ConversationHistoryStore = {
    version: CHAT_HISTORY_VERSION,
    conversations: pruneHistory(store.conversations.map((item) => normalizeConversation(item))),
  };

  try {
    if (typeof window === "undefined" || !window.localStorage) {
      return normalizedStore;
    }

    let candidates = normalizedStore.conversations;
    while (candidates.length > 0) {
      try {
        const payload: CHAT.ConversationHistoryStore = {
          version: CHAT_HISTORY_VERSION,
          conversations: candidates,
        };
        window.localStorage.setItem(CHAT_HISTORY_STORAGE_KEY, JSON.stringify(payload));
        return payload;
      } catch {
        candidates = candidates.slice(0, -1);
      }
    }

    window.localStorage.removeItem(CHAT_HISTORY_STORAGE_KEY);
    return {
      version: CHAT_HISTORY_VERSION,
      conversations: [],
    };
  } catch {
    return normalizedStore;
  }
};
