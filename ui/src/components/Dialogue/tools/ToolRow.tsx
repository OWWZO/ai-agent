import {
  useCallback,
  useRef,
  type KeyboardEvent,
  type ReactNode,
  type RefObject,
} from "react";
import { CheckIcon, ChevronDownIcon, ChevronRightIcon, XIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { StatusDot } from "./StatusDot";

export type ToolRowStackPosition = "single" | "first" | "middle" | "last";

type ToolRowProps = {
  status: "running" | "ok" | "error" | "suspended";
  icon?: ReactNode;
  name: string;
  arg?: string;
  /**
   * arg 的富节点形态（对齐 ZCode `renderFileChip`）：文件名 chip 要能点击打开右侧预览，
   * 纯字符串塞不进 button。给了 argNode 就优先渲染它，arg 字符串退为兜底。
   */
  argNode?: ReactNode;
  time?: string;
  chip?: string;
  /**
   * chip 的富节点形态（对齐 ZCode）：`+A −B` 这类计数需要逐字符翻页动画，
   * 纯字符串塞不进 FlipMetricValue。给了 chipNode 就优先渲染它，chip 字符串退为兜底。
   */
  chipNode?: ReactNode;
  open?: boolean;
  expandable?: boolean;
  stacked?: boolean;
  stackPosition?: ToolRowStackPosition;
  trailing?: ReactNode;
  children?: ReactNode;
  onToggle?: () => void;
  onOpenWorkspace?: () => void;
};

export function ToolRow({
  status,
  icon,
  name,
  arg = "",
  argNode,
  time = "",
  chip = "",
  chipNode,
  open = false,
  expandable = false,
  stacked = false,
  stackPosition = "single",
  trailing,
  children,
  onToggle,
  onOpenWorkspace,
}: ToolRowProps) {
  const headRef = useRef<HTMLButtonElement | null>(null);
  /**
   * argNode 里可能嵌套可点击的 button（文件名 chip）。
   * HTML 不允许 button 套 button，因此有 argNode 时行头退化成一个 div + role="button"，
   * 键盘可达性由内部 chip 自己承担；无 argNode 时保持原生的 <button>。
   */
  const headInteractive = !argNode;

  const pinScroll = useCallback(() => {
    const el = headRef.current;
    if (!el) return;
    const top = el.getBoundingClientRect().top;
    requestAnimationFrame(() => {
      const next = el.getBoundingClientRect().top;
      const delta = next - top;
      if (Math.abs(delta) > 1) {
        const scroller = el.closest(
          "[data-stick-to-bottom], .overflow-y-auto, .overflow-auto"
        );
        if (scroller instanceof HTMLElement) {
          scroller.scrollTop += delta;
        } else {
          window.scrollBy(0, delta);
        }
      }
    });
  }, []);

  const handleHeadClick = () => {
    if (onOpenWorkspace) {
      onOpenWorkspace();
      return;
    }
    if (expandable) {
      onToggle?.();
      pinScroll();
    }
  };

  const handleToggleClick = (event: { stopPropagation: () => void }) => {
    event.stopPropagation();
    onToggle?.();
    pinScroll();
  };

  return (
    <div
      className={cn(
        "kimi-tool-row",
        open && "is-open",
        stacked && "is-stacked",
        // 运行态：类别标签走 ZCode 的扫光渐变（见 global.css .kimi-tool-row.is-running）
        status === "running" && "is-running",
        status === "error" && "is-error",
        stackPosition === "first" && "is-stack-first",
        stackPosition === "middle" && "is-stack-middle",
        stackPosition === "last" && "is-stack-last"
      )}
    >
      <div className="kimi-tool-row-head">
        {headInteractive ? (
          <button
            ref={headRef}
            type="button"
            className="kimi-tool-row-head-main"
            onClick={handleHeadClick}
          >
            <ToolRowHeadContent
              icon={icon}
              name={name}
              arg={arg}
              argNode={argNode}
              status={status}
              chip={chip}
              chipNode={chipNode}
              trailing={trailing}
              time={time}
            />
          </button>
        ) : (
          <div
            ref={headRef as unknown as RefObject<HTMLDivElement>}
            role={onOpenWorkspace ? "button" : undefined}
            tabIndex={onOpenWorkspace ? 0 : undefined}
            className="kimi-tool-row-head-main"
            onClick={handleHeadClick}
            onKeyDown={
              onOpenWorkspace
                ? (event: KeyboardEvent<HTMLDivElement>) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    handleHeadClick();
                  }
                }
                : undefined
            }
          >
            <ToolRowHeadContent
              icon={icon}
              name={name}
              arg={arg}
              argNode={argNode}
              status={status}
              chip={chip}
              chipNode={chipNode}
              trailing={trailing}
              time={time}
            />
          </div>
        )}
        {expandable ? (
          <button
            type="button"
            className="kimi-tool-row-toggle"
            aria-expanded={open}
            aria-label={open ? "收起工具详情" : "展开工具详情"}
            onClick={handleToggleClick}
          >
            {open ? (
              <ChevronDownIcon className="size-3.5 shrink-0 text-[var(--color-text-faint)]" />
            ) : (
              <ChevronRightIcon className="size-3.5 shrink-0 text-[var(--color-text-faint)]" />
            )}
          </button>
        ) : null}
      </div>
      <div
        className={cn("kimi-tool-row-body", open && "is-open")}
        inert={!open ? true : undefined}
      >
        <div className="kimi-tool-row-body-pad">{children}</div>
      </div>
    </div>
  );
}

/** 行头内容：icon / 名称 / arg（富节点优先）/ 状态 / chip / 尾部。 */
function ToolRowHeadContent({
  icon,
  name,
  arg,
  argNode,
  status,
  chip,
  chipNode,
  trailing,
  time,
}: {
  icon?: ReactNode;
  name: string;
  arg: string;
  argNode?: ReactNode;
  status: ToolRowProps["status"];
  chip: string;
  chipNode?: ReactNode;
  trailing?: ReactNode;
  time: string;
}) {
  return (
    <>
      {icon ? <span className="kimi-tool-row-glyph">{icon}</span> : null}
      <span className="kimi-tool-row-text">
        <span className="kimi-tool-row-name">{name}</span>
        {argNode ?? (arg ? <span className="kimi-tool-row-arg" title={arg}>{arg}</span> : null)}
      </span>
      <span className="kimi-tool-row-trailing">
        <span
          className={cn(
            "kimi-tool-row-status",
            status === "ok" && "is-ok",
            status === "error" && "is-error"
          )}
          role="status"
          aria-label={status}
        >
          {status === "ok" ? (
            <CheckIcon className="size-3.5" />
          ) : status === "error" ? (
            <XIcon className="size-3.5" />
          ) : (
            <StatusDot status={status} />
          )}
        </span>
        {chipNode ?? (chip ? <span className="kimi-tool-row-chip">{chip}</span> : null)}
        {trailing}
        {time ? <span className="kimi-tool-row-time">{time}</span> : null}
      </span>
    </>
  );
}
