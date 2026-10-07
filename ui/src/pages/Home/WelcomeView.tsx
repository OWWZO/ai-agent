import classNames from "classnames";
import { motion } from "motion/react";
import { useEffect, useMemo, useState } from "react";
import {
  ArrowUp,
  AtSign,
  BarChart3,
  Briefcase,
  Building2,
  FileSearch,
  FileText,
  Globe2,
  Instagram,
  Link2,
  MapPin,
  MessageCircle,
  Megaphone,
  Music2,
  Search,
  Sparkles,
  ShoppingBag,
  Smartphone,
  UserRound,
  UsersRound,
  X as XIcon,
  Youtube,
  type LucideIcon,
} from "lucide-react";

import FeaturedConversationCard from "@/components/FeaturedConversationCard";
import GeneralInput from "@/components/GeneralInput";
import { AnimatedOrb } from "@/components/chat/AnimatedOrb";
import { KeyboardTypewriter } from "@/components/ai-elements/keyboard-typewriter";
import type { FeaturedConversationCard as FeaturedConversationCardModel } from "@/services/featuredConversation";
import { DURATION, EASE_OUT, useMotionConfig } from "@/lib/motion";
import {
  suggestedQuestionsByProductType,
  type SuggestedQuestionIcon,
  type SuggestedQuestion,
} from "@/utils/constants";
import type { PromptSelectionRequest } from "@/utils/suggestedPrompts";

const SUGGESTION_ICON_MAP: Record<SuggestedQuestionIcon, LucideIcon> = {
  youtube: Youtube,
  instagram: Instagram,
  creator: UsersRound,
  tiktok: Music2,
  x: XIcon,
  reddit: MessageCircle,
  shopping: ShoppingBag,
  megaphone: Megaphone,
  search: Search,
  research: FileSearch,
  chart: BarChart3,
  amazon: ShoppingBag,
  google: Search,
  meta: Globe2,
  openai: Sparkles,
  seo: FileSearch,
  link: Link2,
  mention: AtSign,
  company: Building2,
  linkedin: UsersRound,
  news: FileText,
  map: MapPin,
  person: UserRound,
  industry: Building2,
  briefcase: Briefcase,
  app: Smartphone,
};

