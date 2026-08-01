import React, { memo } from "react";
import { motion, AnimatePresence } from "motion/react";
import { DURATION, EASE_OUT, useMotionConfig } from "@/lib/motion";

interface AnimatedMessageListProps {
  children: React.ReactNode;
  className?: string;
}

/**
 * 消息列表动画容器
 * 为新消息添加平滑的进入动画
 */
export const AnimatedMessageList: React.FC<AnimatedMessageListProps> = ({
  children,
  className,
}) => {
  const childrenArray = React.Children.toArray(children);
  const { reduce, duration, ease, fade } = useMotionConfig();

  return (
    <div className={className}>
      <AnimatePresence mode="popLayout">
        {childrenArray.map((child, index) => (
          <motion.div
            key={index}
            initial={fade.initial}
            animate={fade.animate}
            exit={reduce ? { opacity: 0 } : fade.exit}
            transition={{
              duration,
              ease,
            }}
          >
            {child}
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
};

/**
 * 单个消息的动画包装器
 */
interface AnimatedMessageProps {
  children: React.ReactNode;
  isNew?: boolean;
  delay?: number;
}

export const AnimatedMessage = memo(
  ({ children, isNew = false, delay = 0 }: AnimatedMessageProps) => {
    const { reduce, duration, ease } = useMotionConfig();

    return (
      <motion.div
        initial={
          isNew
            ? reduce
              ? { opacity: 0 }
              : {
                opacity: 0,
                y: 8
              }
            : false
        }
        animate={{
          opacity: 1,
          y: 0
        }}
        transition={{
          duration,
          delay: reduce ? 0 : delay,
          ease,
        }}
      >
        {children}
      </motion.div>
    );
  }
);

AnimatedMessage.displayName = "AnimatedMessage";

/**
 * 流式内容更新动画
 * 用于思考过程、工具调用等内容的平滑更新
 */
interface StreamingContentProps {
  children: React.ReactNode;
  isStreaming?: boolean;
}

export const StreamingContent: React.FC<StreamingContentProps> = ({
  children,
  isStreaming = false,
}) => {
  const { reduce } = useMotionConfig();

  return (
    <motion.div
      animate={
        isStreaming && !reduce
          ? {opacity: [1, 0.95, 1],}
          : { opacity: 1 }
      }
      transition={
        isStreaming && !reduce
          ? {
            duration: 2,
            repeat: Infinity,
            ease: "easeInOut",
          }
          : { duration: DURATION.reduced }
      }
    >
      {children}
    </motion.div>
  );
};

/**
 * 渐入动画容器
 */
interface FadeInProps {
  children: React.ReactNode;
  delay?: number;
  duration?: number;
  className?: string;
  direction?: "up" | "down" | "left" | "right" | "none";
}

export const FadeIn: React.FC<FadeInProps> = ({
  children,
  delay = 0,
  duration = DURATION.panel,
  className,
  direction = "up",
}) => {
  const { reduce } = useMotionConfig();
  const directionOffset = {
    up: { y: 12 },
    down: { y: -12 },
    left: { x: 12 },
    right: { x: -12 },
    none: {},
  };

  return (
    <motion.div
      className={className}
      initial={
        reduce
          ? { opacity: 0 }
          : {
            opacity: 0,
            ...directionOffset[direction]
          }
      }
      animate={{
        opacity: 1,
        x: 0,
        y: 0
      }}
      transition={{
        duration: reduce ? DURATION.reduced : Math.min(duration, 0.28),
        delay: reduce ? 0 : delay,
        ease: EASE_OUT,
      }}
    >
      {children}
    </motion.div>
  );
};

/**
 * 脉冲动画（用于加载状态）
 */
interface PulseProps {
  children: React.ReactNode;
  isActive?: boolean;
}

export const Pulse: React.FC<PulseProps> = ({ children, isActive = true }) => {
  const { reduce } = useMotionConfig();

  return (
    <motion.div
      animate={
        isActive && !reduce
          ? {
            scale: [1, 1.02, 1],
            opacity: [0.9, 1, 0.9],
          }
          : {}
      }
      transition={{
        duration: 2,
        repeat: Infinity,
        ease: "easeInOut",
      }}
    >
      {children}
    </motion.div>
  );
};

/**
 * 交错动画容器
 */
interface StaggerContainerProps {
  children: React.ReactNode;
  className?: string;
  staggerDelay?: number;
}

export const StaggerContainer: React.FC<StaggerContainerProps> = ({
  children,
  className,
  staggerDelay = 0.05,
}) => {
  const { reduce } = useMotionConfig();

  return (
    <motion.div
      className={className}
      initial="hidden"
      animate="visible"
      variants={{
        hidden: { opacity: 0 },
        visible: {
          opacity: 1,
          transition: {staggerChildren: reduce ? 0 : staggerDelay,},
        },
      }}
    >
      {children}
    </motion.div>
  );
};

/**
 * 交错动画子项
 */
interface StaggerItemProps {
  children: React.ReactNode;
  className?: string;
}

export const StaggerItem: React.FC<StaggerItemProps> = ({ children, className }) => {
  const { reduce } = useMotionConfig();

  return (
    <motion.div
      className={className}
      variants={{
        hidden: reduce ? { opacity: 0 } : {
          opacity: 0,
          y: 8
        },
        visible: {
          opacity: 1,
          y: 0,
          transition: {
            duration: reduce ? DURATION.reduced : 0.22,
            ease: EASE_OUT,
          },
        },
      }}
    >
      {children}
    </motion.div>
  );
};

export default AnimatedMessageList;
