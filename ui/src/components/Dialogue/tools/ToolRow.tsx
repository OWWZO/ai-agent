import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
  type RefObject,
} from "react";
import { ChevronRightIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { QueuedSummaryContent } from "./QueuedSummaryContent";
import { ToolFailureTip } from "./ToolFailureTip";

export type ToolRowStackPosition = "single" | "first" | "middle" | "last";

export type ToolRowStatus = "running" | "ok" | "error" | "suspended";

/**
 * 失败 / 停止时追加在摘要行尾的状态词。
 *
 * 对齐 ZCode `ToolLayout`：**成功态与运行中都不显示任何状态标记**，
 * 运行中的活跃感来自类别标签的扫光；只有失败才出现一个带虚线下划线的词，
 * 悬停时弹出富提示卡展示报错原文（ZCode `statusTooltip`）。
 * 文案取自 ZCode `chat.toolCall.status.*`。
 */
const STATUS_WORD: Partial<Record<ToolRowStatus, string>> = {
  error: "执行失败",
  suspended: "已停止",
};

/**
 * 收起详情时的延迟卸载窗口（对齐 ZCode `ToolLayout.TOOL_CONTENT_COLLAPSE_UNMOUNT_DELAY_MS`）。
 * 高度动画还在跑的时候先卸载子内容，0fr 目标高度会瞬间落地，
 * 详情区会短暂撑成一块空白。300ms 覆盖完整的高度过渡时长。
 */
const TOOL_CONTENT_COLLAPSE_UNMOUNT_DELAY_MS = 300;

type ToolRowProps = {
  status: ToolRowStatus;
  icon?: ReactNode;
  /** 类别标签（ZCode `kindLabel`）：Read / Edit / MCP … */
  name: string;
  /**
   * 类别标签后的补充说明（ZCode `kindDetail`）：MCP 的 server 名这类。
   * 排在类别标签与主文本之间，用次一档的 `foreground-subtle`，比类别标签略实一点。
   */
  kindDetail?: ReactNode;
  /** 主文本（ZCode `primaryText`）：文件名 / 命令 / 摘要 */
  arg?: string;
  /**
   * 主文本前的分隔符（ZCode `summaryContentSeparator`）：
   * MCP 用 `·` 把 `server` 与工具名隔开，避免两个文本节点直接粘连。
   */
  separator?: string;
  /**
   * arg 的富节点形态（对齐 ZCode `renderFileChip`）：文件名 chip 要能点击打开右侧预览，
   * 纯字符串塞不进 button。给了 argNode 就优先渲染它，arg 字符串退为兜底。
   */
  argNode?: ReactNode;
  /**
   * 次级文本（ZCode `secondaryText`）：目录路径这类补充信息。
   * 与主文本同色（最浅一档），在窄屏可被隐藏，所以放在主文本之后、计数之前。
   */
  secondary?: ReactNode;
  chip?: string;
  /**
   * chip 的富节点形态（对齐 ZCode）：`+A −B` 这类计数需要逐字符翻页动画，
   * 纯字符串塞不进 FlipMetricValue。给了 chipNode 就优先渲染它，chip 字符串退为兜底。
   */
  chipNode?: ReactNode;
  /** 失败 / 停止的提示原文；存在时状态词改挂富提示卡（对齐 ZCode `statusTooltip`） */
  statusTitle?: string;
  /**
   * 摘要文案上滚（对齐 ZCode `animateSummaryContent`）：
   * 流式期间主文本频繁刷新时，旧文案上移淡出、新文案自下方升起，而不是硬切。
   * 展开态不播动画（ZCode `enabled={animateContent && !isExpanded}`）。
   */
  animateSummary?: boolean;
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
  kindDetail,
  arg = "",
  argNode,
  separator,
  secondary,
  chip = "",
  chipNode,
  statusTitle,
  animateSummary = false,
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

  /**
   * 收起时的延迟卸载：高度动画期间 children 必须还在 DOM 里，
   * 否则 grid-template-rows 的 0fr 目标会立刻落地，详情区闪出一块空白。
   */
  const [shouldRenderContent, setShouldRenderContent] = useState(open);
  const contentUnmountTimerRef = useRef<number | null>(null);

  useEffect(() => {
    if (open) {
      if (contentUnmountTimerRef.current !== null) {
        window.clearTimeout(contentUnmountTimerRef.current);
        contentUnmountTimerRef.current = null;
      }
      setShouldRenderContent(true);
      return;
    }

    if (!shouldRenderContent) {
      return;
    }

    contentUnmountTimerRef.current = window.setTimeout(() => {
      setShouldRenderContent(false);
      contentUnmountTimerRef.current = null;
    }, TOOL_CONTENT_COLLAPSE_UNMOUNT_DELAY_MS);

    return () => {
      if (contentUnmountTimerRef.current !== null) {
        window.clearTimeout(contentUnmountTimerRef.current);
        contentUnmountTimerRef.current = null;
      }
    };
  }, [open, shouldRenderContent]);

  useEffect(() => {
    return () => {
      if (contentUnmountTimerRef.current !== null) {
        window.clearTimeout(contentUnmountTimerRef.current);
      }
    };
  }, []);

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

  const statusWord = STATUS_WORD[status];

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
              kindDetail={kindDetail}
              arg={arg}
              argNode={argNode}
              separator={separator}
              secondary={secondary}
              chip={chip}
              chipNode={chipNode}
              statusWord={statusWord}
              statusTitle={statusTitle}
              animateSummary={animateSummary}
              open={open}
              trailing={trailing}
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
              kindDetail={kindDetail}
              arg={arg}
              argNode={argNode}
              separator={separator}
              secondary={secondary}
              chip={chip}
              chipNode={chipNode}
              statusWord={statusWord}
              statusTitle={statusTitle}
              animateSummary={animateSummary}
              open={open}
              trailing={trailing}
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
            {/* 单一右箭头，展开时旋转 90° —— 与 ZCode 的 ChevronRight + rotate-90 一致 */}
            <ChevronRightIcon
              className={cn(
                "size-4 shrink-0 transition-transform duration-200 ease-out",
                open ? "rotate-90" : "rotate-0"
              )}
            />
          </button>
        ) : null}
      </div>
      <div
        className={cn("kimi-tool-row-body", open && "is-open")}
        inert={!open ? true : undefined}
      >
        <div className="kimi-tool-row-body-pad">
          {shouldRenderContent ? children : null}
        </div>
      </div>
    </div>
  );
}

