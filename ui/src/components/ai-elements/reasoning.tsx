"use client";

import { useControllableState } from "@radix-ui/react-use-controllable-state";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { cn } from "@/lib/utils";
import { BrainIcon, ChevronDownIcon, SparklesIcon } from "lucide-react";
import type { ComponentProps, ReactNode } from "react";
import { createContext, memo, useContext, useEffect, useState, useRef } from "react";
import { Streamdown } from "streamdown";
import { motion, AnimatePresence } from "framer-motion";

type ReasoningContextValue = {
  isStreaming: boolean;
  isOpen: boolean;
  setIsOpen: (open: boolean) => void;
  duration: number | undefined;
};

const ReasoningContext = createContext<ReasoningContextValue | null>(null);

export const useReasoning = () => {
  const context = useContext(ReasoningContext);
  if (!context) {
    throw new Error("Reasoning components must be used within Reasoning");
  }
  return context;
};

export type ReasoningProps = ComponentProps<typeof Collapsible> & {
  isStreaming?: boolean;
  open?: boolean;
  defaultOpen?: boolean;
  onOpenChange?: (open: boolean) => void;
  duration?: number;
};

const AUTO_CLOSE_DELAY = 1000;
const MS_IN_S = 1000;

export const Reasoning = memo(
  ({
    className,
    isStreaming = false,
    open,
    defaultOpen = true,
    onOpenChange,
    duration: durationProp,
    children,
    ...props
  }: ReasoningProps) => {
    const [isOpen, setIsOpen] = useControllableState({
      prop: open,
      defaultProp: defaultOpen,
      onChange: onOpenChange,
    });
    const [duration, setDuration] = useControllableState({
      prop: durationProp,
      defaultProp: undefined,
    });

    const [hasAutoClosed, setHasAutoClosed] = useState(false);
    const [startTime, setStartTime] = useState<number | null>(null);

    // Track duration when streaming starts and ends
    useEffect(() => {
      if (isStreaming) {
        if (startTime === null) {
          setStartTime(Date.now());
        }
      } else if (startTime !== null) {
        setDuration(Math.ceil((Date.now() - startTime) / MS_IN_S));
        setStartTime(null);
      }
    }, [isStreaming, startTime, setDuration]);

    // Auto-open when streaming starts, auto-close when streaming ends (once only)
    useEffect(() => {
      if (defaultOpen && !isStreaming && isOpen && !hasAutoClosed) {
        const timer = setTimeout(() => {
          setIsOpen(false);
          setHasAutoClosed(true);
        }, AUTO_CLOSE_DELAY);

        return () => clearTimeout(timer);
      }
    }, [isStreaming, isOpen, defaultOpen, setIsOpen, hasAutoClosed]);

    const handleOpenChange = (newOpen: boolean) => {
      setIsOpen(newOpen);
    };

    return (
      <ReasoningContext.Provider
        value={{ isStreaming, isOpen, setIsOpen, duration }}
      >
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: [0.25, 0.46, 0.45, 0.94] }}
        >
          <Collapsible
            className={cn("not-prose mb-4", className)}
            onOpenChange={handleOpenChange}
            open={isOpen}
            {...props}
          >
            {children}
          </Collapsible>
        </motion.div>
      </ReasoningContext.Provider>
    );
  }
);

export type ReasoningTriggerProps = ComponentProps<typeof CollapsibleTrigger> & {
  getThinkingMessage?: (isStreaming: boolean, duration?: number) => ReactNode;
};

// 动态思考指示器组件
const ThinkingIndicator = memo(() => {
  return (
    <motion.div
      className="flex items-center gap-1.5"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      <span className="text-xs text-muted-foreground">思考中</span>
      <div className="flex gap-0.5">
        {[0, 1, 2].map((i) => (
          <motion.span
            key={i}
            className="w-1 h-1 rounded-full bg-primary"
            animate={{
              scale: [1, 1.3, 1],
              opacity: [0.4, 1, 0.4],
            }}
            transition={{
              duration: 1,
              repeat: Infinity,
              delay: i * 0.15,
              ease: "easeInOut",
            }}
          />
        ))}
      </div>
    </motion.div>
  );
});

ThinkingIndicator.displayName = "ThinkingIndicator";

