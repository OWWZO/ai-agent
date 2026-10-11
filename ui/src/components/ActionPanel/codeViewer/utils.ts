import type { FileContents, SupportedLanguages } from "@pierre/diffs";
import type { BundledTheme } from "shiki";
import type { CodeCommentLabels, CodeCommentRange, SelectedLineRange } from "./types";

/** 中文 labels（ZCode 为英文，移植时本地化）。 */
export const DEFAULT_CODE_COMMENT_LABELS: CodeCommentLabels = {
  addComment: "添加评论",
  addCommentTooltip: "点击或拖拽选择行后添加评论",
  commentPlaceholder: "写下这里要怎么改",
  submitComment: "添加评论",
  cancelComment: "取消",
  deleteComment: "删除评论",
  commentLine: "第 {line} 行",
  commentRange: "第 {startLine}-{endLine} 行",
};

/**
 * 注入到 `@pierre/diffs` **Shadow DOM** 的样式（库的 `unsafeCSS` 选项，
 * 会落到层序最高的 `@layer unsafe`，可以覆盖库自身的规则）。
 *
 * 目前两件事：
 *
 * 1. **gutter `+` 按钮的位置与 hover tooltip。**
 *    ⚠️ `[data-gutter-utility-slot]` / `[data-utility-button]` 是 `@pierre/diffs` 的
 *    **内部 DOM 契约**（1.5.2 实测仍在 `dist/utils/createGutterUtilityElement.js` 与
 *    `dist/style.js` 中），升级库版本时必须重新核对。
 *
 * 2. **非换行模式下的横向滚动条。**
 *    库把横向溢出交给 `<pre data-file>` 内部的 `[data-code]`
 *    （`overflow: var(--diffs-overflow-override, scroll) clip`），
 *    而纵向溢出由本组件外层的 `overflow-auto` 负责 —— 所以两条滚动条**不属于同一个元素**：
 *    纵向那条吃的是 `global.css` 的全局 `::-webkit-scrollbar`（可见），
 *    横向那条由库在 shadow root 里画，而库把它设成了**全透明**
 *    （`[data-code]::-webkit-scrollbar-thumb{background-color:#0000}`，
 *    hover 时才给 `var(--diffs-bg-context)` —— 那是 98.5% 背景色，等于看不见）。
 *    全局样式进不了 shadow root，只能在这里补。
 *
 *    两点约束：
 *    - 用 `[data-overflow=scroll]`（挂在 `<pre>` 上，`createPreWrapperProperties`
 *      写入）做祖先限定，**换行模式（`data-overflow=wrap`）不生效**，
 *      不会留下一条空的滚动条槽；
 *    - 先把 `scrollbar-color` / `scrollbar-width` 复位成 `auto`，
 *      否则 Chrome 121+ 一旦看到非 `auto` 的 `scrollbar-color`
 *      就会**禁用 `::-webkit-scrollbar` 伪元素**，下面几条全白写。
 *      纵向保持 `width:0`（库是有意的，垂直滚动不在这里）。
 */
export const CODE_VIEWER_UNSAFE_CSS = [
  "[data-gutter-utility-slot]{left:auto;right:4px;justify-content:flex-end;align-items:center;}",
  "[data-utility-button]{margin-left:0;margin-right:0;}",
  "@media (hover:hover) and (pointer:fine){",
  "[data-utility-button]::after{content:var(--code-comment-add-tooltip);position:absolute;left:calc(100% + 6px);top:50%;transform:translateY(-50%);max-width:16rem;white-space:nowrap;pointer-events:none;opacity:0;z-index:5;border:1px solid var(--chat-border);border-radius:8px;background:var(--chat-text);color:var(--chat-surface);padding:4px 8px;font-family:var(--font-mono);font-size:12px;line-height:16px;box-shadow:0 4px 12px rgb(0 0 0 / 0.14);}",
  "[data-utility-button]:hover::after,[data-utility-button]:focus-visible::after{opacity:1;}",
  "}",
  // 横向滚动条：仅在「非换行」模式出现，观感对齐 global.css 的全局滚动条
  "[data-overflow=scroll] [data-code]{scrollbar-width:auto;scrollbar-color:auto;}",
  "[data-overflow=scroll] [data-code]::-webkit-scrollbar{width:0;height:8px;}",
  "[data-overflow=scroll] [data-code]::-webkit-scrollbar-track{background:transparent;}",
  "[data-overflow=scroll] [data-code]::-webkit-scrollbar-thumb{background-color:rgba(134,134,139,0.3);background-clip:padding-box;border:2px solid transparent;border-radius:100px;}",
  "[data-overflow=scroll] [data-code]::-webkit-scrollbar-thumb:hover{background-color:rgba(134,134,139,0.5);}",
  "[data-overflow=scroll] [data-code]::-webkit-scrollbar-corner{background:transparent;}",
].join("");

/**
 * 被点名行号的着色规则。行号格是 `@pierre/diffs` 在 Shadow DOM 里画的
 * `[data-column-number="<行号>"]`，外层 class 进不去，只能随 unsafeCSS 注入；
 * 颜色走应用 token（自定义属性可穿透 shadow root），深浅主题自动跟随。
 * 非正整数与重复值丢弃，空集合返回空串。
 */
export function codeViewerMarkedLinesCss(lines: readonly number[] | undefined): string {
  const unique = [...new Set(lines ?? [])].filter((line) => Number.isInteger(line) && line > 0);
  if (unique.length === 0) return "";
  const selector = unique.map((line) => `[data-column-number="${line}"]`).join(",");
  return `${selector}{color:var(--color-warning);}`;
}

export function toCssString(value: string): string {
  return JSON.stringify(value);
}

