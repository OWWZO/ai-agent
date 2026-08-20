import { useEffect, useRef } from "react";
import { useStreamingText } from "@/hooks/useStreamingText";
import { cn } from "@/lib/utils";

type ToolArgStreamPreviewProps = {
  /** 后端累计入参（argumentsText），可能尚未形成合法 JSON */
  text: string;
  label?: string;
  streaming?: boolean;
  className?: string;
  /** 像终答一样占满可见区，不塞在折叠卡深处 */
  prominent?: boolean;
};

/**
 * 工具入参流式展示：与终答同款 useStreamingText 逐字追赶 + 闪烁光标。
 */
export function ToolArgStreamPreview({
  text,
  label = "Arguments",
  streaming = true,
  className,
  prominent = true,
}: ToolArgStreamPreviewProps) {
  const scrollerRef = useRef<HTMLPreElement | null>(null);
  const displayed = useStreamingText(text || "", streaming);

  useEffect(() => {
    if (!streaming) {
      return;
    }
    const el = scrollerRef.current;
    if (!el) {
      return;
    }
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distance < 160) {
      el.scrollTop = el.scrollHeight;
    }
  }, [displayed, streaming]);

  if (!displayed && !text && !streaming) {
    return null;
  }

  return (
    <div
      className={cn(
        "kimi-tool-stream-preview",
        prominent && "is-prominent",
        className
      )}
      data-testid="tool-arg-stream-preview"
    >
      {label ? (
        <div className="kimi-tool-stream-preview-label">
          {label}
          {streaming ? (
            <span className="kimi-tool-stream-preview-pulse" aria-hidden>
              ●
            </span>
          ) : null}
        </div>
      ) : null}
      <pre
        ref={scrollerRef}
        className="kimi-tool-stream-preview-body"
        aria-live="polite"
      >
        {displayed || (streaming ? "" : text)}
        {streaming ? (
          <span className="kimi-tool-stream-preview-cursor" aria-hidden>
            ▍
          </span>
        ) : null}
      </pre>
    </div>
  );
}
