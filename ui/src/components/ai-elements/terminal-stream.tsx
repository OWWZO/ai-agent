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
 * 轻量终端式流式文本：按固定速度逐字显示，并在流式期间显示光标。
 * 流式阶段不重复跑 Markdown 解析，完成后一次性接管完整文本。
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
  // 文本事件持续到达时只更新 ref，动画 effect 无需因每个 chunk 重启。
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
      // 结束时立即补齐完整文本，避免动画速度导致最终内容缺字。
      stop();
      displayedRef.current = text;
      setDisplayed(text);
      prevIsStreamingRef.current = isStreaming;
      return;
    }

    // 只有从非流式切入流式时清空；同一轮的新 chunk 应从已展示长度继续播放。
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

      // 每帧最多追加本轮目标长度，文本增长后下一帧会继续追赶最新目标。
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
