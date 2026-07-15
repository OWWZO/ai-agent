import type { FC } from "react";

import { AnimatedOrb } from "@/components/chat/AnimatedOrb";

/**
 * 统一复用深度思考/深度研究的初始 Thinking 占位，避免各模式出现不同的等待体感。
 */
const ThinkingMessage: FC = () => (
  <div className="mt-6 flex max-w-[90%] gap-3 md:max-w-[80%]">
    <div className="shrink-0">
      <AnimatedOrb size={32} />
    </div>
    <div
      className="rounded-2xl rounded-bl-md border border-stone-200 bg-white px-4 py-3"
      style={{ boxShadow: "var(--chat-soft-shadow)" }}
      role="status"
      aria-label="Assistant is thinking"
    >
      <div className="flex items-center gap-1">
        <span
          className="h-2 w-2 animate-bounce rounded-full bg-stone-400"
          style={{ animationDelay: "0ms" }}
        />
        <span
          className="h-2 w-2 animate-bounce rounded-full bg-stone-400"
          style={{ animationDelay: "150ms" }}
        />
        <span
          className="h-2 w-2 animate-bounce rounded-full bg-stone-400"
          style={{ animationDelay: "300ms" }}
        />
      </div>
    </div>
  </div>
);

export default ThinkingMessage;
