import type { MutableRefObject } from "react";

export type ActiveRunState = {
  status?: string;
  errorMsg?: string;
  finishedAt?: string;
};

export type ThrottledStreamController<TValue> = {
  pendingRef: MutableRefObject<TValue>;
  cancel: () => void;
  flush: (force?: boolean) => void;
  schedule: (updater: TValue | ((current: TValue) => TValue), force?: boolean) => void;
  reset: (value: TValue) => void;
};

export type ConversationListKey = "chatList" | "dataChatList";

export type ConversationDraftController<TItem> = {
  conversationId: string;
  getSnapshot: () => CHAT.ConversationHistory;
  replaceLastItem: (item: TItem) => CHAT.ConversationHistory;
  commit: (nextConversation: CHAT.ConversationHistory) => void;
};

export type DataConversationRuntime = {
  draftController: ConversationDraftController<CHAT.DataChatItem>;
  currentChat: CHAT.DataChatItem;
};
