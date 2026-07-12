import classNames from "classnames";
import { motion } from "motion/react";
import { Link } from "react-router-dom";

import FeaturedConversationCard from "@/components/FeaturedConversationCard";
import GeneralInput from "@/components/GeneralInput";
import { AiChatSurface } from "@/components/ai-elements/ai-chat-surface";
import { KeyboardTypewriter } from "@/components/ai-elements/keyboard-typewriter";
import { ROUTES } from "@/router/routes";
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
            <h1
              className="mb-3 text-[32px] font-medium leading-[1.08] tracking-normal text-[var(--chat-text)] md:text-[42px] lg:text-[48px]"
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
            <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 className="text-[24px] font-medium text-[var(--chat-text)]">
                  精品对话
                </h2>
                <p className="mt-1 text-[13px] text-[var(--chat-text-soft)]">
                  浏览管理员精选的公开案例，直接查看完整回放。
                </p>
              </div>
              <Link
                to={ROUTES.FEATURED_CONVERSATIONS}
                className="inline-flex items-center gap-2 text-[13px] font-medium text-[var(--primary)] transition-colors hover:text-[var(--chat-text)]"
              >
                <span>查看全部</span>
                <i className="font_family icon-xinjianjiantou text-[10px]" />
              </Link>
            </div>

            {/* 精品对话始终走公共只读路由，避免和访客自己的会话状态耦合。 */}
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {props.featuredCards.map((card) => (
                <FeaturedConversationCard
                  key={card.featuredId}
                  card={card}
                />
              ))}
            </div>
          </motion.section>
        ) : null}
      </div>
    </div>
  );
}
