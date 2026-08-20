import { memo, useEffect, useRef } from "react";
import { X } from "lucide-react";

type ThinkingPanelProps = {
  text: string;
  title?: string;
  onClose?: () => void;
};

export const ThinkingPanel = memo(function ThinkingPanel({
  text,
  title = "深度思考",
  onClose,
}: ThinkingPanelProps) {
  const bodyRef = useRef<HTMLPreElement | null>(null);

  useEffect(() => {
    const el = bodyRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
    if (!atBottom) return;
    el.scrollTop = el.scrollHeight;
  }, [text]);

  return (
    <div className="kimi-thinking-panel">
      <div className="kimi-thinking-panel-head">
        <div className="min-w-0 flex-1">
          <div className="text-[12px] text-[var(--color-text-faint)]">预览</div>
          <div className="truncate text-[14px] font-medium text-[var(--color-text)]">
            {title}
          </div>
        </div>
        <button
          type="button"
          className="flex h-7 w-7 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-sunken)] hover:text-[var(--color-text)]"
          aria-label="关闭"
          onClick={onClose}
        >
          <X className="h-4 w-4" />
        </button>
      </div>
      <pre ref={bodyRef} className="kimi-thinking-panel-body">
        {text || "…"}
      </pre>
    </div>
  );
});