const SUGGESTION_ICON_CLASS: Record<SuggestedQuestionIcon, string> = {
  youtube: "text-[#ff0000]",
  instagram: "text-[#e1306c]",
  creator: "text-[#8294e8]",
  tiktok: "text-[#111111]",
  x: "text-[#111111]",
  reddit: "text-[#ff4500]",
  shopping: "text-[#e58b2a]",
  megaphone: "text-[#e26b50]",
  search: "text-[#7468d8]",
  research: "text-[#4d83db]",
  chart: "text-[#4d83db]",
  amazon: "text-[#ff6b00]",
  google: "text-[#4285f4]",
  meta: "text-[#087eff]",
  openai: "text-[#111111]",
  seo: "text-[#8b9cfb]",
  link: "text-[#8b9cfb]",
  mention: "text-[#8b9cfb]",
  company: "text-[#087eff]",
  linkedin: "text-[#0877b5]",
  news: "text-[#8b9cfb]",
  map: "text-[#34a853]",
  person: "text-[#8b9cfb]",
  industry: "text-[#4285f4]",
  briefcase: "text-[#8b9cfb]",
  app: "text-[#147efb]",
};

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
  videoModalOpen?: string;
  featuredCards: FeaturedConversationCardModel[];
  onSelectionChange: (selection: {
    product: CHAT.Product;
    deepThink: boolean;
  }) => void;
  inputDraft?: string;
  onInputDraftChange?: (draft: string) => void;
  initialFiles?: File[];
  onInitialFilesConsumed?: (sessionId: string) => void;
  suggestedPromptSelection?: PromptSelectionRequest | null;
  onSend: (inputInfo: CHAT.TInputInfo) => void;
  onSendQuestion: (query: SuggestedQuestion) => void;
  onOpenVideo: (url: string) => void;
  onCloseVideo: () => void;
  onOpenFeaturedConversations?: () => void;
  onOpenFeaturedDetail?: (featuredId: string) => void;
}) {
  const suggestedQuestions = useMemo(
    () => suggestedQuestionsByProductType[props.product.type] ?? [],
    [props.product.type]
  );
  const categories = useMemo(
    () => Array.from(new Set(suggestedQuestions.map((item) => item.category))),
    [suggestedQuestions]
  );
  const [selectedCategory, setSelectedCategory] = useState("");
  useEffect(() => {
    if (!categories.includes(selectedCategory)) {
      setSelectedCategory(categories[0] || "");
    }
  }, [categories, selectedCategory]);
  const activeCategory = categories.includes(selectedCategory)
    ? selectedCategory
    : categories[0] || "";
  const visibleSuggestedQuestions = suggestedQuestions.filter(
    (item) => item.category === activeCategory
  );
  const hasSuggestedQuestions = suggestedQuestions.length > 0;
  const hasFeaturedCards = props.featuredCards.length > 0;
  const { reduce } = useMotionConfig();

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
              y: reduce ? 0 : hasSuggestedQuestions ? 0 : -8,
            }}
            transition={{
              duration: reduce ? DURATION.reduced : 0.22,
              ease: EASE_OUT,
            }}
            className={classNames(
              "mx-auto w-full max-w-[960px] overflow-visible",
              hasSuggestedQuestions
                ? "mb-8 pointer-events-auto lg:mb-10"
                : "mb-0 max-h-0 pointer-events-none"
            )}
          >
            <div
              className="mb-4 flex w-full min-w-0 justify-center gap-2 overflow-x-auto pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
              aria-label="推荐任务分类"
            >
              {categories.map((category) => {
                const active = category === activeCategory;
                return (
                  <button
                    key={category}
                    type="button"
                    aria-pressed={active}
                    className={classNames(
                      "shrink-0 rounded-full border px-4 py-2 text-[13px] font-medium transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--chat-accent)]/25",
                      active
                        ? "border-[oklch(0.83_0.03_90)] bg-[oklch(0.94_0.03_90)] text-[var(--chat-text)]"
                        : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text-soft)] hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)]"
                    )}
                    onClick={() => setSelectedCategory(category)}
                  >
                    {category}
                  </button>
                );
              })}
            </div>

            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              {visibleSuggestedQuestions.map((item) => {
                const Icon = SUGGESTION_ICON_MAP[item.icon] || Search;
                return (
                  <button
                    key={item.id}
                    type="button"
                    className="group flex min-h-[48px] min-w-0 w-full items-center gap-2 rounded-[12px] border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-2 text-left text-[13px] font-medium leading-4 text-[var(--chat-text)] shadow-[0_1px_1px_rgba(0,0,0,0.015)] transition-[border-color,background-color,box-shadow] duration-200 hover:border-[var(--chat-border-strong)] hover:bg-[var(--chat-surface-soft)] hover:shadow-[var(--shadow-sm)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--chat-accent)]/25"
                    onClick={() => props.onSendQuestion(item)}
                  >
                    <Icon
                      className={classNames(
                        "size-[17px] shrink-0 stroke-[1.8]",
                        SUGGESTION_ICON_CLASS[item.icon]
                      )}
                      aria-hidden="true"
                    />
                    <span className="min-w-0 flex-1">{item.label}</span>
                    <ArrowUp
                      className="size-[15px] shrink-0 text-[var(--chat-text-soft)] transition-transform duration-200 group-hover:-translate-y-0.5 group-hover:text-[var(--chat-text)]"
                      aria-hidden="true"
                    />
                  </button>
                );
              })}
            </div>
          </motion.div>

          <motion.div
            initial={
              reduce
                ? { opacity: 0 }
                : {
                  opacity: 0,
                  y: 12,
                  scale: 0.98,
                }
            }
            animate={{
              opacity: 1,
              y: 0,
              scale: 1,
            }}
            transition={{
              duration: reduce ? DURATION.reduced : 0.28,
              delay: reduce ? 0 : 0.08,
              ease: EASE_OUT,
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
                draftValue={props.inputDraft}
                onDraftChange={props.onInputDraftChange}
                initialFiles={props.initialFiles}
                onInitialFilesConsumed={() =>
                  props.onInitialFilesConsumed?.(props.currentConversation.sessionId)
                }
                promptSelectionRequest={props.suggestedPromptSelection}
                send={props.onSend}
                onSelectionChange={props.onSelectionChange}
              />
            </div>
          </motion.div>
        </div>

        {hasFeaturedCards ? (
          <motion.section
            initial={reduce ? { opacity: 0 } : {
              opacity: 0,
              y: 10
            }}
            animate={{
              opacity: 1,
              y: 0,
            }}
            transition={{
              duration: reduce ? DURATION.reduced : 0.28,
              delay: reduce ? 0 : 0.12,
              ease: EASE_OUT,
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
