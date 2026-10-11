import { MessageSquareTextIcon, MousePointer2Icon, QuoteIcon } from "lucide-react";
import type { SerializedPromptContexts } from "@/lib/promptContexts";

const CARD_CLASS =
  "flex max-w-[320px] min-w-0 items-center gap-1.5 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface-muted)] px-2.5 py-1 text-ui-xs text-[var(--chat-text-soft)]";

function truncate(text: string, max = 48): string {
  const normalized = text.replace(/\s+/g, " ").trim();
  return normalized.length > max ? `${normalized.slice(0, max)}…` : normalized;
}

/**
 * 用户消息里回显的预览区上下文（只读）。
 *
 * 用户消息正文里其实拼着 `# Code comments:` / `# Web page elements:` / `# userselect:`
 * 三段尾块；`Dialogue` 先用 `parseComposerPromptContexts` 把它们剥出来，
 * 正文只渲染 `visibleContent`，剥出来的上下文在这里渲染成卡片，
 * 否则用户会看到一大坨原始 markdown 糊在气泡里。
 */
export function UserPromptContexts({ contexts }: { contexts: SerializedPromptContexts }) {
  const hasAny =
    contexts.codeComments.length > 0 ||
    contexts.webElements.length > 0 ||
    contexts.conversationSelections.length > 0;
  if (!hasAny) return null;

  return (
    <div className="flex w-full flex-wrap justify-end gap-1.5">
      {contexts.codeComments.map((comment) => {
        const lineLabel =
          comment.startLine === comment.endLine
            ? `L${comment.startLine}`
            : `L${comment.startLine}-L${comment.endLine}`;
        return (
          <div
            key={comment.id}
            className={CARD_CLASS}
            title={comment.comment || comment.sourcePath}
          >
            <MessageSquareTextIcon className="size-3.5 shrink-0 text-[var(--chat-text-muted)]" />
            <span className="shrink-0 font-medium text-[var(--chat-text)]">{comment.sourceTitle}</span>
            <span className="shrink-0 font-mono">{lineLabel}</span>
            {comment.comment.trim() ? (
              <span className="truncate">· {truncate(comment.comment)}</span>
            ) : null}
          </div>
        );
      })}

      {contexts.webElements.map((element) => {
        const title =
          element.accessibleName ||
          element.text ||
          element.selector ||
          element.tagName.toLowerCase();
        return (
          <div
            key={element.id}
            className={CARD_CLASS}
            title={[element.pageTitle || element.pageUrl, element.selector]
              .filter(Boolean)
              .join("\n")}
          >
            <MousePointer2Icon className="size-3.5 shrink-0 text-[var(--chat-text-muted)]" />
            <span className="shrink-0 font-medium text-[var(--chat-text)]">
              {element.tagName.toLowerCase()}
            </span>
            <span className="truncate">{truncate(title)}</span>
          </div>
        );
      })}

      {contexts.conversationSelections.map((selection) => (
        <div key={selection.id} className={CARD_CLASS} title={selection.text}>
          <QuoteIcon className="size-3.5 shrink-0 text-[var(--chat-text-muted)]" />
          {selection.path ? (
            <span className="shrink-0 font-medium text-[var(--chat-text)]">
              {selection.path.split(/[\\/]/).pop() || selection.path}
            </span>
          ) : null}
          <span className="truncate">{truncate(selection.text)}</span>
        </div>
      ))}
    </div>
  );
}

export default UserPromptContexts;
