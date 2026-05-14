import { motion } from "motion/react";
import classNames from "classnames";

import GeneralInput from "@/components/GeneralInput";
import { AiChatSurface } from "@/components/ai-elements/ai-chat-surface";
import { KeyboardTypewriter } from "@/components/ai-elements/keyboard-typewriter";
import { chatQustions, demoList } from "@/utils/constants";

const HERO_TYPEWRITER_TEXTS = [
  "Let's build",
  "Let's create",
  "Hello! How can I help?",
  "Let's analyze",
  "Let's research",
  "Welcome back!",
  "Awaiting your instructions",
];

const SHOW_FEATURED_CASES = false;

const tagColorMap: Record<string, string> = {
  专业研究: "bg-[var(--secondary)] text-[var(--secondary-foreground)]",
  数据分析: "bg-[oklch(0.95_0.05_200)] text-[oklch(0.5_0.1_200)]",
  竞品调研: "bg-[oklch(0.95_0.05_50)] text-[oklch(0.5_0.12_50)]",
};

type CaseCardProps = {
  title: string;
  description: string;
  tag: string;
  image: string;
  url: string;
  videoUrl: string;
  videoModalOpen: string | undefined;
  onOpenVideo: (url: string) => void;
  onCloseVideo: () => void;
  index: number;
};

function CaseCard(props: CaseCardProps) {
  const {
    title,
    description,
    tag,
    image,
    url,
    videoUrl,
    onOpenVideo,
    index,
  } = props;
  const tagColor =
    tagColorMap[tag] ?? "bg-[var(--muted)] text-[var(--muted-foreground)]";

  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 24,
      }}
      animate={{
        opacity: 1,
        y: 0,
      }}
      transition={{
        duration: 0.7,
        delay: 0.8 + index * 0.1,
        ease: [0.16, 1, 0.3, 1],
      }}
      className="group relative flex w-[280px] shrink-0 cursor-pointer flex-col overflow-hidden rounded-[24px] border border-transparent bg-[var(--card)] shadow-[var(--shadow-sm)] transition-all duration-500 ease-out hover:-translate-y-1 hover:border-[var(--border-strong)] hover:shadow-[var(--shadow-lg)]"
    >
      <div className="relative h-[170px] overflow-hidden">
        <img
          src={image}
          className="h-full w-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
          alt={title}
        />
        <div
          className="absolute inset-0 flex items-center justify-center bg-[var(--foreground)]/0 transition-all duration-300 group-hover:bg-[var(--foreground)]/15"
          onClick={() => onOpenVideo(videoUrl)}
        >
          <div className="flex h-[48px] w-[48px] scale-75 items-center justify-center rounded-full bg-white/95 opacity-0 shadow-lg backdrop-blur-sm transition-all duration-300 group-hover:scale-100 group-hover:opacity-100 hover:scale-105 hover:bg-white">
            <i className="font_family icon-bofang ml-[2px] text-[18px] text-[var(--foreground)]"></i>
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-3 p-5">
        <div className="flex items-start justify-between gap-3">
          <h3 className="line-clamp-1 text-[16px] font-medium leading-tight text-[var(--chat-text)] font-[var(--font-sans)]">
            {title}
          </h3>
          <span
            className={`inline-block shrink-0 rounded-full px-2.5 py-1 text-[11px] font-medium ${tagColor}`}
          >
            {tag}
          </span>
        </div>
        <p className="line-clamp-2 text-[13px] leading-[1.6] text-[var(--chat-text-soft)]">
          {description}
        </p>
        <div
          className="flex cursor-pointer items-center gap-1.5 pt-1 text-[13px] font-medium text-[var(--primary)] transition-colors duration-200 hover:text-[var(--accent)]"
          onClick={() => window.open(url)}
        >
          <span>查看报告</span>
          <i className="font_family icon-xinjianjiantou text-[10px] transition-transform duration-200 group-hover:translate-x-0.5"></i>
        </div>
      </div>
    </motion.div>
  );
}

