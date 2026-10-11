import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { CodeCommentLabels, CodeCommentRange } from "./types";
import {
  formatCodeCommentCancelShortcutLabel,
  formatCodeCommentSubmitShortcutLabel,
  formatCommentRange,
  isCodeCommentCancelShortcut,
  isCodeCommentSubmitShortcut,
} from "./utils";

/**
 * 行内评论草稿框 —— 挂在目标行**下方**（`lineAnnotations` + `renderAnnotation`），
 * 与 ZCode 一致的内联形态。
 */
export function CommentDraft({
  range,
  labels,
  value,
  onValueChange,
  onSubmit,
  onCancel,
}: {
  range: CodeCommentRange;
  labels: CodeCommentLabels;
  value: string;
  onValueChange: (value: string) => void;
  onSubmit: () => void;
  onCancel: () => void;
}) {
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const cancelLabel = `${labels.cancelComment} (${formatCodeCommentCancelShortcutLabel()})`;
  const submitLabel = `${labels.submitComment} (${formatCodeCommentSubmitShortcutLabel()})`;

  useEffect(() => {
    const frameId = requestAnimationFrame(() => {
      textareaRef.current?.focus();
    });
    return () => cancelAnimationFrame(frameId);
  }, []);

  return (
    <div className="m-2 rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] p-3 font-sans shadow-sm">
      <div className="mb-2 text-ui-base text-[var(--chat-text-soft)]">
        {formatCommentRange(labels, range)}
      </div>
      <Textarea
        ref={textareaRef}
        value={value}
        onChange={(event) => onValueChange(event.currentTarget.value)}
        onKeyDown={(event) => {
          if (isCodeCommentSubmitShortcut(event)) {
            event.preventDefault();
            onSubmit();
            return;
          }
          if (!isCodeCommentCancelShortcut(event)) {
            return;
          }
          event.preventDefault();
          onCancel();
        }}
        placeholder={labels.commentPlaceholder}
        className="min-h-16 w-full resize-none rounded-lg border-[var(--chat-border)] bg-[var(--chat-surface-soft)] text-ui-base text-[var(--chat-text)] placeholder:text-[var(--chat-text-muted)] focus-visible:ring-0"
        rows={3}
        autoFocus
      />
      <div className="mt-2 flex items-center justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" title={cancelLabel} aria-label={cancelLabel} onClick={onCancel}>
          {cancelLabel}
        </Button>
        <Button type="button" size="sm" title={submitLabel} aria-label={submitLabel} onClick={onSubmit}>
          {submitLabel}
        </Button>
      </div>
    </div>
  );
}