export function hashCodeViewerContent(code: string): string {
  let hash = 5381;
  for (let index = 0; index < code.length; index += 1) {
    hash = (hash * 33) ^ code.charCodeAt(index);
  }
  return (hash >>> 0).toString(36);
}

export function createCodeViewerFile(params: {
  code: string;
  enableSyntaxHighlighting: boolean;
  language: string;
  theme?: string;
}): FileContents {
  const lang = params.enableSyntaxHighlighting
    ? (params.language as SupportedLanguages)
    : ("text" as SupportedLanguages);
  const name = params.language ? `preview.${params.language}` : "preview.txt";
  const themeCacheKey = params.theme ?? "auto";

  return {
    name,
    contents: params.code,
    lang,
    // @pierre/diffs 会按 file.cacheKey 复用 token；key 不带主题时，同一预览内切换
    // 明暗主题会继续命中旧高亮。
    cacheKey: `${themeCacheKey}:${name}:${lang}:${params.code.length}:${hashCodeViewerContent(
      params.code
    )}`,
  };
}

export function normalizeRange(range: CodeCommentRange): CodeCommentRange {
  return {
    startLine: Math.min(range.startLine, range.endLine),
    endLine: Math.max(range.startLine, range.endLine),
  };
}

export function selectedRangeToCodeCommentRange(
  range: SelectedLineRange,
  firstLineNumber: number
): CodeCommentRange {
  return normalizeRange({
    startLine: firstLineNumber + range.start - 1,
    endLine: firstLineNumber + range.end - 1,
  });
}

export function codeCommentRangeToSelectedRange(
  range: CodeCommentRange,
  firstLineNumber: number
): SelectedLineRange {
  const normalized = normalizeRange(range);
  return {
    start: normalized.startLine - firstLineNumber + 1,
    end: normalized.endLine - firstLineNumber + 1,
  };
}

export function getTextForLineRange(
  code: string,
  firstLineNumber: number,
  range: CodeCommentRange
): string {
  const lines = code.split("\n");
  const normalized = normalizeRange(range);
  const startIndex = Math.max(normalized.startLine - firstLineNumber, 0);
  const endIndex = Math.min(normalized.endLine - firstLineNumber, lines.length - 1);
  if (startIndex > endIndex) {
    return "";
  }
  return lines.slice(startIndex, endIndex + 1).join("\n");
}

export function formatCommentRange(labels: CodeCommentLabels, range: CodeCommentRange): string {
  const normalized = normalizeRange(range);
  if (normalized.startLine === normalized.endLine) {
    return labels.commentLine.replace(/\{line\}/g, String(normalized.startLine));
  }
  return labels.commentRange
    .replace(/\{startLine\}/g, String(normalized.startLine))
    .replace(/\{endLine\}/g, String(normalized.endLine));
}

// ---- 快捷键 ----

function isApplePlatform(): boolean {
  if (typeof navigator === "undefined") return false;
  const nav = navigator as Navigator & { userAgentData?: { platform?: string } };
  const platform = nav.userAgentData?.platform || nav.platform || nav.userAgent || "";
  return /mac|iphone|ipad|ipod/i.test(platform);
}

export function isCodeCommentSubmitShortcut(
  event: Pick<KeyboardEvent, "ctrlKey" | "key" | "metaKey">
): boolean {
  if (event.key !== "Enter") return false;
  return isApplePlatform() ? event.metaKey : event.ctrlKey;
}

export function isCodeCommentCancelShortcut(event: Pick<KeyboardEvent, "key">): boolean {
  return event.key === "Escape";
}

export function formatCodeCommentSubmitShortcutLabel(): string {
  return isApplePlatform() ? "⌘↵" : "Ctrl+↵";
}

export function formatCodeCommentCancelShortcutLabel(): string {
  return "Esc";
}

// ---- Shadow DOM 穿透 ----

export function patchCodeCommentUtilityButtons(root: ParentNode, label: string): void {
  for (const button of root.querySelectorAll<HTMLButtonElement>("button[data-utility-button]")) {
    button.title = label;
    button.setAttribute("aria-label", label);
  }
}

export function walkShadowRoots(root: ParentNode, callback: (root: ShadowRoot) => void): void {
  for (const element of root.querySelectorAll<HTMLElement>("*")) {
    if (!element.shadowRoot) continue;
    callback(element.shadowRoot);
    walkShadowRoots(element.shadowRoot, callback);
  }
}

/**
 * 穿透 shadow root 找某一行的 DOM 节点。
 *
 * ⚠️ `[data-line="N"]` 同样是 `@pierre/diffs` 的内部契约（1.5.2 在
 * `dist/managers/InteractionManager.js` 中仍在使用），升级时必须复核。
 */
export function findCodeViewerLineElement(
  root: ParentNode,
  lineNumber: number
): HTMLElement | null {
  const target = root.querySelector<HTMLElement>(`[data-line="${lineNumber}"]`);
  if (target) return target;

  for (const element of root.querySelectorAll<HTMLElement>("*")) {
    if (!element.shadowRoot) continue;
    const shadowTarget = findCodeViewerLineElement(element.shadowRoot, lineNumber);
    if (shadowTarget) return shadowTarget;
  }
  return null;
}

export function findCodeViewerCommentElement(
  root: ParentNode,
  commentId: string
): HTMLElement | null {
  for (const element of root.querySelectorAll<HTMLElement>("[data-code-comment-id]")) {
    if (element.dataset.codeCommentId === commentId) {
      return element;
    }
  }

  for (const element of root.querySelectorAll<HTMLElement>("*")) {
    if (!element.shadowRoot) continue;
    const shadowTarget = findCodeViewerCommentElement(element.shadowRoot, commentId);
    if (shadowTarget) return shadowTarget;
  }
  return null;
}

export type { BundledTheme };
