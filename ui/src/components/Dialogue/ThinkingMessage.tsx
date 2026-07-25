import type { FC } from "react";

import { Message, MessageContent } from "@/components/ai-elements/message";

type ThinkingMessageProps = {
  tip?: string;
};

/**
 * 统一复用深度思考/深度研究的初始 Thinking 占位，避免各模式出现不同的等待体感。
 * tip 用于 Lemon 风格的临时 status 文案（如“正在制定计划...”）。
 */
const ThinkingMessage: FC<ThinkingMessageProps> = ({ tip }) => (
  <div className="mt-6 flex w-full justify-start">
    <Message from="assistant" className="w-full max-w-full">
      <MessageContent>
        <div className="flex items-center gap-2 text-[15px] font-medium text-muted-foreground">
          <span className="thinking-shimmer text-[15px] font-medium tracking-[0.02em]">
            {tip?.trim() ? tip : "Thinking"}
          </span>
        </div>
      </MessageContent>
    </Message>
  </div>
);

export default ThinkingMessage;