const defaultGetThinkingMessage = (isStreaming: boolean, duration?: number) => {
  if (isStreaming || duration === 0) {
    return <ThinkingIndicator />;
  }
  if (duration === undefined) {
    return <span className="text-xs text-muted-foreground">思考完成</span>;
  }
  return (
    <motion.span
      className="text-xs text-muted-foreground"
      initial={{ opacity: 0, x: -5 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.3 }}
    >
      已思考 {duration} 秒
    </motion.span>
  );
};

export const ReasoningTrigger = memo(
  ({ className, children, getThinkingMessage = defaultGetThinkingMessage, ...props }: ReasoningTriggerProps) => {
    const { isStreaming, isOpen, duration } = useReasoning();

    return (
      <CollapsibleTrigger
        className={cn(
          "flex w-full items-center gap-2 text-muted-foreground text-sm transition-colors hover:text-foreground rounded-lg p-2 -m-2 hover:bg-muted/50",
          className
        )}
        {...props}
      >
        {children ?? (
          <>
            <motion.div
              animate={isStreaming ? {
                rotate: [0, 10, -10, 0],
                scale: [1, 1.1, 1],
              } : {}}
              transition={{
                duration: 2,
                repeat: Infinity,
                ease: "easeInOut",
              }}
            >
              {isStreaming ? (
                <SparklesIcon className="size-4 text-primary" />
              ) : (
                <BrainIcon className="size-4" />
              )}
            </motion.div>
            <div className="flex-1 text-left">
              {getThinkingMessage(isStreaming, duration)}
            </div>
            <motion.div
              animate={{ rotate: isOpen ? 180 : 0 }}
              transition={{ duration: 0.3, ease: [0.25, 0.46, 0.45, 0.94] }}
            >
              <ChevronDownIcon className="size-4" />
            </motion.div>
          </>
        )}
      </CollapsibleTrigger>
    );
  }
);

// 流式思考内容组件
const StreamingReasoningContent = memo(({ content }: { content: string }) => {
  const [displayContent, setDisplayContent] = useState(content);
  const contentRef = useRef(content);
  const rafRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    const prevContent = contentRef.current;
    const newContent = content;

    if (newContent.length < prevContent.length) {
      setDisplayContent(newContent);
      contentRef.current = newContent;
      return;
    }

    const diff = newContent.slice(prevContent.length);
    if (diff.length === 0) return;

    let charsAdded = 0;
    const addChars = () => {
      if (charsAdded < diff.length) {
        const chunkSize = Math.min(2, diff.length - charsAdded);
        setDisplayContent(() => prevContent + diff.slice(0, charsAdded + chunkSize));
        charsAdded += chunkSize;
        rafRef.current = requestAnimationFrame(addChars);
      } else {
        contentRef.current = newContent;
      }
    };

    rafRef.current = requestAnimationFrame(addChars);

    return () => {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, [content]);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="relative"
    >
      <Streamdown className="ai-chat-markdown size-full [&>*:first-child]:mt-0 [&>*:last-child]:mb-0">
        {displayContent}
      </Streamdown>
    </motion.div>
  );
});

StreamingReasoningContent.displayName = "StreamingReasoningContent";

export type ReasoningContentProps = ComponentProps<
  typeof CollapsibleContent
> & {
  children: string;
};

export const ReasoningContent = memo(
  ({ className, children, ...props }: ReasoningContentProps) => {
    const { isStreaming } = useReasoning();

    return (
      <AnimatePresence>
        <CollapsibleContent
          className={cn(
            "overflow-hidden",
            className
          )}
          {...props}
        >
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            transition={{
              duration: 0.3,
              ease: [0.25, 0.46, 0.45, 0.94],
            }}
            className="mt-4 text-sm text-muted-foreground"
          >
            {isStreaming ? (
              <StreamingReasoningContent content={children} />
            ) : (
              <Streamdown className="ai-chat-markdown size-full [&>*:first-child]:mt-0 [&>*:last-child]:mb-0">
                {children}
              </Streamdown>
            )}
          </motion.div>
        </CollapsibleContent>
      </AnimatePresence>
    );
  }
);

Reasoning.displayName = "Reasoning";
ReasoningTrigger.displayName = "ReasoningTrigger";
ReasoningContent.displayName = "ReasoningContent";
