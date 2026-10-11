import {
  memo,
  useCallback,
  useEffect,
  useRef,
  useState,
  type MouseEventHandler,
  type ReactNode,
} from "react";
import { BrainIcon, ChevronRightIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";

type ThinkingBlockProps = {
  text: string;
  streaming?: boolean;
  foldable?: boolean;
  label?: string;
  /** 已格式化好的时长文本（如 `0.1s`），原样展示。`durationMs` 优先于它。 */
  durationLabel?: string;
  /** 毫秒数：组件内拼成「持续了 X 秒」，与 ZCode 的 `chat.reasoning.durationSeconds` 同义。 */
  durationMs?: number;
  /** 标题行右侧额外控件（如版本切换），点击不触发折叠 */
  headerExtra?: ReactNode;
  className?: string;
};

/**
 * 思考内容收起后不能立刻卸载 children：Radix 的高度动画需要 closed 阶段
 * 仍能读到真实内容高度（对齐 ZCode `REASONING_CONTENT_COLLAPSE_UNMOUNT_DELAY_MS`）。
 */
const COLLAPSE_UNMOUNT_DELAY_MS = 300;

function hasTextSelection(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  return Boolean(window.getSelection()?.toString());
}

/** 收起 + 流式时，标题右侧滚动显示思考的最后一行（图3 的核心观感）。 */
function resolveStreamingSummary(text: string): string | undefined {
  const lines = text.split("\n");
  for (let index = lines.length - 1; index >= 0; index -= 1) {
    const line = lines[index].trim();
    if (line) {
      return line;
    }
  }
  return undefined;
}

/**
 * 时长文案：数字优先（「持续了 5 秒」），其次原样透传已格式化的文本，
 * 都没有且已结束则退回「持续了几秒」——与 ZCode `ReasoningTrigger` 三分支一致。
 */
function resolveDurationText(
  durationMs: number | undefined,
  durationLabel: string | undefined,
  streaming: boolean
): string | undefined {
  if (typeof durationMs === "number" && Number.isFinite(durationMs) && durationMs >= 0) {
    return `持续了 ${Math.max(1, Math.ceil(durationMs / 1000))} 秒`;
  }
  if (durationLabel) {
    return durationLabel;
  }
  if (!streaming) {
    return "持续了几秒";
  }
  return undefined;
}

export const ThinkingBlock = memo(function ThinkingBlock({
  text,
  streaming = false,
  foldable = true,
  label = "思考",
  durationLabel,
  durationMs,
  headerExtra,
  className,
}: ThinkingBlockProps) {
  const bodyRef = useRef<HTMLPreElement | null>(null);
  const scrollFrameRef = useRef<number | null>(null);
  const wasStreamingRef = useRef(streaming);
  /** 用户手动点过折叠后，流式过程中不再被强制拉开 */
  const userToggledRef = useRef(false);
  const isFoldable = foldable && Boolean(text.trim());
  const [open, setOpen] = useState(() => Boolean(streaming));
  const [shouldRenderContent, setShouldRenderContent] = useState(() =>
    Boolean(streaming)
  );

  useEffect(() => {
    if (streaming && !wasStreamingRef.current) {
      userToggledRef.current = false;
      setOpen(true);
    } else if (!streaming && wasStreamingRef.current) {
      userToggledRef.current = false;
      setOpen(false);
    }
    wasStreamingRef.current = streaming;
  }, [streaming]);

  // 收起后延迟卸载：让收起动画先跑完，再拆掉重 DOM。
  useEffect(() => {
    if (open) {
      setShouldRenderContent(true);
      return;
    }
    if (!shouldRenderContent) {
      return;
    }
    const timer = window.setTimeout(
      () => setShouldRenderContent(false),
      COLLAPSE_UNMOUNT_DELAY_MS
    );
    return () => window.clearTimeout(timer);
  }, [open, shouldRenderContent]);

  useEffect(() => {
    const el = bodyRef.current;
    if (!el || !open) return;
    scrollFrameRef.current = requestAnimationFrame(() => {
      scrollFrameRef.current = null;
      const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 48;
      if (atBottom || streaming) {
        el.scrollTop = el.scrollHeight;
      }
    });
    return () => {
      if (scrollFrameRef.current !== null) {
        cancelAnimationFrame(scrollFrameRef.current);
        scrollFrameRef.current = null;
      }
    };
  }, [text, open, streaming]);

  /** Radix 负责 toggle；这里只拦「用户正在选中文本」这一种情况，避免打断复制。 */
  const handleOpenChange = useCallback((next: boolean) => {
    if (hasTextSelection()) {
      return;
    }
    userToggledRef.current = true;
    setOpen(next);
  }, []);

  /**
   * 只做冒泡拦截，**不要** `preventDefault`：
   * Radix 的 Slot 会把我们的 onClick 与它自己的 toggle handler 组合，
   * 部分版本的 `composeEventHandlers` 会检查 `defaultPrevented` 并跳过 toggle。
   * 折叠本身交给 Radix（`onOpenChange`），这里不重复 toggle。
   */
  const handleTriggerClick: MouseEventHandler = (event) => {
    event.stopPropagation();
  };

  if (!text.trim()) {
    return null;
  }

  const durationText = resolveDurationText(durationMs, durationLabel, streaming);
  const streamingSummary =
    !open && streaming ? resolveStreamingSummary(text) : undefined;

  if (!isFoldable) {
    return (
      <div className={cn("kimi-think", className)}>
        <div className="kimi-think-trigger" aria-hidden>
          <BrainIcon className="kimi-think-brain size-4 shrink-0" />
          <span
            className={cn(
              "kimi-think-label",
              streaming && "thinking-shimmer"
            )}
          >
            {label}
          </span>
          {durationText ? (
            <>
              <span className="kimi-think-dot" aria-hidden>
                ·
              </span>
              <span className="kimi-think-duration">{durationText}</span>
            </>
          ) : null}
        </div>
        <div className="kimi-think-indent">
          <pre ref={bodyRef} className="kimi-think-live">
            {text}
          </pre>
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn("kimi-think", className)}
      data-open={open ? "true" : "false"}
      data-streaming={streaming ? "true" : "false"}
    >
      <Collapsible open={open} onOpenChange={handleOpenChange}>
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="kimi-think-trigger group/thinking"
            onClick={handleTriggerClick}
            aria-expanded={open}
            aria-label={open ? "折叠思考" : "展开思考"}
          >
            <BrainIcon className="kimi-think-brain size-4 shrink-0" />
            <span
              className={cn(
                "kimi-think-label",
                streaming && open && "thinking-shimmer"
              )}
            >
              {label}
            </span>
            {durationText ? (
              <>
                <span className="kimi-think-dot" aria-hidden>
                  ·
                </span>
                <span className="kimi-think-duration">{durationText}</span>
              </>
            ) : null}
            {streamingSummary ? (
              <span
                className="kimi-think-summary"
                data-testid="thinking-streaming-summary"
              >
                <span>{streamingSummary}</span>
              </span>
            ) : null}
            {headerExtra ? (
              <span
                className="kimi-think-extra"
                onClick={(event) => event.stopPropagation()}
                onKeyDown={(event) => event.stopPropagation()}
              >
                {headerExtra}
              </span>
            ) : null}
            <ChevronRightIcon
              className={cn(
                "kimi-think-chevron size-4 shrink-0 text-[var(--chat-text-faint)] transition-[transform,opacity] duration-200",
                open
                  ? "rotate-90 opacity-100"
                  : "rotate-0 opacity-0 group-hover/thinking:opacity-100"
              )}
            />
          </button>
        </CollapsibleTrigger>

        <CollapsibleContent forceMount={shouldRenderContent ? true : undefined}>
          {shouldRenderContent ? (
            <div className="kimi-think-indent pt-1">
              <pre
                ref={bodyRef}
                className="kimi-think-live"
                onClick={() => {
                  handleOpenChange(false);
                }}
              >
                {text}
              </pre>
            </div>
          ) : null}
        </CollapsibleContent>
      </Collapsible>
    </div>
  );
});
