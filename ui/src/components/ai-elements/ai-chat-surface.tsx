import * as React from "react";

import { cn } from "@/lib/utils";

import "./ai-chat-surface.css";

type AiChatSurfaceProps = React.ComponentProps<"section">;

const AI_CHAT_FLOATING_CLASS = "ai-chat-floating";

function AiChatSurface({ className, ...props }: AiChatSurfaceProps) {
  return (
    <section
      className={cn(
        "ai-chat-surface relative isolate flex min-h-0 flex-col overflow-hidden rounded-[28px] bg-background/95 text-foreground shadow-[0_24px_80px_-32px_rgba(15,23,42,0.35)] backdrop-blur-xl supports-[backdrop-filter]:bg-background/80",
        className
      )}
      data-slot="ai-chat-surface"
      {...props}
    />
  );
}

export { AI_CHAT_FLOATING_CLASS, AiChatSurface };
