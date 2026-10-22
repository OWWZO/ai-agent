import { createPromptContextId } from "./ids";

/**
 * 网页元素上下文（ZCode `lib/webElementContext.ts` 的移植版）。
 *
 * 尾块格式与 ZCode 逐字段一致：
 *
 * ```
 * # Web page elements:
 *
 * ## Element 1
 * URL: http://localhost:3000/index.html
 * Title: 首页
 * Tag: button
 * Selector: #submit
 * Color: #FFFFFF
 * Background: #2563EB
 * Font: 14px Inter, sans-serif
 * Rect: x=120, y=340, width=88, height=32
 *
 * Text:
 * ```
 * 提交订单
 * ```
 *
 * HTML excerpt:
 * ```html
 * <button id="submit" class="btn btn-primary">提交订单</button>
 * ```
 * ```
 */
export const WEB_ELEMENT_ADD_TO_CHAT_EVENT = "cute:web-element-add-to-chat";
export const WEB_ELEMENT_REMOVE_FROM_CHAT_EVENT = "cute:web-element-remove-from-chat";

const BLOCK_TITLE = "# Web page elements:";
const MAX_MARKDOWN_FIELD_LENGTH = 8_000;

export interface WebElementRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface WebElementStyleSummary {
  color?: string;
  backgroundColor?: string;
  fontFamily?: string;
  fontSize?: string;
  fontWeight?: string;
  display?: string;
}

export interface WebElementPayload {
  id?: string;
  pageUrl: string;
  pageTitle: string;
  tagName: string;
  role?: string;
  accessibleName?: string;
  selector?: string;
  xpath?: string;
  text?: string;
  nearbyText?: string;
  htmlExcerpt?: string;
  attributes?: Record<string, string>;
  rect?: WebElementRect;
  style?: WebElementStyleSummary;
  capturedAt: number;
}

export interface WebElementAttachment extends WebElementPayload {
  id: string;
}

export interface WebElementRemovePayload {
  id: string;
}

export interface ParsedWebElementPrompt {
  visibleContent: string;
  webElementContexts: WebElementAttachment[];
}

type WebElementAddToChatEvent = CustomEvent<WebElementPayload>;
type WebElementRemoveFromChatEvent = CustomEvent<WebElementRemovePayload>;

export function isWebElementAddToChatEvent(event: Event): event is WebElementAddToChatEvent {
  return (
    event.type === WEB_ELEMENT_ADD_TO_CHAT_EVENT &&
    "detail" in event &&
    typeof (event as CustomEvent<unknown>).detail === "object" &&
    (event as CustomEvent<unknown>).detail !== null
  );
}

export function isWebElementRemoveFromChatEvent(
  event: Event
): event is WebElementRemoveFromChatEvent {
  return (
    event.type === WEB_ELEMENT_REMOVE_FROM_CHAT_EVENT &&
    "detail" in event &&
    typeof (event as CustomEvent<unknown>).detail === "object" &&
    (event as CustomEvent<unknown>).detail !== null
  );
}

export function isWebElementPayload(payload: unknown): payload is WebElementPayload {
  const candidate = payload as WebElementPayload;
  return (
    typeof payload === "object" &&
    payload !== null &&
    typeof candidate.pageUrl === "string" &&
    candidate.pageUrl.length > 0 &&
    typeof candidate.pageTitle === "string" &&
    typeof candidate.tagName === "string" &&
    candidate.tagName.length > 0 &&
    typeof candidate.capturedAt === "number"
  );
}

function truncateMarkdownValue(value: string | undefined): string {
  if (!value) {
    return "";
  }

  const normalized = value.trim();
  return normalized.length > MAX_MARKDOWN_FIELD_LENGTH
    ? `${normalized.slice(0, MAX_MARKDOWN_FIELD_LENGTH)}\n\n[truncated]`
    : normalized;
}

function appendOptionalLine(lines: string[], label: string, value: string | undefined): void {
  const normalized = truncateMarkdownValue(value);
  if (normalized) {
    lines.push(`${label}: ${normalized}`);
  }
}

function formatAttributes(attributes: Record<string, string> | undefined): string {
  if (!attributes || Object.keys(attributes).length === 0) {
    return "";
  }

  return Object.entries(attributes)
    .map(([key, value]) => `${key}=${JSON.stringify(value)}`)
    .join(" ");
}

function formatFont(style: WebElementStyleSummary | undefined): string {
  if (!style?.fontSize && !style?.fontFamily) {
    return "";
  }
  return [style.fontSize, style.fontFamily].filter(Boolean).join(" ");
}

function buildWebElementMarkdown(payload: WebElementPayload): string {
  const lines = [
    "## Element",
    `URL: ${payload.pageUrl}`,
    `Title: ${payload.pageTitle || "(untitled)"}`,
    `Tag: ${payload.tagName.toLowerCase()}`,
  ];

  appendOptionalLine(lines, "Role", payload.role);
  appendOptionalLine(lines, "Accessible name", payload.accessibleName);
  appendOptionalLine(lines, "Selector", payload.selector);
  appendOptionalLine(lines, "XPath", payload.xpath);
  appendOptionalLine(lines, "Attributes", formatAttributes(payload.attributes));
  appendOptionalLine(lines, "Color", payload.style?.color);
  appendOptionalLine(lines, "Background", payload.style?.backgroundColor);
  appendOptionalLine(lines, "Font", formatFont(payload.style));
  appendOptionalLine(lines, "Font weight", payload.style?.fontWeight);
  appendOptionalLine(lines, "Display", payload.style?.display);

  if (payload.rect) {
    lines.push(
      `Rect: x=${Math.round(payload.rect.x)}, y=${Math.round(payload.rect.y)}, width=${Math.round(
        payload.rect.width
      )}, height=${Math.round(payload.rect.height)}`
    );
  }

  const text = truncateMarkdownValue(payload.text);
  if (text) {
    lines.push("", "Text:", "```", text, "```");
  }

  const nearbyText = truncateMarkdownValue(payload.nearbyText);
  if (nearbyText) {
    lines.push("", "Nearby context:", "```", nearbyText, "```");
  }

  const htmlExcerpt = truncateMarkdownValue(payload.htmlExcerpt);
  if (htmlExcerpt) {
    lines.push("", "HTML excerpt:", "```html", htmlExcerpt, "```");
  }

  return lines.join("\n");
}

