import { createPromptContextId } from "./ids";

/**
 * Markdown 正文选段上下文（ZCode `lib/conversationSelectionReference.ts` 的移植版）。
 *
 * 尾块是一个 JSON 代码块 —— 唯一一个不用 `## 条目` 分节的上下文类型：
 *
 * ```
 * # userselect:
 * ```userselect
 * [{"path":"README.md","text":"选中一段正文"}]
 * ```
 * ```
 *
 * 只保留 `path` + `text`，不发送任何内部身份字段（与 ZCode 的发送合同一致）。
 */
export const CONVERSATION_SELECTION_ADD_TO_CHAT_EVENT =
  "cute:conversation-selection-add-to-chat";
export const CONVERSATION_SELECTION_REMOVE_FROM_CHAT_EVENT =
  "cute:conversation-selection-remove-from-chat";

const BLOCK_TITLE = "# userselect:";
const BLOCK_FENCE = "userselect";
const BLOCK_PATTERN = /(?:\n\n)?# userselect:\n```userselect\n([\s\S]*?)\n```\s*$/;

export const CONVERSATION_SELECTION_MAX_TEXT_LENGTH = 8_000;
export const CONVERSATION_SELECTION_MAX_COUNT = 8;
export const CONVERSATION_SELECTION_MAX_TOTAL_LENGTH = 16_000;

export interface ConversationSelectionText {
  text: string;
  path?: string;
}

export interface ConversationSelectionAttachment extends ConversationSelectionText {
  id: string;
}

export interface ConversationSelectionRemovePayload {
  id: string;
}

export interface ParsedConversationSelectionPrompt {
  visibleContent: string;
  references: ConversationSelectionAttachment[];
}

export function isConversationSelectionAddToChatEvent(
  event: Event
): event is CustomEvent<ConversationSelectionText & { id?: string }> {
  return (
    event.type === CONVERSATION_SELECTION_ADD_TO_CHAT_EVENT &&
    "detail" in event &&
    typeof (event as CustomEvent<unknown>).detail === "object" &&
    (event as CustomEvent<unknown>).detail !== null
  );
}

export function isConversationSelectionRemoveFromChatEvent(
  event: Event
): event is CustomEvent<ConversationSelectionRemovePayload> {
  return (
    event.type === CONVERSATION_SELECTION_REMOVE_FROM_CHAT_EVENT &&
    "detail" in event &&
    typeof (event as CustomEvent<unknown>).detail === "object" &&
    (event as CustomEvent<unknown>).detail !== null
  );
}

export function buildPromptWithConversationSelections(
  visibleContent: string,
  references: readonly ConversationSelectionAttachment[]
): string {
  if (references.length === 0) return visibleContent;

  const block = [
    BLOCK_TITLE,
    `\`\`\`${BLOCK_FENCE}`,
    JSON.stringify(
      references.map(({ text, path }) => (path?.trim() ? { path, text } : { text }))
    ),
    "```",
  ].join("\n");

  return visibleContent ? `${visibleContent}\n\n${block}` : block;
}

export function parsePromptConversationSelections(
  text: string
): ParsedConversationSelectionPrompt {
  const match = text.match(BLOCK_PATTERN);
  if (!match) {
    return { visibleContent: text, references: [] };
  }

  try {
    const parsed: unknown = JSON.parse(match[1] ?? "[]");
    if (!Array.isArray(parsed)) {
      return { visibleContent: text, references: [] };
    }
    const references: ConversationSelectionAttachment[] = [];
    for (const [index, value] of parsed.entries()) {
      if (!value || typeof value !== "object" || Array.isArray(value)) {
        return { visibleContent: text, references: [] };
      }
      const candidate = value as Partial<ConversationSelectionText>;
      if (typeof candidate.text !== "string") {
        return { visibleContent: text, references: [] };
      }
      const path =
        typeof candidate.path === "string" && candidate.path.trim() ? candidate.path : undefined;
      references.push({
        id: `parsed-selection-${index + 1}`,
        text: candidate.text,
        ...(path ? { path } : {}),
      });
    }

    return {
      visibleContent: text.slice(0, match.index).trimEnd(),
      references,
    };
  } catch {
    return { visibleContent: text, references: [] };
  }
}

export function dispatchConversationSelectionAddToChat(
  payload: ConversationSelectionText & { id?: string }
): void {
  window.dispatchEvent(
    new CustomEvent(CONVERSATION_SELECTION_ADD_TO_CHAT_EVENT, {
      cancelable: true,
      detail: { ...payload, id: payload.id ?? createPromptContextId("selection") },
    })
  );
}

export function dispatchConversationSelectionRemoveFromChat(
  payload: ConversationSelectionRemovePayload
): void {
  window.dispatchEvent(
    new CustomEvent(CONVERSATION_SELECTION_REMOVE_FROM_CHAT_EVENT, { detail: payload })
  );
}
