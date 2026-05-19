import { useState } from "react";
import { motion } from "motion/react";
import { ArrowRight } from "lucide-react";

type VisitorLoginGateProps = {
  loading?: boolean;
  onSubmit: (username: string) => void;
};

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.08,
      delayChildren: 0.1,
    },
  },
};

const itemVariants = {
  hidden: {
    opacity: 0,
    y: 20
  },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.5,
      ease: [0.16, 1, 0.3, 1] as const,
    },
  },
};

/**
 * 访客登录入口 — Editorial Portal 风格
 * 温暖智慧的初次相遇，拒绝冰冷的表单感
 */
export default function VisitorLoginGate(props: VisitorLoginGateProps) {
  const [username, setUsername] = useState("");
  const [isFocused, setIsFocused] = useState(false);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && username.trim().length > 0) {
      props.onSubmit(username.trim());
    }
  };

  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden">
      {/* 背景层 */}
      <div className="absolute inset-0 bg-[var(--page-gradient)]" />

      {/* 装饰光斑 — 增加空间层次 */}
      <div
        className="pointer-events-none absolute -right-32 top-1/4 h-[500px] w-[500px] rounded-full opacity-60"
        style={{
          background: "radial-gradient(circle, oklch(0.7 0.05 260 / 0.06), transparent 70%)",
          filter: "blur(60px)",
        }}
      />
      <div
        className="pointer-events-none absolute -left-24 bottom-1/4 h-[400px] w-[400px] rounded-full opacity-50"
        style={{
          background: "radial-gradient(circle, oklch(0.65 0.04 200 / 0.05), transparent 70%)",
          filter: "blur(50px)",
        }}
      />

      {/* 内容 */}
      <motion.div
        className="relative z-10 w-full max-w-[460px] px-6"
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        {/* 品牌区 */}
        <motion.div className="mb-10 text-center" variants={itemVariants}>
          {/* 主标题 */}
          <h1
            className="mb-3 text-[38px] leading-[1.1] tracking-tight text-[var(--chat-text)] md:text-[44px]"
            style={{ fontFamily: "var(--font-display)" }}
          >
            你好，探索者
          </h1>

          {/* 副标题 */}
          <p className="mx-auto max-w-[320px] text-[15px] leading-relaxed text-[var(--chat-text-soft)]">
            输入一个名字，开启与 AI 的协作之旅
          </p>
        </motion.div>

        {/* 表单区 */}
        <motion.div className="space-y-4" variants={itemVariants}>
          {/* 输入框 */}
          <div className="relative">
            <input
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              onKeyDown={handleKeyDown}
              onFocus={() => setIsFocused(true)}
              onBlur={() => setIsFocused(false)}
              placeholder="你的名字"
              disabled={props.loading}
              className="h-14 w-full rounded-2xl border bg-white/70 px-5 text-[16px] text-[var(--chat-text)] outline-none transition-all duration-300 placeholder:text-[var(--chat-text-muted)] disabled:opacity-60"
              style={{
                borderColor: isFocused
                  ? "oklch(0.55 0.18 260 / 0.35)"
                  : "var(--chat-border)",
                boxShadow: isFocused
                  ? "0 0 0 3px oklch(0.55 0.18 260 / 0.12), 0 4px 20px oklch(0.55 0.18 260 / 0.08)"
                  : "0 2px 8px oklch(0 0 0 / 0.03)",
                backdropFilter: "blur(12px)",
              }}
            />
          </div>

          {/* 提交按钮 */}
          <motion.button
            type="button"
            disabled={props.loading === true || username.trim().length === 0}
            onClick={() => props.onSubmit(username.trim())}
            whileHover={
              props.loading || username.trim().length === 0
                ? {}
                : {
                  y: -1,
                  boxShadow: "0 8px 28px oklch(0.25 0.02 60 / 0.18)"
                }
            }
            whileTap={
              props.loading || username.trim().length === 0
                ? {}
                : { scale: 0.98 }
            }
            className="flex h-[52px] w-full items-center justify-center gap-2 rounded-2xl text-[15px] font-medium text-white transition-colors duration-300 disabled:cursor-not-allowed disabled:opacity-50"
            style={{background: "var(--chat-text)",}}
          >
            {props.loading === true ? (
              <>
                <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                <span>准备中...</span>
              </>
            ) : (
              <>
                <span>进入工作台</span>
                <ArrowRight className="h-4 w-4" />
              </>
            )}
          </motion.button>
        </motion.div>

      </motion.div>
    </div>
  );
}
