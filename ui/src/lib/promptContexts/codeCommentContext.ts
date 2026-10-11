import { createPromptContextId } from "./ids";

/**
 * 代码评论上下文（ZCode `lib/codeCommentContext.ts` 的移植版）。
 *
 * 尾块格式与 ZCode **逐字节一致**，这样模型看到的提示词结构完全一样：
 *
 * ```
 * # Code comments:
 *
 * ## Comment 1
 * File: src/components/A.tsx
 * Side: R
 * Lines: 12-15
 * Selected text:
 * ```
 * const total = items.reduce(...)
 * ```
 * Comment:
 * 这里没处理空数组
 * ```
 *
 * 与 ZCode 的差异：ZCode 的 payload 带 `workspacePath` / `workspaceIdentity` 用于多
 * workspace 过滤；本项目是单会话单输入框，过滤维度换成 `sessionId`（由 hook 持有，
 * 不写进 payload 与 markdown）。
 */
export const CODE_COMMENT_ADD_TO_CHAT_EVENT = "reactor:code-comment-add-to-chat";
export const CODE_COMMENT_REMOVE_FROM_CHAT_EVENT = "reactor:code-comment-remove-from-chat";

const BLOCK_TITLE = "# Code comments:";

export interface CodeCommentRange {
  startLine: number;
  endLine: number;
}

export interface CodeCommentPayload extends CodeCommentRange {
  id?: string;
  /** 源文件路径，序列化成 prompt 里的 `File:` 行；回显时用于定位。 */
  sourcePath: string;
  /** 展示名（文件名），路径缺失时兜底。 */
  sourceTitle: string;
  selectedText: string;
  comment: string;
  contextLabel?: string;
  commentLabel?: string;
}

export interface CodeCommentAttachment extends CodeCommentPayload {
  id: string;
}

export interface CodeCommentRemovePayload {
  id: string;
}

export interface ParsedCodeCommentPrompt {
  visibleContent: string;
  codeCommentAttachments: CodeCommentAttachment[];
}

type CodeCommentAddToChatEvent = CustomEvent<CodeCommentPayload>;
type CodeCommentRemoveFromChatEvent = CustomEvent<CodeCommentRemovePayload>;

export function isCodeCommentAddToChatEvent(event: Event): event is CodeCommentAddToChatEvent {
  return (
    event.type === CODE_COMMENT_ADD_TO_CHAT_EVENT &&
    "detail" in event &&
    typeof (event as CustomEvent<unknown>).detail === "object" &&
    (event as CustomEvent<unknown>).detail !== null
  );
}

export function isCodeCommentRemoveFromChatEvent(
  event: Event
): event is CodeCommentRemoveFromChatEvent {
  return (
    event.type === CODE_COMMENT_REMOVE_FROM_CHAT_EVENT &&
    "detail" in event &&
    typeof (event as CustomEvent<unknown>).detail === "object" &&
    (event as CustomEvent<unknown>).detail !== null
  );
}

export function isCodeCommentPayload(payload: unknown): payload is CodeCommentPayload {
  const candidate = payload as CodeCommentPayload;
  return (
    typeof payload === "object" &&
    payload !== null &&
    typeof candidate.sourcePath === "string" &&
    candidate.sourcePath.length > 0 &&
    typeof candidate.sourceTitle === "string" &&
    candidate.sourceTitle.length > 0 &&
    typeof candidate.selectedText === "string" &&
    typeof candidate.comment === "string" &&
    Number.isFinite(candidate.startLine) &&
    Number.isFinite(candidate.endLine)
  );
}

function buildCodeCommentMarkdown(payload: CodeCommentPayload): string {
  const lineLabel =
    payload.startLine === payload.endLine
      ? String(payload.startLine)
      : `${payload.startLine}-${payload.endLine}`;

  return `## Comment\nFile: ${payload.sourcePath || payload.sourceTitle}\nSide: R\nLines: ${lineLabel}\nSelected text:\n\`\`\`\n${payload.selectedText.trim()}\n\`\`\`\nComment:\n${payload.comment.trim()}\n`;
}

