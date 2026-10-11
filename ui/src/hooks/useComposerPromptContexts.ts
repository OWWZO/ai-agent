import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  CODE_COMMENT_ADD_TO_CHAT_EVENT,
  CODE_COMMENT_REMOVE_FROM_CHAT_EVENT,
  CONVERSATION_SELECTION_ADD_TO_CHAT_EVENT,
  CONVERSATION_SELECTION_REMOVE_FROM_CHAT_EVENT,
  MAX_PROMPT_CONTEXT_COUNT,
  MAX_PROMPT_CONTEXT_TOTAL_CHARS,
  WEB_ELEMENT_ADD_TO_CHAT_EVENT,
  WEB_ELEMENT_REMOVE_FROM_CHAT_EVENT,
  isCodeCommentAddToChatEvent,
  isCodeCommentPayload,
  isCodeCommentRemoveFromChatEvent,
  isConversationSelectionAddToChatEvent,
  isConversationSelectionRemoveFromChatEvent,
  isWebElementAddToChatEvent,
  isWebElementPayload,
  isWebElementRemoveFromChatEvent,
  serializeComposerPromptContexts,
  type CodeCommentAttachment,
  type ConversationSelectionAttachment,
  type WebElementAttachment,
} from "@/lib/promptContexts";
import { createPromptContextId } from "@/lib/promptContexts/ids";

export interface ComposerPromptContextState {
  codeComments: readonly CodeCommentAttachment[];
  webElements: readonly WebElementAttachment[];
  conversationSelections: readonly ConversationSelectionAttachment[];
}

function createEmptyContextState(): ComposerPromptContextState {
  return { codeComments: [], webElements: [], conversationSelections: [] };
}

/** 去重 + 条数上限；命中任一条时返回原引用，让 React 直接 bail out。 */
function appendContext<T>(list: readonly T[], item: T, keyOf: (value: T) => string): readonly T[] {
  const key = keyOf(item);
  if (list.some((value) => keyOf(value) === key)) {
    return list;
  }
  if (list.length >= MAX_PROMPT_CONTEXT_COUNT) {
    return list;
  }
  return [...list, item];
}

const codeCommentKey = (item: CodeCommentAttachment): string =>
  [item.sourcePath, item.startLine, item.endLine, item.comment, item.selectedText].join("\u0000");

const webElementKey = (item: WebElementAttachment): string =>
  [item.pageUrl, item.selector ?? item.xpath ?? "", item.tagName, item.text ?? ""].join("\u0000");

const selectionKey = (item: ConversationSelectionAttachment): string =>
  [item.path ?? "", item.text].join("\u0000");

export interface ComposerPromptContextsApi {
  contexts: ComposerPromptContextState;
  /** 序列化后的上下文尾块字符数，用于在 chip 行展示用量。 */
  totalChars: number;
  removeCodeComment: (id: string) => void;
  removeWebElement: (id: string) => void;
  removeConversationSelection: (id: string) => void;
  clear: () => void;
}

/**
 * 消费「预览区 → 聊天输入框」的三类上下文。
 *
 * 与 ZCode 一致：**没有全局 store、没有 Context Provider**，全部靠 `window` CustomEvent。
 * 本 hook 只是把三个事件汇聚成三组 chip 数组，并按 `sessionId` 变化清空。
 */