export default function WelcomeView(props: {
  currentConversation: CHAT.ConversationHistory;
  product: CHAT.Product;
  displayOutput: CHAT.Product;
  currentConversationRole: CHAT.ConversationRole | null;
  fixRoles: CHAT.FixRole[];
  visitorUsername?: string;
  videoModalOpen?: string;
  onSelectionChange: (selection: {
    product: CHAT.Product;
    deepThink: boolean;
  }) => void;
  onRoleSelect: (role: CHAT.FixRole) => void;
  onSend: (inputInfo: CHAT.TInputInfo) => void;
  onSendQuestion: (query: { label: string; type: number }) => void;
  onOpenVideo: (url: string) => void;
  onCloseVideo: () => void;
}) {
  return (
    <div className="h-full w-full px-6 md:px-12 lg:px-16">
      <div className="mx-auto flex h-full w-full max-w-[1000px] flex-col items-center justify-center py-12">
        <div className="mb-10 text-center">
          <h1
            className="mb-3 text-[34px] font-medium leading-[1.05] tracking-normal text-[var(--chat-text)] md:text-[46px] lg:text-[52px]"
            style={{ fontFamily: "var(--font-sans)" }}
          >
            <KeyboardTypewriter
              texts={HERO_TYPEWRITER_TEXTS}
              speed={80}
              eraseSpeed={45}
              holdMs={10000}
              pauseMs={550}
            />
          </h1>
        </div>

        <motion.div
          initial={{
            opacity: 0,
            y: 24,
            scale: 0.98,
          }}
          animate={{
            opacity: 1,
            y: 0,
            scale: 1,
          }}
          transition={{
            duration: 0.8,
            delay: 0.5,
            ease: [0.16, 1, 0.3, 1],
          }}
          className="mb-12 w-full max-w-[920px]"
        >
          <AiChatSurface className="w-full rounded-[32px] bg-[var(--chat-surface)]/90 p-5 shadow-none">
            <GeneralInput
              key={`welcome-input-${props.currentConversation.sessionId}`}
              sessionId={props.currentConversation.sessionId}
              placeholder={props.product.placeholder}
              showBtn={true}
              size="big"
              disabled={false}
              product={props.product}
              deepThink={props.currentConversation.deepThink}
              displayOutput={props.displayOutput}
              chatRole={props.currentConversationRole}
              chatRoles={props.fixRoles}
              showRoleSelector={props.product.type === "chat"}
              send={props.onSend}
              onSelectionChange={props.onSelectionChange}
              onRoleSelect={props.onRoleSelect}
            />
          </AiChatSurface>
        </motion.div>

        <motion.div
          initial={false}
          animate={{
            opacity: props.product.type === "dataAgent" ? 1 : 0,
            y: props.product.type === "dataAgent" ? 0 : -10,
          }}
          transition={{
            duration: 0.3,
            ease: [0.16, 1, 0.3, 1],
          }}
          className={classNames(
            "mx-auto w-full max-w-[800px] overflow-hidden",
            props.product.type === "dataAgent"
              ? "mb-12 max-h-[100px] pointer-events-auto"
              : "mb-0 max-h-0 pointer-events-none"
          )}
        >
          <div className="flex flex-wrap justify-center gap-3">
            {chatQustions.map((item, index) => (
              <div
                key={index}
                className="flex cursor-pointer items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-5 py-2.5 text-[13px] text-[var(--chat-text-soft)] transition-all duration-300 hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)] hover:shadow-[var(--shadow-sm)]"
                onClick={() => props.onSendQuestion(item)}
              >
                {item.type === 2 && (
                  <i className="font_family icon-shendusikao text-[12px] text-[var(--primary)]" />
                )}
                {item.label}
              </div>
            ))}
          </div>
        </motion.div>

        {SHOW_FEATURED_CASES && (
          <div className="mx-auto mt-8 w-full max-w-[1000px] pb-24">
            <motion.div
              initial={{
                opacity: 0,
                y: 20,
              }}
              animate={{
                opacity: 1,
                y: 0,
              }}
              transition={{
                duration: 0.7,
                delay: 0.75,
                ease: [0.16, 1, 0.3, 1],
              }}
              className="mb-10 text-center"
            >
              <h2
                className="mb-3 text-[28px] font-normal tracking-[-0.02em] text-[var(--chat-text)]"
                style={{ fontFamily: "var(--font-display)" }}
              >
                精选案例
              </h2>
              <p
                className="text-[15px] text-[var(--chat-text-soft)]"
                style={{ fontFamily: "var(--font-sans)" }}
              >
                和 Reactor 一起，让效率飞起来
              </p>
            </motion.div>

            <div className="flex flex-wrap justify-center gap-6">
              {demoList.map((demo, index) => (
                <CaseCard
                  key={index}
                  {...demo}
                  index={index}
                  videoModalOpen={props.videoModalOpen}
                  onOpenVideo={props.onOpenVideo}
                  onCloseVideo={props.onCloseVideo}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
