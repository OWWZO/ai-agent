import classNames from "classnames";
import { motion } from "motion/react";

import FeaturedConversationCard from "@/components/FeaturedConversationCard";
import GeneralInput from "@/components/GeneralInput";
import { AnimatedOrb } from "@/components/chat/AnimatedOrb";
import { KeyboardTypewriter } from "@/components/ai-elements/keyboard-typewriter";
import type { FeaturedConversationCard as FeaturedConversationCardModel } from "@/services/featuredConversation";
import {
  suggestedQuestionsByProductType,
  type SuggestedQuestion,
} from "@/utils/constants";

const HERO_TYPEWRITER_TEXTS = [
  "Let's build",
  "Let's create",
  "Hello! How can I help?",
  "Let's analyze",
  "Let's research",
  "Welcome back!",
  "Awaiting your instructions",
];

export default function WelcomeView(props: {
  currentConversation: CHAT.ConversationHistory;
  product: CHAT.Product;
  displayOutput: CHAT.Product;
  currentConversationRole: CHAT.ConversationRole | null;
  fixRoles: CHAT.FixRole[];
  visitorUsername?: string;
  videoModalOpen?: string;
  featuredCards: FeaturedConversationCardModel[];
  onSelectionChange: (selection: {
    product: CHAT.Product;
    deepThink: boolean;
  }) => void;
  onRoleSelect: (role: CHAT.FixRole) => void;
  onSend: (inputInfo: CHAT.TInputInfo) => void;
  onSendQuestion: (query: SuggestedQuestion) => void;
  onOpenVideo: (url: string) => void;
  onCloseVideo: () => void;
  onOpenFeaturedConversations?: () => void;
  onOpenFeaturedDetail?: (featuredId: string) => void;
}) {
  const suggestedQuestions =
    suggestedQuestionsByProductType[props.product.type] ?? [];
  const hasSuggestedQuestions = suggestedQuestions.length > 0;
  const hasFeaturedCards = props.featuredCards.length > 0;

  return (
    <div className="h-full w-full overflow-y-auto px-6 md:px-12 lg:px-16">
      <div
        className={classNames(
          "mx-auto flex min-h-full w-full max-w-[1280px] flex-col items-center py-8 lg:py-10",
          hasFeaturedCards ? "justify-start" : "justify-center"
        )}
      >
        <div
          className={classNames(
            "flex w-full flex-col items-center",
            // 欢迎态主视觉整体下移，避免标题和输入区过于贴近顶部。
            hasFeaturedCards ? "pt-10 md:pt-12 lg:pt-16" : "pt-12 md:pt-16 lg:pt-20"
          )}
        >
          <div className="mb-8 text-center lg:mb-10">
            <div className="orb-intro mx-auto mb-5 flex justify-center">
              <AnimatedOrb size={88} />
            </div>
            <h1
              className="text-blur-intro mb-3 text-[32px] font-medium leading-[1.08] tracking-normal text-[var(--chat-text)] md:text-[42px] lg:text-[48px]"
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
            initial={false}
            animate={{
              opacity: hasSuggestedQuestions ? 1 : 0,
              y: hasSuggestedQuestions ? 0 : -10,
            }}
            transition={{
              duration: 0.3,
              ease: [0.16, 1, 0.3, 1],
            }}
            className={classNames(
              "mx-auto w-full max-w-[1180px] overflow-visible",
              hasSuggestedQuestions
                ? "mb-8 pointer-events-auto lg:mb-10"
                : "mb-0 max-h-0 pointer-events-none"
            )}
          >
            <div className="flex flex-wrap justify-center gap-3">
              {suggestedQuestions.map((item, index) => (
                <button
                  key={index}
                  type="button"
                  className="flex max-w-full cursor-pointer items-center gap-2 rounded-[16px] bg-[oklch(0.955_0.002_90)] px-5 py-3 text-[14px] font-medium leading-none text-[var(--chat-text)] transition-colors duration-200 hover:bg-[oklch(0.925_0.003_90)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--chat-accent)]/25 md:text-[15px]"
                  onClick={() => props.onSendQuestion(item)}
                >
                  {item.deepThink ? (
                    <i className="font_family icon-shendusikao text-[12px] text-[var(--chat-text)]" />
                  ) : null}
                  {item.label}
                </button>
              ))}
            </div>
          </motion.div>

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
            className="mb-8 w-full max-w-[920px] lg:mb-10"
          >
            <div className="w-full">
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
            </div>
          </motion.div>
        </div>

        {hasFeaturedCards ? (
          <motion.section
            initial={{
              opacity: 0,
              y: 20,
            }}
            animate={{
              opacity: 1,
              y: 0,
            }}
            transition={{
              duration: 0.55,
              delay: 0.15,
              ease: [0.16, 1, 0.3, 1],
            }}
            className="mx-auto mt-4 w-full max-w-[1180px] pb-20"
          >
            <div className="mb-5 flex items-end justify-between gap-4">
              <div>
                <h2 className="text-[22px] font-semibold tracking-tight text-[var(--chat-text)]">
                  精品对话
                </h2>
                <p className="mt-1 text-[13px] text-[var(--chat-text-muted)]">
                  精选公开案例，点击查看完整回放
                </p>
              </div>
              <button
                type="button"
                onClick={() => props.onOpenFeaturedConversations?.()}
                className="inline-flex h-9 items-center gap-1.5 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3.5 text-[13px] font-medium text-[var(--chat-text-soft)] transition hover:text-[var(--chat-text)]"
              >
                <span>查看全部</span>
                <i className="font_family icon-xinjianjiantou text-[10px]" />
              </button>
            </div>

            {/* 精品对话始终走公共只读路由，避免和访客自己的会话状态耦合。 */}
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {props.featuredCards.map((card) => (
                <FeaturedConversationCard
                  key={card.featuredId}
                  card={card}
                  variant="grid"
                  onSelect={props.onOpenFeaturedDetail}
                />
              ))}
            </div>
          </motion.section>
        ) : null}
      </div>
    </div>
  );
}