export function useComposerPromptContexts(sessionId: string): ComposerPromptContextsApi {
  const [state, setState] = useState<ComposerPromptContextState>(createEmptyContextState);
  const sessionRef = useRef(sessionId);

  // 会话切换时清空，避免把上一轮的上下文带进新会话。
  useEffect(() => {
    if (sessionRef.current === sessionId) return;
    sessionRef.current = sessionId;
    setState(createEmptyContextState());
  }, [sessionId]);

  useEffect(() => {
    if (typeof window === "undefined") return;

    const handleAddCodeComment = (event: Event) => {
      if (!isCodeCommentAddToChatEvent(event)) return;
      const payload = event.detail;
      if (!isCodeCommentPayload(payload)) return;
      const attachment: CodeCommentAttachment = {
        ...payload,
        id: payload.id ?? createPromptContextId("code-comment"),
      };
      setState((current) => {
        const codeComments = appendContext(current.codeComments, attachment, codeCommentKey);
        if (codeComments === current.codeComments) return current;
        const next = { ...current, codeComments };
        return serializeComposerPromptContexts("", next).length > MAX_PROMPT_CONTEXT_TOTAL_CHARS
          ? current
          : next;
      });
    };

    const handleRemoveCodeComment = (event: Event) => {
      if (!isCodeCommentRemoveFromChatEvent(event)) return;
      const id = event.detail?.id;
      if (!id) return;
      setState((current) => ({
        ...current,
        codeComments: current.codeComments.filter((item) => item.id !== id),
      }));
    };

    const handleAddWebElement = (event: Event) => {
      if (!isWebElementAddToChatEvent(event)) return;
      const payload = event.detail;
      if (!isWebElementPayload(payload)) return;
      const attachment: WebElementAttachment = {
        ...payload,
        id: payload.id ?? createPromptContextId("web-element"),
      };
      setState((current) => {
        const webElements = appendContext(current.webElements, attachment, webElementKey);
        if (webElements === current.webElements) return current;
        const next = { ...current, webElements };
        return serializeComposerPromptContexts("", next).length > MAX_PROMPT_CONTEXT_TOTAL_CHARS
          ? current
          : next;
      });
    };

    const handleRemoveWebElement = (event: Event) => {
      if (!isWebElementRemoveFromChatEvent(event)) return;
      const id = event.detail?.id;
      if (!id) return;
      setState((current) => ({
        ...current,
        webElements: current.webElements.filter((item) => item.id !== id),
      }));
    };

    const handleAddSelection = (event: Event) => {
      if (!isConversationSelectionAddToChatEvent(event)) return;
      const detail = event.detail;
      if (!detail || typeof detail.text !== "string") return;
      const attachment: ConversationSelectionAttachment = {
        id: detail.id ?? createPromptContextId("selection"),
        text: detail.text,
        ...(detail.path?.trim() ? { path: detail.path } : {}),
      };
      setState((current) => {
        const conversationSelections = appendContext(
          current.conversationSelections,
          attachment,
          selectionKey
        );
        if (conversationSelections === current.conversationSelections) return current;
        const next = { ...current, conversationSelections };
        return serializeComposerPromptContexts("", next).length > MAX_PROMPT_CONTEXT_TOTAL_CHARS
          ? current
          : next;
      });
    };

    const handleRemoveSelection = (event: Event) => {
      if (!isConversationSelectionRemoveFromChatEvent(event)) return;
      const id = event.detail?.id;
      if (!id) return;
      setState((current) => ({
        ...current,
        conversationSelections: current.conversationSelections.filter((item) => item.id !== id),
      }));
    };

    window.addEventListener(CODE_COMMENT_ADD_TO_CHAT_EVENT, handleAddCodeComment);
    window.addEventListener(CODE_COMMENT_REMOVE_FROM_CHAT_EVENT, handleRemoveCodeComment);
    window.addEventListener(WEB_ELEMENT_ADD_TO_CHAT_EVENT, handleAddWebElement);
    window.addEventListener(WEB_ELEMENT_REMOVE_FROM_CHAT_EVENT, handleRemoveWebElement);
    window.addEventListener(CONVERSATION_SELECTION_ADD_TO_CHAT_EVENT, handleAddSelection);
    window.addEventListener(
      CONVERSATION_SELECTION_REMOVE_FROM_CHAT_EVENT,
      handleRemoveSelection
    );

    return () => {
      window.removeEventListener(CODE_COMMENT_ADD_TO_CHAT_EVENT, handleAddCodeComment);
      window.removeEventListener(CODE_COMMENT_REMOVE_FROM_CHAT_EVENT, handleRemoveCodeComment);
      window.removeEventListener(WEB_ELEMENT_ADD_TO_CHAT_EVENT, handleAddWebElement);
      window.removeEventListener(WEB_ELEMENT_REMOVE_FROM_CHAT_EVENT, handleRemoveWebElement);
      window.removeEventListener(CONVERSATION_SELECTION_ADD_TO_CHAT_EVENT, handleAddSelection);
      window.removeEventListener(
        CONVERSATION_SELECTION_REMOVE_FROM_CHAT_EVENT,
        handleRemoveSelection
      );
    };
  }, []);

  const totalChars = useMemo(
    () => serializeComposerPromptContexts("", state).length,
    [state]
  );

  const removeCodeComment = useCallback((id: string) => {
    setState((current) => ({
      ...current,
      codeComments: current.codeComments.filter((item) => item.id !== id),
    }));
  }, []);

  const removeWebElement = useCallback((id: string) => {
    setState((current) => ({
      ...current,
      webElements: current.webElements.filter((item) => item.id !== id),
    }));
  }, []);

  const removeConversationSelection = useCallback((id: string) => {
    setState((current) => ({
      ...current,
      conversationSelections: current.conversationSelections.filter((item) => item.id !== id),
    }));
  }, []);

  const clear = useCallback(() => {
    setState(createEmptyContextState());
  }, []);

  return {
    contexts: state,
    totalChars,
    removeCodeComment,
    removeWebElement,
    removeConversationSelection,
    clear,
  };
}
