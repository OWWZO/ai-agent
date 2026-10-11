import { WrapText } from "lucide-react";
import { cn } from "@/lib/utils";

interface CodeWrapToggleProps {
  wrap: boolean;
  onToggle: () => void;
  className?: string;
}

/**
 * 代码预览的「自动换行」开关。
 *
 * 做成图标按钮而不是文字分段控件：它要浮在代码区右上角，必须足够小、
 * 且不与 `PreviewModeToggle`（预览/源码）抢视觉重心。
 *
 * 视觉与 `PreviewModeToggle` 同源（同一套 `--chat-*` token、同样的圆角与
 * 选中态），保证同一块面板里的控件观感一致。
 */
export function CodeWrapToggle(props: CodeWrapToggleProps) {
  const { wrap, onToggle, className } = props;
  const label = wrap ? "自动换行：已开启" : "自动换行：已关闭";

  return (
    <button
      type="button"
      aria-pressed={wrap}
      aria-label="自动换行"
      title={label}
      onClick={onToggle}
      className={cn(
        "inline-flex h-7 w-7 items-center justify-center rounded-md border border-[var(--chat-border)]/60 shadow-sm backdrop-blur transition-colors",
        wrap
          ? "bg-[var(--chat-surface)] text-[var(--chat-text)]"
          : "bg-[var(--chat-surface)]/90 text-[var(--chat-text-muted)] hover:text-[var(--chat-text)]",
        className
      )}
    >
      <WrapText className="h-3.5 w-3.5" />
    </button>
  );
}

export default CodeWrapToggle;
