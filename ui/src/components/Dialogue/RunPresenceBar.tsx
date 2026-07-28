import type { FC } from "react";
import { AnimatedOrb } from "@/components/chat/AnimatedOrb";

type RunPresenceBarProps = {
  hint: string;
  compact?: boolean;
};

/**
 * 流式运行时的轻量存在感条：告诉用户 Agent 当前在哪一步，而不是只转圈。
 */
const RunPresenceBar: FC<RunPresenceBarProps> = ({ hint, compact = false }) => {
  const text = hint.trim() || "处理中…";

  if (compact) {
    return (
      <div
        className="mt-4 inline-flex max-w-full items-center gap-2 rounded-full border border-[var(--chat-border)]/50 bg-[var(--chat-surface-soft)]/55 px-3 py-1.5 text-[13px] text-[var(--chat-text-soft)]"
        role="status"
        aria-live="polite"
      >
        <span
          className="size-1.5 shrink-0 rounded-full bg-[var(--chat-accent)] motion-safe:animate-pulse"
          aria-hidden
        />
        <span className="truncate">{text}</span>
      </div>
    );
  }

  return (
    <div
      className="mt-6 flex w-full items-center gap-3"
      role="status"
      aria-live="polite"
    >
      <AnimatedOrb size={28} />
      <div className="min-w-0">
        <div className="thinking-shimmer text-[15px] font-medium tracking-[0.02em] text-[var(--chat-text-soft)]">
          {text}
        </div>
      </div>
    </div>
  );
};

export default RunPresenceBar;
