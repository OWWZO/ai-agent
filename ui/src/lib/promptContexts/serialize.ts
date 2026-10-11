import {
  buildPromptWithCodeComments,
  parsePromptCodeComments,
  type CodeCommentAttachment,
} from "./codeCommentContext";
import {
  buildPromptWithConversationSelections,
  parsePromptConversationSelections,
  type ConversationSelectionAttachment,
} from "./conversationSelectionContext";
import {
  buildPromptWithWebElementContexts,
  parsePromptWebElementContexts,
  type WebElementAttachment,
} from "./webElementContext";

export interface SerializedPromptContexts {
  visibleContent: string;
  codeComments: CodeCommentAttachment[];
  conversationSelections: ConversationSelectionAttachment[];
  webElements: WebElementAttachment[];
}

/**
 * 三类上下文都只识别 prompt **尾块**，因此序列化顺序与解析顺序必须严格相反。
 *
 * 序列化：正文 → 选段 → 代码评论 → 网页元素
 * 解析：  网页元素 → 代码评论 → 选段 → 正文
 *
 * ⚠️ 调用方注意：**上下文尾块不参与正文的 `MAX_QUERY_CHARS` 截断**。
 * 正确用法是先把用户正文截断到上限，再把截断后的正文交给本函数拼接上下文。
 */
export function serializeComposerPromptContexts(
  text: string,
  contexts: {
    codeComments: readonly CodeCommentAttachment[];
    conversationSelections: readonly ConversationSelectionAttachment[];
    webElements: readonly WebElementAttachment[];
  }
): string {
  const withSelections = buildPromptWithConversationSelections(text, contexts.conversationSelections);
  const withCodeComments = buildPromptWithCodeComments(withSelections, contexts.codeComments);
  return buildPromptWithWebElementContexts(withCodeComments, contexts.webElements);
}

export function parseComposerPromptContexts(content: string): SerializedPromptContexts {
  const web = parsePromptWebElementContexts(content);
  const code = parsePromptCodeComments(web.visibleContent);
  const selections = parsePromptConversationSelections(code.visibleContent);

  return {
    visibleContent: selections.visibleContent,
    codeComments: code.codeCommentAttachments,
    conversationSelections: selections.references,
    webElements: web.webElementContexts,
  };
}

/** 判断一段消息里是否真的含上下文尾块（回显时用来决定是否走解析路径）。 */
export function hasComposerPromptContexts(content: string): boolean {
  const parsed = parseComposerPromptContexts(content);
  return (
    parsed.codeComments.length > 0 ||
    parsed.conversationSelections.length > 0 ||
    parsed.webElements.length > 0
  );
}
