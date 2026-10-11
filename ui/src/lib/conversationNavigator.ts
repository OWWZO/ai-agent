/**
 * 会话轮次导航的纯逻辑层。
 *
 * 移植自 ZCode `v4/conversationTurnNavigatorHelpers.ts`，但去掉虚拟列表依赖：
 * 本项目消息是 `deferredChatList.map` **全量 DOM**（`ChatView/index.tsx`），
 * 活跃项直接用 DOM 测量算，不需要 `@tanstack/react-virtual`。
 *
 * 视觉状态（`resolveBarVisualState`）的数值**逐值照抄** ZCode，
 * 因为那是设计稿定死的刻度手感：越靠近焦点越粗越长、越不透明。
 */

import { parseComposerPromptContexts } from "@/lib/promptContexts";

export interface ConversationNavigatorItem {
  /** 稳定 key，用于 React 列表 */
  key: string;
  requestId: string;
  userPreview: string;
  assistantPreview: string;
  isRunning: boolean;
}

export type NavigatorBarTone = "idle" | "mid" | "near" | "peak";
export type NavigatorBarColorTone = "focus" | "muted";

export interface NavigatorBarVisualState {
  colorTone: NavigatorBarColorTone;
  opacity: number;
  scaleX: number;
  tone: NavigatorBarTone;
}

export const DEFAULT_MAX_PREVIEW_CHARS = 220;
export const DEFAULT_MAX_PREVIEW_PARAGRAPHS = 2;

export const ASSISTANT_EMPTY_PREVIEW = "暂无助手正文";
export const ASSISTANT_RUNNING_PREVIEW = "助手仍在工作";
export const USER_FALLBACK_PREVIEW = "（空消息）";

function normalizePreviewParagraphs(text: string, maxParagraphs: number): string[] {
  return text
    .trim()
    .split(/\n\s*\n/u)
    .map((paragraph) => paragraph.replace(/\s+/gu, " ").trim())
    .filter(Boolean)
    .slice(0, Math.max(1, maxParagraphs));
}

function truncatePreview(text: string, maxChars: number): string {
  const normalizedMaxChars = Math.max(8, maxChars);
  if (text.length <= normalizedMaxChars) {
    return text;
  }
  return `${text.slice(0, normalizedMaxChars - 3).trimEnd()}...`;
}

/** 把多段文本压成「最多 N 段、最多 M 字符」的预览，超长用 `...` 截断。 */
export function buildPreviewText(
  texts: readonly string[],
  options: {
    fallback: string;
    maxPreviewChars?: number;
    maxPreviewParagraphs?: number;
  }
): string {
  const maxPreviewChars = options.maxPreviewChars ?? DEFAULT_MAX_PREVIEW_CHARS;
  const maxPreviewParagraphs =
    options.maxPreviewParagraphs ?? DEFAULT_MAX_PREVIEW_PARAGRAPHS;
  const paragraphs = normalizePreviewParagraphs(
    texts.join("\n\n"),
    maxPreviewParagraphs
  );
  if (paragraphs.length === 0) {
    return options.fallback;
  }
  return truncatePreview(paragraphs.join("\n"), maxPreviewChars);
}

/**
 * 每条「用户可见 query」建一个导航项。
 *
 * 用户正文会先剥掉上下文尾块（`# Code comments:` / `# Web page elements:` /
 * `# userselect:`），否则浮层里会显示原始 markdown。
 */
export function buildNavigatorItems(
  chats: readonly CHAT.ChatItem[]
): ConversationNavigatorItem[] {
  return chats.flatMap((chat, index) => {
    const visibleQuery = parseComposerPromptContexts(chat.query || "").visibleContent.trim();
    if (!visibleQuery) {
      return [];
    }

    const assistantText = (chat.response || "").trim();
    const assistantPreview = assistantText
      ? buildPreviewText([assistantText], { fallback: ASSISTANT_EMPTY_PREVIEW })
      : chat.loading
        ? ASSISTANT_RUNNING_PREVIEW
        : ASSISTANT_EMPTY_PREVIEW;

    return [
      {
        key: chat.requestId || `turn-${index}`,
        requestId: chat.requestId,
        userPreview: buildPreviewText([visibleQuery], {
          fallback: USER_FALLBACK_PREVIEW,
        }),
        assistantPreview,
        isRunning: Boolean(chat.loading),
      },
    ];
  });
}

/**
 * 刻度视觉状态：距焦点越近越粗越长。
 *
 * 数值照抄 ZCode（peak 2.6 / near 1.7 / mid 1.25 / idle 1.0，
 * opacity 1 / .86 / .72 / .58），不要「优化」成别的曲线。
 */
export function resolveBarVisualState(
  itemIndex: number,
  visualFocusItemIndex: number | undefined
): NavigatorBarVisualState {
  if (visualFocusItemIndex === undefined) {
    return { colorTone: "muted", opacity: 0.58, scaleX: 1, tone: "idle" };
  }

  const distance = Math.abs(itemIndex - visualFocusItemIndex);
  if (distance === 0) {
    return { colorTone: "focus", opacity: 1, scaleX: 2.6, tone: "peak" };
  }
  if (distance === 1) {
    return { colorTone: "muted", opacity: 0.86, scaleX: 1.7, tone: "near" };
  }
  if (distance === 2) {
    return { colorTone: "muted", opacity: 0.72, scaleX: 1.25, tone: "mid" };
  }
  return { colorTone: "muted", opacity: 0.58, scaleX: 1, tone: "idle" };
}

/**
 * 按「离区域顶部最近」挑活跃项。
 *
 * 输入是每个锚点相对区域顶部的偏移（px），越接近 0 越活跃。
 */
export function resolveActiveItemIndex(
  anchorOffsets: readonly number[]
): number | undefined {
  if (anchorOffsets.length === 0) {
    return undefined;
  }
  let bestIndex = 0;
  let bestDistance = Number.POSITIVE_INFINITY;
  anchorOffsets.forEach((offset, index) => {
    if (!Number.isFinite(offset)) {
      return;
    }
    const distance = Math.abs(offset);
    if (distance < bestDistance) {
      bestDistance = distance;
      bestIndex = index;
    }
  });
  return Number.isFinite(bestDistance) ? bestIndex : undefined;
}
