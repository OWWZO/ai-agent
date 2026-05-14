import { motion } from "motion/react";

/**
 * 访客引导加载界面 — 与登录入口保持一致的视觉语言
 */
export default function VisitorBootstrapScreen() {
  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden">
      {/* 背景层 */}
      <div className="absolute inset-0 bg-[var(--page-gradient)]" />

      {/* 装饰光斑 */}
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
        className="relative z-10 text-center"
        initial={{
          opacity: 0,
          y: 16
        }}
        animate={{
          opacity: 1,
          y: 0
        }}
        transition={{
          duration: 0.5,
          ease: [0.16, 1, 0.3, 1]
        }}
      >
        <h1
          className="mb-2 text-[28px] font-normal leading-[1.15] tracking-tight text-[var(--chat-text)]"
          style={{ fontFamily: "var(--font-display)" }}
        >
          正在进入工作台
        </h1>
        <p className="text-[14px] text-[var(--chat-text-soft)]">
          准备你的 AI 协作环境...
        </p>
      </motion.div>
    </div>
  );
}
