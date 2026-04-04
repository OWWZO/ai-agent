import classNames from "classnames";
import { motion } from "motion/react";

interface LoadingSpinnerProps {
  color?: string;
  className?: string;
  children?: React.ReactNode;
  size?: "sm" | "md" | "lg";
}

const sizeMap = {
  sm: "w-4 h-4",
  md: "w-5 h-5",
  lg: "w-6 h-6",
};

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  className,
  children,
  color = "white",
  size = "md",
}) => {
  return (
    <div className={classNames("flex items-center gap-2", className)}>
      <div className={classNames("relative shrink-0", sizeMap[size])}>
        {/* 外圈旋转 */}
        <div
          className="absolute inset-0 rounded-full animate-spin"
          style={{
            background: `conic-gradient(from 0deg, transparent 0%, #4040ff 50%, transparent 100%)`,
            animationDuration: "1.2s",
          }}
        />
        {/* 内圈背景 */}
        <div
          className="absolute inset-[2px] rounded-full"
          style={{ backgroundColor: color }}
        />
      </div>
      {children}
    </div>
  );
};

/**
 * 渐变旋转加载器
 */
export const GradientSpinner: React.FC<{
  className?: string;
  size?: number;
}> = ({ className, size = 20 }) => {
  return (
    <motion.div
      className={classNames("relative", className)}
      style={{ width: size, height: size }}
      animate={{ rotate: 360 }}
      transition={{
        duration: 1.5,
        repeat: Infinity,
        ease: "linear",
      }}
    >
      <svg viewBox="0 0 24 24" fill="none" className="w-full h-full">
        <defs>
          <linearGradient id="spinner-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#0071e3" stopOpacity="0" />
            <stop offset="50%" stopColor="#0071e3" />
            <stop offset="100%" stopColor="#4040ff" />
          </linearGradient>
        </defs>
        <circle
          cx="12"
          cy="12"
          r="10"
          stroke="url(#spinner-gradient)"
          strokeWidth="2"
          strokeLinecap="round"
          strokeDasharray="30 30"
        />
      </svg>
    </motion.div>
  );
};

/**
 * 脉冲点加载器
 */
export const PulseDots: React.FC<{ className?: string }> = ({ className }) => {
  return (
    <div className={classNames("flex items-center gap-1", className)}>
      {[0, 1, 2].map((i) => (
        <motion.span
          key={i}
          className="w-1.5 h-1.5 rounded-full bg-primary"
          animate={{
            scale: [1, 1.3, 1],
            opacity: [0.5, 1, 0.5],
          }}
          transition={{
            duration: 1,
            repeat: Infinity,
            delay: i * 0.2,
            ease: "easeInOut",
          }}
        />
      ))}
    </div>
  );
};

/**
 * 环形进度加载器
 */
export const RingSpinner: React.FC<{
  className?: string;
  size?: number;
  progress?: number;
}> = ({ className, size = 24, progress }) => {
  const circumference = 2 * Math.PI * ((size - 4) / 2);
  const strokeDashoffset = progress
    ? circumference - (progress / 100) * circumference
    : circumference * 0.25;

  return (
    <div className={classNames("relative", className)} style={{ width: size, height: size }}>
      <motion.svg
        viewBox={`0 0 ${size} ${size}`}
        className="w-full h-full -rotate-90"
        animate={{ rotate: progress ? -90 : 270 }}
        transition={
          progress
            ? { duration: 0.3 }
            : {
                duration: 1.5,
                repeat: Infinity,
                ease: "linear",
              }
        }
      >
        <circle
          cx={size / 2}
          cy={size / 2}
          r={(size - 4) / 2}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={strokeDashoffset}
          className="text-primary"
        />
      </motion.svg>
    </div>
  );
};

export default LoadingSpinner;
