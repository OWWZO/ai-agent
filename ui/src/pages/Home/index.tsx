import {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ComponentType,
} from "react";
import { Image } from "antd";
import classNames from "classnames";
import { motion } from "motion/react";
import {
  DatabaseZap,
  MessageSquarePlus,
  MessagesSquare,
  WandSparkles,
} from "lucide-react";
import ChatView from "@/components/ChatView";
import GeneralInput from "@/components/GeneralInput";
import WorkspaceMRag from "@/pages/WorkspaceMRag";
import WorkspaceImageGeneration from "@/pages/WorkspaceImageGeneration";
import { AiChatSurface } from "@/components/ai-elements/ai-chat-surface";
import { KeyboardTypewriter } from "@/components/ai-elements/keyboard-typewriter";
import { chatQustions, defaultProduct, demoList, productList } from "@/utils/constants";
import { getUniqId } from "@/utils";
import {
  roleLibraryApi,
  type FixRoleItem,
} from "@/services/agentConversation";

type HomeProps = Record<string, never>;

type SidebarView = "chat" | "mrag" | "image-generation";

type InitialState = {
  productType: string;
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

const OUTPUT_TYPES = ["html", "docs", "ppt", "table"];
const EMPTY_INPUT: CHAT.TInputInfo = {
  message: "",
  deepThink: false,
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
const SHOW_FEATURED_CASES = false;

const tagColorMap: Record<string, string> = {
  专业研究: "bg-[var(--secondary)] text-[var(--secondary-foreground)]",
  数据分析: "bg-[oklch(0.95_0.05_200)] text-[oklch(0.5_0.1_200)]",
  竞品调研: "bg-[oklch(0.95_0.05_50)] text-[oklch(0.5_0.12_50)]",
};

const navItems: {
  key: SidebarView;
  label: string;
  icon: ComponentType<{ className?: string }>;
}[] = [
  { key: "chat", label: "对话", icon: MessagesSquare },
  { key: "mrag", label: "MRAG", icon: DatabaseZap },
  { key: "image-generation", label: "生图", icon: WandSparkles },
];

const getModeName = (type: string) => {
  return productList.find((item) => item.type === type)?.name || type;
};

const toConversationRole = (
  role?: CHAT.FixRole | FixRoleItem | CHAT.ConversationRole | null
): CHAT.ConversationRole | null => {
  if (!role) {
    return null;
  }
  return {
    agentId: role.agentId,
    agentName: role.agentName,
    available: "available" in role ? role.available !== false : true,
    defaultRole: Boolean(role.defaultRole),
  };
};

const hasConversationContent = (
  conversation: CHAT.ConversationHistory | undefined
) => {
  if (!conversation) {
    return false;
  }
  return (
    conversation.chatList.length > 0 || conversation.dataChatList.length > 0
  );
};

const createConversation = (
  partial: Partial<CHAT.ConversationHistory> = {}
): CHAT.ConversationHistory => {
  const now = Date.now();
  return {
    id: partial.id || `conversation-${getUniqId()}`,
    sessionId: partial.sessionId || `session-${getUniqId()}`,
    title: partial.title || "新对话",
    productType: partial.productType || "chat",
    deepThink: Boolean(partial.deepThink),
    role: partial.role || null,
    createdAt: partial.createdAt ?? now,
    updatedAt: partial.updatedAt ?? now,
    chatTitle: partial.chatTitle || "",
    chatList: partial.chatList || [],
    dataChatList: partial.dataChatList || [],
  };
};

const createInitialState = (): InitialState => {
  const initialProduct =
    productList.find((item) => item.type === "html") ?? defaultProduct;
  return {
    productType: initialProduct.type,
  };
};

const CaseCard = memo((props: CaseCardProps) => {
  const {
    title,
    description,
    tag,
    image,
    url,
    videoUrl,
    videoModalOpen,
    onOpenVideo,
    onCloseVideo,
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
        <Image
          style={{ display: "none" }}
          preview={{
            visible: videoModalOpen === videoUrl,
            destroyOnHidden: true,
            imageRender: () => (
              <video muted width="80%" controls autoPlay src={videoUrl} />
            ),
            toolbarRender: () => null,
            onVisibleChange: onCloseVideo,
          }}
          src={image}
        />
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
});

const Home: ReactorType.FC<HomeProps> = memo(() => {
  const initialRef = useRef<InitialState>(createInitialState());
  const [fixRoles, setFixRoles] = useState<CHAT.FixRole[]>([]);
  const [activeView, setActiveView] = useState<SidebarView>("chat");
  const [inputInfo, setInputInfo] = useState<CHAT.TInputInfo>(EMPTY_INPUT);
  const [product, setProduct] = useState(
    () =>
      productList.find(
        (item) => item.type === initialRef.current.productType
      ) ?? defaultProduct
  );
  const [displayOutput, setDisplayOutput] = useState(
    () => productList.find((item) => item.type === "html") ?? defaultProduct
  );
  const [videoModalOpen, setVideoModalOpen] = useState<string>();

  const defaultFixRole = useMemo(
    () => fixRoles.find((item) => item.defaultRole) ?? fixRoles[0],
    [fixRoles]
  );

  const [currentConversation, setCurrentConversation] =
    useState<CHAT.ConversationHistory>(() =>
      createConversation({
        productType: initialRef.current.productType,
      })
    );

  const currentConversationRole = useMemo(() => {
    if (currentConversation.productType !== "chat") {
      return null;
    }
    return currentConversation.role || toConversationRole(defaultFixRole);
  }, [currentConversation.productType, currentConversation.role, defaultFixRole]);

  const currentHeaderTitle =
    currentConversation.chatTitle || currentConversation.title;

  const currentModeName = getModeName(currentConversation.productType);

  const canRenderChatView =
    activeView === "chat" &&
    (hasConversationContent(currentConversation) || inputInfo.message.length > 0);

  useEffect(() => {
    roleLibraryApi
      .list()
      .then((data: any) => {
        setFixRoles(data || []);
      })
      .catch((error) => {
        console.error("加载角色库失败", error);
      });
  }, []);

  useEffect(() => {
    if (
      currentConversation.productType !== "chat" ||
      currentConversation.role ||
      !defaultFixRole
    ) {
      return;
    }

    setCurrentConversation((prev) => ({
      ...prev,
      role: toConversationRole(defaultFixRole),
      updatedAt: Date.now(),
    }));
  }, [
    currentConversation.productType,
    currentConversation.role,
    defaultFixRole,
  ]);

  useEffect(() => {
    const matched = productList.find(
      (item) => item.type === currentConversation.productType
    );
    if (!matched) {
      return;
    }

    setProduct((prev) => (prev.type === matched.type ? prev : matched));
    if (OUTPUT_TYPES.includes(matched.type)) {
      setDisplayOutput((prev) =>
        prev.type === matched.type ? prev : matched
      );
    }
  }, [currentConversation.productType]);

  const resetInput = useCallback(() => {
    setInputInfo({ ...EMPTY_INPUT });
  }, []);

  const updateConversation = useCallback(
    (_conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      setCurrentConversation({
        ...nextConversation,
        updatedAt: Date.now(),
      });
    },
    []
  );

  const createNewChat = useCallback(
    (override?: Partial<CHAT.ConversationHistory>) => {
      setActiveView("chat");
      setCurrentConversation(
        createConversation({
          productType: override?.productType || product.type,
          deepThink: override?.deepThink ?? false,
          role:
            override?.role ||
            (product.type === "chat"
              ? toConversationRole(defaultFixRole)
              : null),
          ...override,
        })
      );
      resetInput();
    },
    [defaultFixRole, product.type, resetInput]
  );

  const updateCurrentConversationMeta = useCallback(
    (meta: Partial<CHAT.ConversationHistory>) => {
      setCurrentConversation((prev) => ({
        ...prev,
        ...meta,
        updatedAt: Date.now(),
      }));
    },
    []
  );

  const onInputConsumed = useCallback(() => {
    resetInput();
  }, [resetInput]);

  const changeInputInfo = useCallback(
    (info: CHAT.TInputInfo) => {
      const outputStyle = info.outputStyle || product.type;
      const isChatMode = outputStyle === "chat";
      const deepThink =
        isChatMode || outputStyle === "dataAgent" ? false : info.deepThink;

      updateCurrentConversationMeta({
        productType: outputStyle,
        deepThink,
        role: outputStyle === "chat" ? currentConversationRole : null,
      });

      setInputInfo({
        ...info,
        outputStyle,
        deepThink,
        aiAgentId:
          outputStyle === "chat" ? currentConversationRole?.agentId : undefined,
      });
    },
    [currentConversationRole, product.type, updateCurrentConversationMeta]
  );

  const handleInputSelectionChange = useCallback(
    ({
      product: nextProduct,
      deepThink: nextDeepThink,
    }: {
      product: CHAT.Product;
      deepThink: boolean;
    }) => {
      setProduct(nextProduct);
      if (OUTPUT_TYPES.includes(nextProduct.type)) {
        setDisplayOutput(nextProduct);
      }

      updateCurrentConversationMeta({
        productType: nextProduct.type,
        deepThink:
          nextProduct.type === "chat" || nextProduct.type === "dataAgent"
            ? false
            : nextDeepThink,
        role:
          nextProduct.type === "chat"
            ? currentConversation.role || toConversationRole(defaultFixRole)
            : null,
      });
    },
    [currentConversation.role, defaultFixRole, updateCurrentConversationMeta]
  );

  const handleRoleSelect = useCallback(
    (role: CHAT.FixRole) => {
      const nextRole = toConversationRole(role);

      if (
        currentConversation.productType === "chat" &&
        hasConversationContent(currentConversation)
      ) {
        createNewChat({
          productType: "chat",
          deepThink: false,
          role: nextRole,
        });
        return;
      }

      updateCurrentConversationMeta({
        productType: "chat",
        deepThink: false,
        role: nextRole,
      });
      setProduct(productList.find((item) => item.type === "chat") ?? defaultProduct);
      setActiveView("chat");
    },
    [createNewChat, currentConversation, updateCurrentConversationMeta]
  );

  const toSendMessage = useCallback(
    (query: Record<string, any>) => {
      changeInputInfo({
        message: query.label,
        outputStyle: "dataAgent",
        deepThink: query.type === 2,
      });
    },
    [changeInputInfo]
  );

  const renderWelcome = () => {
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
                key={`welcome-input-${currentConversation.sessionId}`}
                sessionId={currentConversation.sessionId}
                placeholder={product.placeholder}
                showBtn={true}
                size="big"
                disabled={false}
                product={product}
                deepThink={currentConversation.deepThink}
                displayOutput={displayOutput}
                chatRole={currentConversationRole}
                chatRoles={fixRoles}
                showRoleSelector={product.type === "chat"}
                send={changeInputInfo}
                onSelectionChange={handleInputSelectionChange}
                onRoleSelect={handleRoleSelect}
              />
            </AiChatSurface>
          </motion.div>

          <motion.div
            initial={false}
            animate={{
              opacity: product.type === "dataAgent" ? 1 : 0,
              y: product.type === "dataAgent" ? 0 : -10,
            }}
            transition={{
              duration: 0.3,
              ease: [0.16, 1, 0.3, 1],
            }}
            className={classNames(
              "mx-auto w-full max-w-[800px] overflow-hidden",
              product.type === "dataAgent"
                ? "mb-12 max-h-[100px] pointer-events-auto"
                : "mb-0 max-h-0 pointer-events-none"
            )}
          >
            <div className="flex flex-wrap justify-center gap-3">
              {chatQustions.map((item, index) => (
                <div
                  key={index}
                  className="flex cursor-pointer items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-5 py-2.5 text-[13px] text-[var(--chat-text-soft)] transition-all duration-300 hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)] hover:shadow-[var(--shadow-sm)]"
                  onClick={() => toSendMessage(item)}
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
                    videoModalOpen={videoModalOpen}
                    onOpenVideo={setVideoModalOpen}
                    onCloseVideo={() => setVideoModalOpen(undefined)}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="h-full w-full bg-[var(--page-gradient)] text-foreground">
      <div className="flex h-full w-full">
        <div className="hidden h-full w-[88px] shrink-0 border-r border-[var(--chat-border)] bg-[var(--chat-surface)]/95 lg:flex lg:flex-col lg:items-center lg:justify-between lg:px-3 lg:py-4">
          <div className="flex w-full flex-col gap-2">
            <button
              type="button"
              onClick={() => createNewChat()}
              className="flex h-11 w-full items-center justify-center rounded-2xl bg-[var(--chat-text)] text-[var(--chat-surface)] transition-colors hover:bg-[var(--chat-text)]/90"
              title="新对话"
            >
              <MessageSquarePlus className="h-5 w-5" />
            </button>
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = activeView === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => setActiveView(item.key)}
                  className={classNames(
                    "flex h-11 w-full items-center justify-center rounded-2xl transition-colors",
                    isActive
                      ? "bg-[var(--primary)]/10 text-[var(--primary)]"
                      : "text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                  )}
                  title={item.label}
                >
                  <Icon className="h-5 w-5" />
                </button>
              );
            })}
          </div>
          <div className="text-center text-[11px] text-[var(--chat-text-muted)]">
            单会话
          </div>
        </div>

        <div className="flex min-w-0 flex-1 flex-col">
          <div className="border-b border-[var(--chat-border)] bg-[var(--chat-surface)]/80 px-4 py-3 backdrop-blur-md sm:px-6">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div className="min-w-0">
                <div className="truncate text-[16px] font-medium text-[var(--chat-text)]">
                  {currentHeaderTitle}
                </div>
                <div className="mt-1 text-[12px] text-[var(--chat-text-soft)]">
                  {activeView === "chat"
                    ? `当前模式：${currentModeName}`
                    : activeView === "mrag"
                      ? "当前工作台：MRAG"
                      : "当前工作台：米醋画图"}
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                {navItems.map((item) => {
                  const Icon = item.icon;
                  const isActive = activeView === item.key;
                  return (
                    <button
                      key={item.key}
                      type="button"
                      onClick={() => setActiveView(item.key)}
                      className={classNames(
                        "inline-flex items-center gap-2 rounded-full border px-3 py-2 text-[13px] transition-colors",
                        isActive
                          ? "border-[var(--chat-border-strong)] bg-[var(--chat-surface-soft)] text-[var(--chat-text)]"
                          : "border-[var(--chat-border)] text-[var(--chat-text-soft)] hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)]"
                      )}
                    >
                      <Icon className="h-4 w-4" />
                      <span>{item.label}</span>
                    </button>
                  );
                })}
                <button
                  type="button"
                  onClick={() => createNewChat()}
                  className="inline-flex items-center gap-2 rounded-full bg-[var(--chat-text)] px-3 py-2 text-[13px] text-[var(--chat-surface)] transition-colors hover:bg-[var(--chat-text)]/90"
                >
                  <MessageSquarePlus className="h-4 w-4" />
                  <span>新对话</span>
                </button>
              </div>
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-auto">
            {activeView === "mrag" ? (
              <WorkspaceMRag embedded />
            ) : activeView === "image-generation" ? (
              <WorkspaceImageGeneration embedded />
            ) : canRenderChatView ? (
              <ChatView
                inputInfo={inputInfo}
                product={product}
                conversation={currentConversation}
                chatRoles={fixRoles}
                onConversationChange={updateConversation}
                onRoleSelect={handleRoleSelect}
                onInputConsumed={onInputConsumed}
              />
            ) : (
              renderWelcome()
            )}
          </div>
        </div>
      </div>
    </div>
  );
});

Home.displayName = "Home";

export default Home;
