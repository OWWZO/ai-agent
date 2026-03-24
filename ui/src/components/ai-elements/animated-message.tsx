import React, { memo } from "react";
import { motion, AnimatePresence } from "motion/react";

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

  return (
    <div className={className}>
      <AnimatePresence mode="popLayout">
        {childrenArray.map((child, index) => (
          <motion.div
            key={index}
            initial={{ opacity: 0, y: 20, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -10, scale: 0.98 }}
            transition={{
              duration: 0.4,
              delay: index === childrenArray.length - 1 ? 0 : 0, // 只给最新消息添加延迟
              ease: [0.25, 0.46, 0.45, 0.94],
            }}
            layout
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
    return (
      <motion.div
        initial={isNew ? { opacity: 0, y: 15, scale: 0.99 } : false}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{
          duration: 0.35,
          delay,
          ease: [0.25, 0.46, 0.45, 0.94],
        }}
        layout
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
  return (
    <motion.div
      animate={
        isStreaming
          ? {
              opacity: [1, 0.95, 1],
            }
          : { opacity: 1 }
      }
      transition={
        isStreaming
          ? {
              duration: 2,
              repeat: Infinity,
              ease: "easeInOut",
            }
          : { duration: 0.3 }
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
  duration = 0.4,
  className,
  direction = "up",
}) => {
  const directionOffset = {
    up: { y: 20 },
    down: { y: -20 },
    left: { x: 20 },
    right: { x: -20 },
    none: {},
  };

  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, ...directionOffset[direction] }}
      animate={{ opacity: 1, x: 0, y: 0 }}
      transition={{
        duration,
        delay,
        ease: [0.25, 0.46, 0.45, 0.94],
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
  return (
    <motion.div
      animate={
        isActive
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
  return (
    <motion.div
      className={className}
      initial="hidden"
      animate="visible"
      variants={{
        hidden: { opacity: 0 },
        visible: {
          opacity: 1,
          transition: {
            staggerChildren: staggerDelay,
          },
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
  return (
    <motion.div
      className={className}
      variants={{
        hidden: { opacity: 0, y: 10 },
        visible: {
          opacity: 1,
          y: 0,
          transition: {
            duration: 0.3,
            ease: [0.25, 0.46, 0.45, 0.94],
          },
        },
      }}
    >
      {children}
    </motion.div>
  );
};

export default AnimatedMessageList;
