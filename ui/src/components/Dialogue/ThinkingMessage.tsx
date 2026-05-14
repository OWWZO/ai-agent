import type { FC } from "react";

import { Message, MessageContent } from "@/components/ai-elements/message";

/**
 * 统一复用深度思考/深度研究的初始 Thinking 占位，避免各模式出现不同的等待体感。
 */
const ThinkingMessage: FC = () => (
  <div className="mt-6 flex w-full justify-start">
    <Message from="assistant" className="w-full max-w-full">
      <MessageContent>
        <div className="flex items-center text-[15px] font-medium text-muted-foreground">
          <span className="thinking-shimmer text-[15px] font-medium tracking-[0.02em]">Thinking</span>
        </div>
      </MessageContent>
    </Message>
  </div>
);

export default ThinkingMessage;
