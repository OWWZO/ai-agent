"use client";

import { memo, useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

export type TerminalStreamTextProps = {
  text: string;
  isStreaming: boolean;
  /** ms per char */
  speed?: number;
  className?: string;
  /** caret color class (tailwind) */
  caretClassName?: string;
  caretWidthClassName?: string;
  caretHeightClassName?: string;
};

/**
 * Lightweight terminal-like streaming text:
 * - incrementally reveals characters at a steady rate
 * - shows a soft caret (pulse while streaming)
 * - avoids Streamdown/Markdown re-parsing during streaming for better performance
 */
const TerminalStreamTextComponent = ({
  text,
  isStreaming,
  speed = 10,
  className,
  caretClassName = "bg-[var(--chat-text)]/55",
  caretWidthClassName = "w-[3px]",
  caretHeightClassName = "h-[0.9em]",
}: TerminalStreamTextProps) => {
  const latestTextRef = useRef(text);
  const displayedRef = useRef("");
  const prevIsStreamingRef = useRef(isStreaming);

  const [displayed, setDisplayed] = useState(isStreaming ? text : "");

  useEffect(() => {
    latestTextRef.current = text;
  }, [text]);

  useEffect(() => {
    let rafId: number | null = null;
    let startTs = 0;
    let startLen = 0;

    const stop = () => {
      if (rafId != null) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
    };

    if (!isStreaming) {
      stop();
      displayedRef.current = text;
      setDisplayed(text);
      prevIsStreamingRef.current = isStreaming;
      return;
    }

    // Only reset when we enter streaming mode.
    if (!prevIsStreamingRef.current) {
      displayedRef.current = "";
      setDisplayed("");
      startTs = performance.now();
      startLen = 0;
    } else {
      startTs = performance.now();
      startLen = displayedRef.current.length;
    }

    const tick = (now: number) => {
      const target = latestTextRef.current || "";
      const targetLen = target.length;

      const elapsedMs = now - startTs;
      const desiredLen = Math.min(targetLen, startLen + Math.floor(elapsedMs / Math.max(1, speed)));

      if (desiredLen !== displayedRef.current.length) {
        displayedRef.current = target.slice(0, desiredLen);
        setDisplayed(displayedRef.current);
      }

      if (isStreaming) {
        rafId = requestAnimationFrame(tick);
      }
    };

    prevIsStreamingRef.current = isStreaming;
    rafId = requestAnimationFrame(tick);

    return () => {
      stop();
    };
  }, [isStreaming, speed]);

  return (
    <pre
      className={cn(
        "m-0 w-full whitespace-pre-wrap break-words font-mono text-[13px] leading-6",
        className
      )}
    >
      <code>{displayed}</code>
      {isStreaming ? (
        <span
          aria-hidden="true"
          className={cn(
            "inline-block align-baseline ml-1 rounded-[1px] translate-y-[2px]",
            caretWidthClassName,
            caretHeightClassName,
            caretClassName,
            "animate-pulse"
          )}
        />
      ) : null}
    </pre>
  );
};

export const TerminalStreamText = memo(TerminalStreamTextComponent);

