import { useEffect, useRef, useState } from "react";

/**
 * 与终答 MessageResponse 同款：把后端累计文本缓成前端逐字/小步追赶，
 * 避免 SSE 节流导致「整段蹦字」。
 */
export function useStreamingText(text: string, isStreaming: boolean): string {
  const [displayedText, setDisplayedText] = useState(text);
  const displayedRef = useRef(text);
  const targetRef = useRef(text);
  const frameRef = useRef<number | null>(null);
  const lastUpdateRef = useRef(0);
  const lastTargetChangeRef = useRef(0);

  useEffect(() => {
    targetRef.current = text;
    lastTargetChangeRef.current = performance.now();

    if (!isStreaming) {
      displayedRef.current = text;
      setDisplayedText(text);
      if (frameRef.current) {
        cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
      return;
    }

    if (displayedRef.current.length > text.length) {
      displayedRef.current = text;
      setDisplayedText(text);
    }

    // 前缀不一致（重连/换卡）时直接对齐，避免乱序拼接
    if (!text.startsWith(displayedRef.current) && displayedRef.current.length > 0) {
      const common = commonPrefixLength(displayedRef.current, text);
      displayedRef.current = text.slice(0, common);
      setDisplayedText(displayedRef.current);
    }

    const tick = (timestamp: number) => {
      const frameInterval = 28;
      if (timestamp - lastUpdateRef.current < frameInterval) {
        frameRef.current = requestAnimationFrame(tick);
        return;
      }
      lastUpdateRef.current = timestamp;

      const currentValue = displayedRef.current;
      const targetValue = targetRef.current;

      if (currentValue === targetValue) {
        frameRef.current = null;
        return;
      }

      if (!targetValue.startsWith(currentValue)) {
        const common = commonPrefixLength(currentValue, targetValue);
        displayedRef.current = targetValue.slice(0, Math.max(common, 0));
        setDisplayedText(displayedRef.current);
        frameRef.current = requestAnimationFrame(tick);
        return;
      }

      const remaining = targetValue.length - currentValue.length;
      const keepBuffer =
        remaining > 6 && timestamp - lastTargetChangeRef.current < 180
          ? Math.min(24, Math.max(4, Math.floor(remaining * 0.35)))
          : 0;
      const readyChars = Math.max(1, remaining - keepBuffer);
      // 入参/终答同节奏：小步追赶，积压多时略加速
      const chunkSize =
        readyChars > 48 ? 4 : readyChars > 24 ? 3 : readyChars > 8 ? 2 : 1;
      const nextValue = targetValue.slice(0, currentValue.length + chunkSize);

      displayedRef.current = nextValue;
      setDisplayedText(nextValue);
      frameRef.current = requestAnimationFrame(tick);
    };

    if (!frameRef.current) {
      frameRef.current = requestAnimationFrame(tick);
    }

    return () => {
      if (frameRef.current) {
        cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
    };
  }, [isStreaming, text]);

  return displayedText;
}

function commonPrefixLength(a: string, b: string): number {
  const n = Math.min(a.length, b.length);
  let i = 0;
  while (i < n && a.charCodeAt(i) === b.charCodeAt(i)) {
    i += 1;
  }
  return i;
}
