import { getUniqId } from "@/utils/utils";

export const CHAT_HISTORY_STORAGE_KEY = "reactor.chat.history.v1";
export const CHAT_HISTORY_BACKUP_STORAGE_KEY = "reactor.chat.history.v1.backup";
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
    const parseStore = (raw: string | null): CHAT.ConversationHistoryStore | null => {
      if (!raw) return null;
      const parsed = JSON.parse(raw) as Partial<CHAT.ConversationHistoryStore>;
      if (!parsed || !Array.isArray(parsed.conversations)) {
        return null;
      }
      return {
        version: CHAT_HISTORY_VERSION,
        conversations: pruneHistory(
          parsed.conversations.map((item) => normalizeConversation(item))
        ),
      };
    };

    // 主存储异常时回退到备份，避免一次异常写入后整段历史直接丢失。
    return (
      parseStore(window.localStorage.getItem(CHAT_HISTORY_STORAGE_KEY)) ||
      parseStore(window.localStorage.getItem(CHAT_HISTORY_BACKUP_STORAGE_KEY)) ||
      fallback
    );
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

    const previousRaw =
      window.localStorage.getItem(CHAT_HISTORY_STORAGE_KEY) ||
      window.localStorage.getItem(CHAT_HISTORY_BACKUP_STORAGE_KEY);

    let candidates = normalizedStore.conversations;
    while (candidates.length > 0) {
      try {
        const payload: CHAT.ConversationHistoryStore = {
          version: CHAT_HISTORY_VERSION,
          conversations: candidates,
        };
        const serializedPayload = JSON.stringify(payload);
        window.localStorage.setItem(CHAT_HISTORY_STORAGE_KEY, serializedPayload);
        window.localStorage.setItem(CHAT_HISTORY_BACKUP_STORAGE_KEY, serializedPayload);
        return payload;
      } catch {
        candidates = candidates.slice(0, -1);
      }
    }

    // 即使本次保存失败，也保留之前已成功落盘的历史，不做破坏性清空。
    if (previousRaw) {
      try {
        const parsed = JSON.parse(previousRaw) as Partial<CHAT.ConversationHistoryStore>;
        if (parsed && Array.isArray(parsed.conversations)) {
          return {
            version: CHAT_HISTORY_VERSION,
            conversations: pruneHistory(
              parsed.conversations.map((item) => normalizeConversation(item))
            ),
          };
        }
      } catch {
        return normalizedStore;
      }
    }

    return normalizedStore;
  } catch {
    return normalizedStore;
  }
};
