import type { CodeCommentAttachment } from "./codeCommentContext";
import type { ConversationSelectionAttachment } from "./conversationSelectionContext";
import type { WebElementAttachment } from "./webElementContext";

/**
 * 「预览区 → 聊天输入框」三类上下文的聚合形态。
 *
 * 协议来源：ZCode `packages/ui/src/v4/composer/composerPromptContexts.ts`。
 * 核心约定 —— 三类上下文都**只序列化成 prompt 尾部的一段 markdown**，
 * 不引入任何结构化请求字段，后端只看到纯文本 prompt。
 */
export interface ComposerPromptContexts {
  codeComments: readonly CodeCommentAttachment[];
  conversationSelections: readonly ConversationSelectionAttachment[];
  webElements: readonly WebElementAttachment[];
}

export function countComposerPromptContexts(contexts: ComposerPromptContexts): number {
  return (
    contexts.codeComments.length +
    contexts.conversationSelections.length +
    contexts.webElements.length
  );
}

/**
 * 上下文尾块不参与正文配额，因此单独设一个总字符预算，防止无限堆叠把请求体撑爆。
 * 超过预算时由 `useComposerPromptContexts` 拒绝继续追加（而不是静默截断尾块）。
 */
export const MAX_PROMPT_CONTEXT_TOTAL_CHARS = 24_000;
export const MAX_PROMPT_CONTEXT_COUNT = 24;
