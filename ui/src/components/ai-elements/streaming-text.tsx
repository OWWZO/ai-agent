"use client";

import { motion, AnimatePresence, useSpring, useMotionValue } from "motion/react";
import { Streamdown } from "streamdown";
import { cn } from "@/lib/utils";
import { memo, useEffect, useRef, useState, useMemo } from "react";

export interface StreamingTextProps {
  children: string;
  isStreaming?: boolean;
  className?: string;
  /** 是否启用逐字动画 */
  enableCharAnimation?: boolean;
  /** 是否启用光标 */
  showCursor?: boolean;
  /** 光标样式 */
  cursorStyle?: "block" | "line" | "pulse";
}

/**
 * 优化的流式文本组件
 * 提供平滑的字符级动画和流畅的流式体验
 */
const StreamingTextComponent = ({
  children,
  isStreaming = false,
  className,
  enableCharAnimation = true,
  showCursor = true,
  cursorStyle = "pulse",
}: StreamingTextProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [displayText, setDisplayText] = useState(children);
  const prevTextRef = useRef(children);
  const cursorOpacity = useMotionValue(1);
  const smoothCursorOpacity = useSpring(cursorOpacity, { stiffness: 300, damping: 30 });

  // 优化流式文本更新逻辑
  useEffect(() => {
    if (!isStreaming) {
      setDisplayText(children);
      prevTextRef.current = children;
      return;
    }

    const prevText = prevTextRef.current;
    const newText = children;

    // 如果文本长度减少，直接更新
    if (newText.length < prevText.length) {
      setDisplayText(newText);
      prevTextRef.current = newText;
      return;
    }

    // 计算需要添加的文本
    const diff = newText.slice(prevText.length);

    if (diff.length === 0) return;

    // 使用 RAF 批量更新，提高流畅度
    let charsAdded = 0;
    const batchSize = Math.max(1, Math.min(3, Math.floor(diff.length / 10)));

    const addChars = () => {
      if (charsAdded < diff.length) {
        const end = Math.min(charsAdded + batchSize, diff.length);
        const newDisplayText = prevText + diff.slice(0, end);
        setDisplayText(newDisplayText);
        charsAdded = end;
        requestAnimationFrame(addChars);
      } else {
        prevTextRef.current = newText;
      }
    };

    requestAnimationFrame(addChars);
  }, [children, isStreaming]);

  // 光标闪烁动画
  useEffect(() => {
    if (!isStreaming || !showCursor) return;

    const interval = setInterval(() => {
      cursorOpacity.set(cursorOpacity.get() === 1 ? 0.3 : 1);
    }, 530);

    return () => clearInterval(interval);
  }, [isStreaming, showCursor, cursorOpacity]);

  // 平滑滚动到底部
  useEffect(() => {
    if (!isStreaming || !containerRef.current) return;

    const container = containerRef.current;
    const parent = container.parentElement;
    if (!parent) return;

    // 检查是否在底部附近
    const scrollThreshold = 100;
    const isNearBottom =
      parent.scrollHeight - parent.scrollTop - parent.clientHeight < scrollThreshold;

    if (isNearBottom) {
      parent.scrollTo({
        top: parent.scrollHeight,
        behavior: "smooth",
      });
    }
  }, [displayText, isStreaming]);

  // 光标渲染
  const cursorElement = useMemo(() => {
    if (!showCursor || !isStreaming) return null;

    const cursorClasses = {
      block: "w-2 h-5 bg-primary rounded-sm",
      line: "w-0.5 h-5 bg-primary",
      pulse: "w-2 h-5 bg-primary rounded-sm",
    };

    return (
      <motion.span
        className={cn("inline-block align-middle ml-0.5", cursorClasses[cursorStyle])}
        style={{ opacity: cursorStyle === "pulse" ? smoothCursorOpacity : 1 }}
        animate={cursorStyle === "pulse" ? {} : { opacity: [1, 0.3, 1] }}
        transition={cursorStyle === "pulse" ? {} : { duration: 1, repeat: Infinity }}
      />
    );
  }, [showCursor, isStreaming, cursorStyle, smoothCursorOpacity]);

  return (
    <div ref={containerRef} className="relative">
      <Streamdown
        className={cn(
          "ai-chat-markdown size-full [&>*:first-child]:mt-0 [&>*:last-child]:mb-0",
          className
        )}
      >
        {enableCharAnimation && isStreaming ? displayText : displayText}
      </Streamdown>
      {cursorElement}
    </div>
  );
};

export const StreamingText = memo(StreamingTextComponent);

/**
 * 流式消息包装组件
 * 添加进入动画和布局动画
 */
interface StreamMessageWrapperProps {
  children: React.ReactNode;
  className?: string;
  isNew?: boolean;
}

const StreamMessageWrapperComponent = ({
  children,
  className,
  isNew = false,
}: StreamMessageWrapperProps) => {
  return (
    <motion.div
      className={cn("w-full", className)}
      initial={isNew ? { opacity: 0, y: 20, scale: 0.98 } : false}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{
        duration: 0.4,
        ease: [0.25, 0.46, 0.45, 0.94],
      }}
      layout
      layoutId={isNew ? "new-message" : undefined}
    >
      {children}
    </motion.div>
  );
};