function buildCodeCommentsBlock(attachments: readonly CodeCommentAttachment[]): string {
  if (attachments.length === 0) {
    return "";
  }

  return `${BLOCK_TITLE}\n\n${attachments
    .map((attachment, index) =>
      buildCodeCommentMarkdown(attachment).replace("## Comment", `## Comment ${index + 1}`)
    )
    .join("\n")}`;
}

export function buildPromptWithCodeComments(
  text: string,
  attachments: readonly CodeCommentAttachment[]
): string {
  const content = text.trimEnd();
  const commentsBlock = buildCodeCommentsBlock(attachments);
  if (!commentsBlock) {
    return content.trim();
  }

  return `${content}${content ? "\n\n" : ""}${commentsBlock}`.trim();
}

function getSourceTitle(path: string): string {
  return path.split(/[\\/]/).filter(Boolean).pop() ?? path;
}

function parseLineRange(value: string): CodeCommentRange | null {
  const normalized = value.trim().replace(/^L/i, "");
  const match = /^(\d+)(?:\s*-\s*L?(\d+))?$/.exec(normalized);
  if (!match) {
    return null;
  }

  const startLine = Number(match[1]);
  const endLine = Number(match[2] ?? match[1]);
  if (!Number.isFinite(startLine) || !Number.isFinite(endLine)) {
    return null;
  }

  return { startLine, endLine };
}

function parseCodeCommentItem(rawItem: string, index: number): CodeCommentAttachment | null {
  const fileMatch = /^File:\s*(.+)$/m.exec(rawItem);
  const linesMatch = /^Lines:\s*(.+)$/m.exec(rawItem);
  const selectedTextMatch =
    /Selected text:\s*\n```(?:[^\n`]*)?\n([\s\S]*?)\n```\s*\nComment:\s*\n?([\s\S]*)$/m.exec(
      rawItem
    );
  if (!fileMatch || !linesMatch || !selectedTextMatch) {
    return null;
  }

  const filePath = fileMatch[1];
  const lines = linesMatch[1];
  const selectedText = selectedTextMatch[1];
  const rawComment = selectedTextMatch[2];
  if (!filePath || !lines || selectedText === undefined || rawComment === undefined) {
    return null;
  }

  const range = parseLineRange(lines);
  if (!range) {
    return null;
  }

  const sourcePath = filePath.trim();

  return {
    id: `parsed-code-comment-${index + 1}-${range.startLine}-${range.endLine}`,
    sourcePath,
    sourceTitle: getSourceTitle(sourcePath),
    startLine: range.startLine,
    endLine: range.endLine,
    selectedText: selectedText.trim(),
    comment: rawComment.trim(),
  };
}

export function parsePromptCodeComments(content: string): ParsedCodeCommentPrompt {
  // 这里不能使用 multiline 的 `$`，否则非贪婪匹配会在第一行 `## Comment 1` 后提前停止，
  // 导致持久化消息无法解析回 comment 附件，只能把原始 markdown 暴露在聊天气泡里。
  const blockMatch = /(?:^|\n\n)# Code comments:\s*\n\n([\s\S]*?)\s*$/.exec(content);
  if (!blockMatch || blockMatch.index < 0) {
    return { visibleContent: content, codeCommentAttachments: [] };
  }

  const rawBlock = blockMatch[1];
  if (rawBlock === undefined) {
    return { visibleContent: content, codeCommentAttachments: [] };
  }

  const rawItems = rawBlock
    .split(/\n(?=## Comment(?:\s+\d+)?\n)/)
    .map((item) => item.trim())
    .filter(Boolean);
  const codeCommentAttachments = rawItems
    .map((item, index) => parseCodeCommentItem(item, index))
    .filter((item): item is CodeCommentAttachment => item !== null);

  if (codeCommentAttachments.length === 0) {
    return { visibleContent: content, codeCommentAttachments: [] };
  }

  return {
    visibleContent: content.slice(0, blockMatch.index).trimEnd(),
    codeCommentAttachments,
  };
}

export function dispatchCodeCommentAddToChat(payload: CodeCommentPayload): void {
  window.dispatchEvent(
    new CustomEvent(CODE_COMMENT_ADD_TO_CHAT_EVENT, {
      cancelable: true,
      detail: { ...payload, id: payload.id ?? createPromptContextId("code-comment") },
    })
  );
}

export function dispatchCodeCommentRemoveFromChat(payload: CodeCommentRemovePayload): void {
  window.dispatchEvent(
    new CustomEvent(CODE_COMMENT_REMOVE_FROM_CHAT_EVENT, { detail: payload })
  );
}
