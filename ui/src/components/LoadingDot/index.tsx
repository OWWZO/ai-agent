import React from "react";
import { motion } from "motion/react";

/**
 * 优化的 LoadingDot 组件
 * 使用 framer-motion 实现流畅的波浪动画
 */
export const LoadingDot: React.FC = () => {
  return (
    <div className="flex items-center gap-1.5 p-2">
      {[0, 1, 2].map((index) => (
        <motion.div
          key={index}
          className="w-2 h-2 rounded-full bg-primary"
          animate={{
            scale: [1, 1.3, 1],
            opacity: [0.4, 1, 0.4],
            y: [0, -4, 0],
          }}
          transition={{
            duration: 1.2,
            repeat: Infinity,
            delay: index * 0.15,
            ease: "easeInOut",
          }}
        />
      ))}
    </div>
  );
};

/**
 * 脉冲波纹加载指示器
 */
export const LoadingPulse: React.FC<{ className?: string }> = ({ className }) => {
  return (
    <div className={`relative flex items-center justify-center ${className}`}>
      <motion.div
        className="absolute w-full h-full rounded-full bg-primary/30"
        animate={{
          scale: [1, 1.5, 1.5],
          opacity: [0.5, 0, 0],
        }}
        transition={{
          duration: 1.5,
          repeat: Infinity,
          ease: "easeOut",
        }}
      />
      <motion.div
        className="w-3 h-3 rounded-full bg-primary"
        animate={{
          scale: [1, 1.1, 1],
        }}
        transition={{
          duration: 1,
          repeat: Infinity,
          ease: "easeInOut",
        }}
      />
    </div>
  );
};

/**
 * 轨道旋转加载指示器
 */
export const LoadingOrbit: React.FC<{ className?: string }> = ({ className }) => {
  return (
    <div className={`relative ${className}`}>
      <motion.div
        className="w-full h-full"
        animate={{ rotate: 360 }}
        transition={{
          duration: 2,
          repeat: Infinity,
          ease: "linear",
        }}
      >
        <div className="absolute inset-0 rounded-full border-2 border-primary/20" />
        <motion.div
          className="absolute w-2 h-2 rounded-full bg-primary"
          style={{ top: 0, left: "50%", marginLeft: -4 }}
        />
      </motion.div>
    </div>
  );
};

/**
 * 波浪加载指示器
 */
export const LoadingWave: React.FC<{ className?: string }> = ({ className }) => {
  return (
    <div className={`flex items-center gap-0.5 h-5 ${className}`}>
      {[0, 1, 2, 3, 4].map((index) => (
        <motion.div
          key={index}
          className="w-1 bg-primary rounded-full"
          animate={{
            height: [8, 20, 8],
            opacity: [0.4, 1, 0.4],
          }}
          transition={{
            duration: 0.8,
            repeat: Infinity,
            delay: index * 0.1,
            ease: "easeInOut",
          }}
        />
      ))}
    </div>
  );
};

/**
 * 渐变旋转加载指示器
 */
export const LoadingGradient: React.FC<{ size?: number; className?: string }> = ({
  size = 24,
  className,
}) => {
  return (
    <motion.div
      className={`relative ${className}`}
      style={{ width: size, height: size }}
      animate={{ rotate: 360 }}
      transition={{
        duration: 1.5,
        repeat: Infinity,
        ease: "linear",
      }}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        className="w-full h-full"
      >
        <defs>
          <linearGradient id="loading-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#0071e3" />
            <stop offset="100%" stopColor="#4040ff" />
          </linearGradient>
        </defs>
        <motion.circle
          cx="12"
          cy="12"
          r="10"
          stroke="url(#loading-gradient)"
          strokeWidth="2"
          strokeLinecap="round"
          strokeDasharray="40 20"
        />
      </svg>
    </motion.div>
  );
};

/**
 * 打字机效果加载指示器
 */
export const LoadingTyping: React.FC<{ className?: string }> = ({ className }) => {
  return (
    <div className={`flex items-center gap-1 ${className}`}>
      <motion.span
        className="w-2 h-2 rounded-full bg-primary"
        animate={{
          scale: [1, 0.5, 1],
          opacity: [1, 0.5, 1],
        }}
        transition={{
          duration: 0.6,
          repeat: Infinity,
          ease: "easeInOut",
        }}
      />
      <motion.span
        className="w-2 h-2 rounded-full bg-primary"
        animate={{
          scale: [1, 0.5, 1],
          opacity: [1, 0.5, 1],
        }}
        transition={{
          duration: 0.6,
          repeat: Infinity,
          delay: 0.2,
          ease: "easeInOut",
        }}
      />
      <motion.span
        className="w-2 h-2 rounded-full bg-primary"
        animate={{
          scale: [1, 0.5, 1],
          opacity: [1, 0.5, 1],
        }}
        transition={{
          duration: 0.6,
          repeat: Infinity,
          delay: 0.4,
          ease: "easeInOut",
        }}
      />
    </div>
  );
};

export default LoadingDot;
