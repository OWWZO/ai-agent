import {
  memo,
  useEffect,
  useRef,
  useState,
  type MouseEventHandler,
  type ReactNode,
} from "react";
import { ChevronDownIcon } from "lucide-react";
import { cn } from "@/lib/utils";

type ThinkingBlockProps = {
  text: string;
  streaming?: boolean;
  foldable?: boolean;
  label?: string;
  durationLabel?: string;
  /** 标题行右侧额外控件（如版本切换），点击不触发折叠 */
  headerExtra?: ReactNode;
  className?: string;
};

function hasTextSelection(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  return Boolean(window.getSelection()?.toString());
}

export const ThinkingBlock = memo(function ThinkingBlock({
  text,
  streaming = false,
  foldable = true,
  label = "深度思考",
  durationLabel,
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

  const handleToggle: MouseEventHandler = (event) => {
    event.preventDefault();
    event.stopPropagation();
    if (hasTextSelection()) {
      return;
    }
    userToggledRef.current = true;
    setOpen((previous) => !previous);
  };

  if (!text.trim()) {
    return null;
  }

  if (!isFoldable) {
    return (
      <div className={cn("kimi-think", className)}>
        <div className="kimi-think-trigger" aria-hidden>
          <span
            className={cn(
              "kimi-think-label",
              streaming && "thinking-shimmer"
            )}
          >
            {label}
          </span>
          {durationLabel ? (
            <span className="kimi-think-duration">{durationLabel}</span>
          ) : null}
        </div>
        <pre ref={bodyRef} className="kimi-think-live">
          {text}
        </pre>
      </div>
    );
  }

  return (
    <div
      className={cn("kimi-think", className)}
      data-open={open ? "true" : "false"}
      data-streaming={streaming ? "true" : "false"}
    >
      <button
        type="button"
        className="kimi-think-trigger"
        onClick={handleToggle}
        aria-expanded={open}
        aria-label={open ? "折叠思考" : "展开思考"}
      >
        <span
          className={cn("kimi-think-label", streaming && open && "thinking-shimmer")}
        >
          {label}
        </span>
        {durationLabel ? (
          <span className="kimi-think-duration">{durationLabel}</span>
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
        <ChevronDownIcon
          className={cn(
            "kimi-think-chevron ml-auto size-3.5 shrink-0 text-[var(--chat-text-faint)] transition-transform duration-200",
            open && "rotate-180"
          )}
        />
      </button>

      {open ? (
        <pre
          ref={bodyRef}
          className="kimi-think-live mt-1"
          onClick={handleToggle}
        >
          {text}
        </pre>
      ) : null}
    </div>
  );
});
