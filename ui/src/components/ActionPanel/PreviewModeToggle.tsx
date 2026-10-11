import { Code2, Eye } from "lucide-react";
import { cn } from "@/lib/utils";

export type PreviewViewMode = "preview" | "source";

interface PreviewModeToggleProps {
  mode: PreviewViewMode;
  onChange: (mode: PreviewViewMode) => void;
  className?: string;
  previewLabel?: string;
  sourceLabel?: string;
}

/**
 * 「预览 ⇄ 源码」分段切换控件，供 Markdown / SVG 等双模式预览共用。
 */
export function PreviewModeToggle(props: PreviewModeToggleProps) {
  const {
    mode,
    onChange,
    className,
    previewLabel = "预览",
    sourceLabel = "源码",
  } = props;

  const items: Array<{ key: PreviewViewMode; label: string; Icon: typeof Eye }> = [
    { key: "preview", label: previewLabel, Icon: Eye },
    { key: "source", label: sourceLabel, Icon: Code2 },
  ];

  return (
    <div
      role="group"
      aria-label="预览模式"
      className={cn(
        "inline-flex shrink-0 items-center gap-0.5 rounded-lg border border-[var(--chat-border)]/60 bg-[var(--chat-surface-muted)] p-0.5",
        className
      )}
    >
      {items.map(({ key, label, Icon }) => {
        const active = mode === key;
        return (
          <button
            key={key}
            type="button"
            aria-pressed={active}
            aria-label={label}
            title={label}
            onClick={() => onChange(key)}
            className={cn(
              "inline-flex items-center gap-1 rounded-md px-2 py-1 text-[12px] transition-colors",
              active
                ? "bg-[var(--chat-surface)] text-[var(--chat-text)] shadow-sm"
                : "text-[var(--chat-text-muted)] hover:text-[var(--chat-text)]"
            )}
          >
            <Icon className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{label}</span>
          </button>
        );
      })}
    </div>
  );
}

export default PreviewModeToggle;