export function buildPromptWithWebElementContexts(
  text: string,
  contexts: readonly WebElementAttachment[]
): string {
  const content = text.trimEnd();
  if (contexts.length === 0) {
    return content.trim();
  }

  const contextBlock = `${BLOCK_TITLE}\n\n${contexts
    .map((context, index) =>
      buildWebElementMarkdown(context).replace("## Element", `## Element ${index + 1}`)
    )
    .join("\n\n")}`;

  return `${content}${content ? "\n\n" : ""}${contextBlock}`.trim();
}

function readField(rawItem: string, label: string): string {
  const match = new RegExp(`^${label}:\\s*(.*)$`, "m").exec(rawItem);
  return match?.[1]?.trim() ?? "";
}

function readFencedSection(rawItem: string, label: string): string {
  const escapedLabel = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = new RegExp(`${escapedLabel}:\\s*\\n\`\`\`(?:html)?\\n([\\s\\S]*?)\\n\`\`\``, "m").exec(
    rawItem
  );
  return match?.[1]?.trim() ?? "";
}

function parseFontSummary(font: string): Pick<WebElementStyleSummary, "fontFamily" | "fontSize"> {
  const match = /^([0-9.]+(?:px|rem|em|pt|%))\s+(.+)$/iu.exec(font);
  if (!match) {
    return { fontFamily: font };
  }
  const fontSize = match[1];
  const fontFamily = match[2];
  if (!fontSize || !fontFamily) {
    return { fontFamily: font };
  }

  return { fontSize, fontFamily };
}

function readStyleSummary(rawItem: string): WebElementStyleSummary | undefined {
  const color = readField(rawItem, "Color");
  const backgroundColor = readField(rawItem, "Background");
  const font = readField(rawItem, "Font");
  const fontWeight = readField(rawItem, "Font weight");
  const display = readField(rawItem, "Display");
  const style: WebElementStyleSummary = {
    ...(color ? { color } : {}),
    ...(backgroundColor ? { backgroundColor } : {}),
    ...(font ? parseFontSummary(font) : {}),
    ...(fontWeight ? { fontWeight } : {}),
    ...(display ? { display } : {}),
  };

  return Object.keys(style).length > 0 ? style : undefined;
}

function parseElementItem(rawItem: string, index: number): WebElementAttachment | null {
  const pageUrl = readField(rawItem, "URL");
  const pageTitle = readField(rawItem, "Title");
  const tagName = readField(rawItem, "Tag");

  if (!pageUrl || !tagName) {
    return null;
  }

  return {
    id: `parsed-web-element-${index + 1}-${tagName}`,
    pageUrl,
    pageTitle: pageTitle === "(untitled)" ? "" : pageTitle,
    tagName,
    role: readField(rawItem, "Role") || undefined,
    accessibleName: readField(rawItem, "Accessible name") || undefined,
    selector: readField(rawItem, "Selector") || undefined,
    xpath: readField(rawItem, "XPath") || undefined,
    text: readFencedSection(rawItem, "Text") || undefined,
    nearbyText: readFencedSection(rawItem, "Nearby context") || undefined,
    htmlExcerpt: readFencedSection(rawItem, "HTML excerpt") || undefined,
    style: readStyleSummary(rawItem),
    capturedAt: 0,
  };
}

export function parsePromptWebElementContexts(content: string): ParsedWebElementPrompt {
  const blockMatch = /(?:^|\n\n)# Web page elements:\s*\n\n([\s\S]*?)\s*$/.exec(content);
  if (!blockMatch || blockMatch.index < 0) {
    return { visibleContent: content, webElementContexts: [] };
  }

  const rawBlock = blockMatch[1];
  if (rawBlock === undefined) {
    return { visibleContent: content, webElementContexts: [] };
  }

  const rawItems = rawBlock
    .split(/\n(?=## Element(?:\s+\d+)?\n)/)
    .map((item) => item.trim())
    .filter(Boolean);
  const webElementContexts = rawItems
    .map((item, index) => parseElementItem(item, index))
    .filter((item): item is WebElementAttachment => item !== null);

  if (webElementContexts.length === 0) {
    return { visibleContent: content, webElementContexts: [] };
  }

  return {
    visibleContent: content.slice(0, blockMatch.index).trimEnd(),
    webElementContexts,
  };
}

export function dispatchWebElementAddToChat(payload: WebElementPayload): void {
  window.dispatchEvent(
    new CustomEvent(WEB_ELEMENT_ADD_TO_CHAT_EVENT, {
      cancelable: true,
      detail: { ...payload, id: payload.id ?? createPromptContextId("web-element") },
    })
  );
}

export function dispatchWebElementRemoveFromChat(payload: WebElementRemovePayload): void {
  window.dispatchEvent(
    new CustomEvent(WEB_ELEMENT_REMOVE_FROM_CHAT_EVENT, { detail: payload })
  );
}