/**
 * 行头内容，顺序与 ZCode `ToolSummaryRow` 一致：
 * 图标 → 类别标签 → 主文本 → 次级文本 → 变更计数 → 状态词 → 尾部动作。
 */
function ToolRowHeadContent({
  icon,
  name,
  kindDetail,
  arg,
  argNode,
  separator,
  secondary,
  chip,
  chipNode,
  statusWord,
  statusTitle,
  animateSummary,
  open,
  trailing,
}: {
  icon?: ReactNode;
  name: string;
  kindDetail?: ReactNode;
  arg: string;
  argNode?: ReactNode;
  separator?: string;
  secondary?: ReactNode;
  chip: string;
  chipNode?: ReactNode;
  statusWord?: string;
  statusTitle?: string;
  animateSummary: boolean;
  open: boolean;
  trailing?: ReactNode;
}) {
  const primaryContent =
    argNode ?? (arg ? <span className="kimi-tool-row-arg" title={arg}>{arg}</span> : null);

  return (
    <>
      {icon ? <span className="kimi-tool-row-glyph">{icon}</span> : null}
      {name ? <span className="kimi-tool-row-name">{name}</span> : null}
      {kindDetail ? (
        <span className="kimi-tool-row-kind-detail">{kindDetail}</span>
      ) : null}
      {separator ? (
        <span className="kimi-tool-row-separator">{separator}</span>
      ) : null}
      {/* 摘要文案上滚：主文本是滚动的部分，次级文本与计数留在快照外（ZCode 同构）。 */}
      <QueuedSummaryContent
        contentKey={arg}
        primaryText={primaryContent}
        enabled={animateSummary && !open}
      />
      {secondary}
      {chipNode ?? (chip ? <span className="kimi-tool-row-chip">{chip}</span> : null)}
      {statusWord ? (
        statusTitle ? (
          <ToolFailureTip label={statusWord} text={statusTitle} />
        ) : (
          <span className="kimi-tool-row-status">{statusWord}</span>
        )
      ) : null}
      {trailing}
    </>
  );
}
