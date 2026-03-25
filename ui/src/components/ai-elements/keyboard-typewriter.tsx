"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { cn } from "@/lib/utils";

export type KeyboardTypewriterProps = {
  /** Single sentence (fallback when `texts` not provided). */
  text?: string;
  /**
   * Sentence sequence to type/erase.
   * When provided, component will type the current sentence, hold, then erase it, then move to next.
   */
  texts?: string[];
  /** typing ms per char */
  speed?: number;
  /** erasing ms per char */
  eraseSpeed?: number;
  /** hold time after typing finished (ms) */
  holdMs?: number;
  /** pause time after erasing finished before next typing (ms) */
  pauseMs?: number;
  /** loop the sentence sequence */
  loop?: boolean;
  className?: string;
  /**
   * Caret color class (Tailwind). Should be compatible with the surrounding text theme.
   * Example: "bg-[var(--chat-text)]/60"
   */
  caretClassName?: string;
  caretWidthClassName?: string;
  caretHeightClassName?: string;
  /**
   * CSS animation class while typing (Tailwind animate-pulse exists by default).
   * Example: "animate-pulse"
   */
  typingCaretAnimationClassName?: string;
  /**
   * CSS animation class when done (we will define `animate-blink` in global.css).
   * Example: "animate-blink"
   */
  doneCaretAnimationClassName?: string;
};

/**
 * Keyboard-like typewriter effect, ported from `front2` hero typing/caret style:
 * - invisible full text reserves layout
 * - absolute overlay reveals characters one-by-one
 * - caret: pulse while typing, blink when completed
 */
export function KeyboardTypewriter({
  speed = 80,
  eraseSpeed = 45,
  holdMs = 900,
  pauseMs = 550,
  loop = true,
  text,
  texts,
  className,
  caretClassName = "bg-[var(--chat-text)]/60",
  caretWidthClassName = "w-[3px]",
  caretHeightClassName = "h-[0.9em]",
  typingCaretAnimationClassName = "animate-pulse",
  doneCaretAnimationClassName = "animate-blink",
}: KeyboardTypewriterProps) {
  const [displayed, setDisplayed] = useState("");
  const [isTypingDone, setIsTypingDone] = useState(false);

  const items = useMemo(() => {
    return (texts && texts.length ? texts : text != null ? [text] : []).filter(Boolean);
  }, [texts, text]);

  const reserveText = useMemo(() => {
    if (!items.length) return "";
    return items.reduce((max, s) => (s.length > max.length ? s : max), items[0] || "");
  }, [items]);

  const itemIndexRef = useRef(0);
  const charIndexRef = useRef(0);
  const phaseRef = useRef<"typing" | "holding" | "erasing" | "pause">("typing");
  const timeoutRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (!items.length) return;

    itemIndexRef.current = 0;
    charIndexRef.current = 0;
    phaseRef.current = "typing";
    setDisplayed("");
    setIsTypingDone(false);

    const clearTimer = () => {
      if (timeoutRef.current != null) {
        window.clearTimeout(timeoutRef.current);
        timeoutRef.current = undefined;
      }
    };

    const step = () => {
      const curText = items[itemIndexRef.current] || "";

      if (phaseRef.current === "typing") {
        setIsTypingDone(false);
        if (charIndexRef.current < curText.length) {
          charIndexRef.current += 1;
          setDisplayed(curText.slice(0, charIndexRef.current));
          timeoutRef.current = window.setTimeout(step, speed);
          return;
        }

        phaseRef.current = "holding";
        setIsTypingDone(true);
        timeoutRef.current = window.setTimeout(step, holdMs);
        return;
      }

      if (phaseRef.current === "holding") {
        phaseRef.current = "erasing";
        setIsTypingDone(false);
        timeoutRef.current = window.setTimeout(step, eraseSpeed);
        return;
      }

      if (phaseRef.current === "erasing") {
        if (charIndexRef.current > 0) {
          charIndexRef.current -= 1;
          setDisplayed(curText.slice(0, charIndexRef.current));
          timeoutRef.current = window.setTimeout(step, eraseSpeed);
          return;
        }

        phaseRef.current = "pause";
        setIsTypingDone(false);

        const isLast = itemIndexRef.current >= items.length - 1;
        if (!loop && isLast) {
          setIsTypingDone(true);
          return;
        }

        itemIndexRef.current = (itemIndexRef.current + 1) % items.length;
        charIndexRef.current = 0;
        setDisplayed("");
        phaseRef.current = "typing";
        timeoutRef.current = window.setTimeout(step, pauseMs);
        return;
      }
    };

    timeoutRef.current = window.setTimeout(step, speed);

    return () => {
      clearTimer();
    };
  }, [items, speed, eraseSpeed, holdMs, pauseMs, loop]);

  return (
    <span className={cn("relative inline-block whitespace-nowrap", className)}>
      {/* Screen readers get the full text immediately. */}
      <span className="sr-only">{items[0] || ""}</span>

      {/* Reserve layout to prevent width jumps while typing. */}
      <span
        className="invisible whitespace-nowrap"
        aria-hidden="true"
      >
        {reserveText}
      </span>

      {/* Overlay typing layer */}
      <span className="absolute inset-0">
        <span className="inline-flex items-baseline">
          <span aria-hidden="true">{displayed}</span>
          <span
            aria-hidden="true"
            className={cn(
              "inline-block ml-1 rounded-[1px]",
              caretWidthClassName,
              caretHeightClassName,
              caretClassName,
              isTypingDone ? doneCaretAnimationClassName : typingCaretAnimationClassName
            )}
          />
        </span>
      </span>
    </span>
  );
}