export const StreamMessageWrapper = memo(StreamMessageWrapperComponent);

/**
 * 打字机效果组件
 * 适合短文本的逐字显示
 */
interface TypewriterProps {
  text: string;
  speed?: number;
  className?: string;
  cursorClassName?: string;
  /** Cursor blink duration in seconds. */
  cursorBlinkDuration?: number;
  /** Hide cursor when completed. Default true. */
  hideCursorOnComplete?: boolean;
  onComplete?: () => void;
}

const TypewriterComponent = ({
  text,
  speed = 50,
  className,
  cursorClassName,
  cursorBlinkDuration = 0.72,
  hideCursorOnComplete = true,
  onComplete,
}: TypewriterProps) => {
  const [displayText, setDisplayText] = useState("");
  const [isComplete, setIsComplete] = useState(false);
  const indexRef = useRef(0);

  useEffect(() => {
    indexRef.current = 0;
    setDisplayText("");
    setIsComplete(false);

    const interval = setInterval(() => {
      if (indexRef.current < text.length) {
        setDisplayText(text.slice(0, indexRef.current + 1));
        indexRef.current++;
      } else {
        setIsComplete(true);
        onComplete?.();
        clearInterval(interval);
      }
    }, speed);

    return () => clearInterval(interval);
  }, [text, speed, onComplete]);

  return (
    <span className={className}>
      {displayText}
      {(!isComplete || !hideCursorOnComplete) && (
        <motion.span
          className={cn(
            "inline-block align-middle ml-[0.18em] w-[0.1em] h-[0.85em] rounded-full bg-current/55 blur-[0.2px]",
            cursorClassName
          )}
          animate={{ opacity: [1, 0.25, 1] }}
          transition={{ duration: cursorBlinkDuration, repeat: Infinity, ease: "easeInOut" }}
        />
      )}
    </span>
  );
};

export const Typewriter = memo(TypewriterComponent);

/**
 * 渐显文本组件
 * 适合较长文本的平滑淡入
 */
interface FadeInTextProps {
  children: string;
  chunkSize?: number;
  delay?: number;
  className?: string;
}

const FadeInTextComponent = ({
  children,
  chunkSize = 10,
  delay = 0,
  className,
}: FadeInTextProps) => {
  const [visibleChunks, setVisibleChunks] = useState(0);
  const chunks = useMemo(() => {
    const result: string[] = [];
    for (let i = 0; i < children.length; i += chunkSize) {
      result.push(children.slice(i, i + chunkSize));
    }
    return result;
  }, [children, chunkSize]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      let current = 0;
      const interval = setInterval(() => {
        if (current < chunks.length) {
          setVisibleChunks((prev) => prev + 1);
          current++;
        } else {
          clearInterval(interval);
        }
      }, 30);

      return () => clearInterval(interval);
    }, delay * 1000);

    return () => clearTimeout(timeout);
  }, [chunks.length, delay]);

  return (
    <span className={className}>
      {chunks.slice(0, visibleChunks).map((chunk, index) => (
        <motion.span
          key={index}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.15 }}
        >
          {chunk}
        </motion.span>
      ))}
    </span>
  );
};

export const FadeInText = memo(FadeInTextComponent);

/**
 * 脉冲加载指示器
 * 用于流式输出时的加载状态
 */
interface StreamingPulseProps {
  isActive: boolean;
  className?: string;
}

const StreamingPulseComponent = ({ isActive, className }: StreamingPulseProps) => {
  if (!isActive) return null;

  return (
    <motion.div
      className={cn("flex items-center gap-1.5", className)}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      {[0, 1, 2].map((i) => (
        <motion.span
          key={i}
          className="w-1.5 h-1.5 rounded-full bg-primary"
          animate={{
            scale: [1, 1.2, 1],
            opacity: [0.4, 1, 0.4],
          }}
          transition={{
            duration: 1.2,
            repeat: Infinity,
            delay: i * 0.2,
            ease: "easeInOut",
          }}
        />
      ))}
    </motion.div>
  );
};

export const StreamingPulse = memo(StreamingPulseComponent);

/**
 * 波纹效果容器
 * 为子元素添加波纹进入动画
 */
interface RippleContainerProps {
  children: React.ReactNode;
  className?: string;
  staggerDelay?: number;
}

const RippleContainerComponent = ({
  children,
  className,
  staggerDelay = 0.05,
}: RippleContainerProps) => {
  const childrenArray = Array.isArray(children) ? children : [children];

  return (
    <div className={className}>
      <AnimatePresence mode="popLayout">
        {childrenArray.map((child, index) => (
          <motion.div
            key={index}
            initial={{ opacity: 0, x: -10 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 10 }}
            transition={{
              duration: 0.3,
              delay: index * staggerDelay,
              ease: [0.25, 0.46, 0.45, 0.94],
            }}
          >
            {child}
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
};

export const RippleContainer = memo(RippleContainerComponent);

export default StreamingText;
