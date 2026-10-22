import type { ReactNode } from "react";
import { MessageSquareTextIcon, MousePointer2Icon, QuoteIcon, XIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ComposerPromptContextState } from "@/hooks/useComposerPromptContexts";

const CHIP_CLASS =
  "cute-composer-context-chip group flex min-w-0 max-w-[min(100%,300px)] items-center gap-1.5 rounded-full border border-[var(--color-line)] bg-[var(--color-surface-raised)] px-1.5 py-1 text-ui-sm shadow-none transition-colors hover:border-[var(--color-line-strong)]";

function ChipShell({
  icon,
  title,
  subtitle,
  removeLabel,
  onRemove,
}: {
  icon: ReactNode;
  title: string;
  subtitle?: string;
  removeLabel: string;
  onRemove: () => void;
}) {
  return (
    <div className={CHIP_CLASS} title={subtitle ? `${title}\n${subtitle}` : title}>
      <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-[var(--color-surface-sunken)]">
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <div className="truncate text-ui-sm font-medium text-[var(--color-text)]">{title}</div>
        {subtitle ? (
          <div className="truncate text-ui-xs leading-3.5 text-[var(--color-text-muted)]">
            {subtitle}
          </div>
        ) : null}
      </div>
      <button
        type="button"
        aria-label={removeLabel}
        title={removeLabel}
        className="flex size-5 shrink-0 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-hover)] hover:text-[var(--color-text)]"
        onClick={(event) => {
          event.stopPropagation();
          onRemove();
        }}
      >
        <XIcon className="size-3" />
      </button>
    </div>
  );
}

interface PromptContextChipsProps {
  contexts: ComposerPromptContextState;
  onRemoveCodeComment: (id: string) => void;
  onRemoveWebElement: (id: string) => void;
  onRemoveConversationSelection: (id: string) => void;
  className?: string;
}

function truncate(text: string, max = 60): string {
  const normalized = text.replace(/\s+/g, " ").trim();
  return normalized.length > max ? `${normalized.slice(0, max)}…` : normalized;
}

/**
 * 「预览区 → 聊天输入框」的上下文 chip 行。
 *
 * 三条链路（代码评论 / 网页元素 / Markdown 选段）共用这一行，删除即从 prompt 尾块中移除。
 */
export function PromptContextChips({
  contexts,
  onRemoveCodeComment,
  onRemoveWebElement,
  onRemoveConversationSelection,
  className,
}: PromptContextChipsProps) {
  const hasAny =
    contexts.codeComments.length > 0 ||
    contexts.webElements.length > 0 ||
    contexts.conversationSelections.length > 0;
  if (!hasAny) return null;

  return (
    <div className={cn("flex flex-wrap items-center gap-1.5", className)}>
      {contexts.codeComments.map((comment) => {
        const lineLabel =
          comment.startLine === comment.endLine
            ? `L${comment.startLine}`
            : `L${comment.startLine}-L${comment.endLine}`;
        return (
          <ChipShell
            key={comment.id}
            icon={<MessageSquareTextIcon className="size-3.5 text-[var(--color-text-muted)]" />}
            title={`${comment.sourceTitle} ${lineLabel}`}
            subtitle={comment.comment.trim() ? truncate(comment.comment) : comment.sourcePath}
            removeLabel="移除代码评论"
            onRemove={() => onRemoveCodeComment(comment.id)}
          />
        );
      })}

      {contexts.webElements.map((element) => {
        const title =
          element.accessibleName || element.text || element.selector || element.tagName.toLowerCase();
        const meta = [element.tagName.toLowerCase(), element.role ? `role=${element.role}` : null]
          .filter(Boolean)
          .join(" · ");
        return (
          <ChipShell
            key={element.id}
            icon={<MousePointer2Icon className="size-3.5 text-[var(--color-text-muted)]" />}
            title={truncate(title, 40)}
            subtitle={element.selector ? `${meta} · ${element.selector}` : meta}
            removeLabel="移除网页元素"
            onRemove={() => onRemoveWebElement(element.id)}
          />
        );
      })}

      {contexts.conversationSelections.map((selection) => (
        <ChipShell
          key={selection.id}
          icon={<QuoteIcon className="size-3.5 text-[var(--color-text-muted)]" />}
          title={selection.path ? selection.path.split(/[\\/]/).pop() || selection.path : "选中的正文"}
          subtitle={truncate(selection.text)}
          removeLabel="移除选中文本"
          onRemove={() => onRemoveConversationSelection(selection.id)}
        />
      ))}
    </div>
  );
}

export default PromptContextChips;
