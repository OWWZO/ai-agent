import { Trash2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { CodeCommentLabels, CodeCommentPreview } from "./types";
import { formatCommentRange } from "./utils";

/** 已提交评论的行内卡片（挂在目标行下方）。 */
export function CodeCommentAnnotation({
  comment,
  labels,
  onDelete,
  showRange = true,
}: {
  comment: CodeCommentPreview;
  labels: CodeCommentLabels;
  onDelete?: (commentId: string) => void;
  showRange?: boolean;
}) {
  return (
    <div
      data-code-comment-id={comment.id}
      className="m-2 rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)] p-3 font-sans shadow-sm"
    >
      {showRange ? (
        <div className="mb-2 text-ui-base text-[var(--chat-text-soft)]">
          {formatCommentRange(labels, comment)}
        </div>
      ) : null}
      {comment.comment.trim() ? (
        <div className="whitespace-pre-wrap text-ui-base leading-relaxed text-[var(--chat-text)]">
          {comment.comment}
        </div>
      ) : null}
      {onDelete ? (
        <div className="mt-2 flex items-center justify-end">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-[var(--chat-text-soft)] hover:text-[var(--chat-text)]"
            title={labels.deleteComment}
            aria-label={labels.deleteComment}
            onClick={() => onDelete(comment.id)}
          >
            <Trash2Icon className="size-4" />
            {labels.deleteComment}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
