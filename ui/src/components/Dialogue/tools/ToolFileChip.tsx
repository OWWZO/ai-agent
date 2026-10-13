import { memo, type MouseEvent, type ReactNode } from "react";
import { FileTextIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { pathBaseName } from "@/lib/codeViewerSource";

type ToolFileChipProps = {
  path: string;
  /** 展示名；缺省从 path 取 basename */
  label?: string;
  /** 自定义图标；缺省用 FileTextIcon */
  icon?: ReactNode;
  /** 传了就变成可点击按钮，触发打开右侧预览 */
  onOpen?: () => void;
  className?: string;
};

/**
 * 工具卡里的文件 chip（对齐 ZCode `ToolCallBlocks/renderers.tsx` `renderFileChip`）。
 *
 * 关键点（ZCode 原样保留）：
 * - 行头本身可能是可展开按钮，chip 必须 `stopPropagation` 两次
 *   （`onMouseDown` + `onClick`），否则点文件名会顺带把卡片折叠掉；
 * - `onMouseDown` 还要 `preventDefault`，避免行头抢走焦点后 chip 丢失点击；
 * - 不可点击时退化成 `<span>`，视觉完全一致。
 */
export const ToolFileChip = memo(function ToolFileChip({
  path,
  label,
  icon,
  onOpen,
  className,
}: ToolFileChipProps) {
  const leaf = label || pathBaseName(path) || path;
  // ZCode `renderFileChip` 用 `FileDisplayIcon size={16}` + `size-4 shrink-0`
  const glyph = icon ?? <FileTextIcon className="size-4 shrink-0" />;

  if (!onOpen) {
    return (
      <span
        className={cn(
          "inline-flex min-w-0 max-w-full items-center gap-1.5 text-foreground-subtle",
          className
        )}
        title={path}
      >
        {glyph}
        <span className="min-w-0 truncate">{leaf}</span>
      </span>
    );
  }

  const handleMouseDown = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
  };
  const handleClick = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    onOpen();
  };

  return (
    <button
      type="button"
      className={cn(
        "inline-flex min-w-0 max-w-full items-center gap-1.5 text-foreground-subtle hover:underline",
        className
      )}
      title={path}
      onMouseDown={handleMouseDown}
      onClick={handleClick}
    >
      {glyph}
      <span className="min-w-0 truncate">{leaf}</span>
    </button>
  );
});
