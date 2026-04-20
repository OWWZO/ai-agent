import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Drawer, Image } from "antd";
import classNames from "classnames";
import { motion } from "motion/react";
import { Edit3Icon, MessageCircleIcon, SearchIcon, XIcon } from "lucide-react";

import ChatView from "@/components/ChatView";
import GeneralInput from "@/components/GeneralInput";
import ResizableSidebar from "@/components/ResizableSidebar";
import { AiChatSurface } from "@/components/ai-elements/ai-chat-surface";
import { KeyboardTypewriter } from "@/components/ai-elements/keyboard-typewriter";
import type { LocalThreadListItem } from "@/components/assistant-ui/thread-list";
import { chatQustions, defaultProduct, demoList, productList } from "@/utils/constants";
import { groupConversationHistoryItems } from "@/utils/conversationHistoryGroups";
import {
  hasLocalConversationContent,
  isDraftConversation,
  resolveConversationHistories,
} from "@/utils/chatHistory";
import { useAgentConversation } from "@/hooks/useAgentConversation";

type HomeProps = Record<string, never>;

type InitialState = {
  currentConversationId: string;
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

const HERO_TYPEWRITER_TEXTS = ["Let's build", "Let's create", "Hello! How can I help?", "Let's analyze", "Let's research", "Welcome back!", "Awaiting your instructions"];
const SHOW_FEATURED_CASES = false;

const tagColorMap: Record<string, string> = {
  专业研究: "bg-[var(--secondary)] text-[var(--secondary-foreground)]",
  数据分析: "bg-[oklch(0.95_0.05_200)] text-[oklch(0.5_0.1_200)]",
  竞品调研: "bg-[oklch(0.95_0.05_50)] text-[oklch(0.5_0.12_50)]",
};

const getModeName = (type: string) => {
  return productList.find((item) => item.type === type)?.name || type;
};

const formatHistoryTime = (timestamp: number) => {
  try {
    return new Date(timestamp).toLocaleString("zh-CN", {
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
};

const toConversationRole = (role?: CHAT.FixRole | null): CHAT.ConversationRole | null => {
  if (!role) {
    return null;
  }
  return {
    agentId: role.agentId,
    agentName: role.agentName,
    available: true,
    defaultRole: role.defaultRole,
  };
};

const createInitialState = (): InitialState => {
  const initialProduct = productList.find((item) => item.type === "html") ?? defaultProduct;
  return {
    currentConversationId: "",
    productType: initialProduct.type,
  };
};

const CaseCard = memo((props: CaseCardProps) => {
  const { title, description, tag, image, url, videoUrl, videoModalOpen, onOpenVideo, onCloseVideo, index } = props;
  const tagColor = tagColorMap[tag] ?? "bg-[var(--muted)] text-[var(--muted-foreground)]";

  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 24
      }}
      animate={{
        opacity: 1,
        y: 0
      }}
      transition={{
        duration: 0.7,
        delay: 0.8 + index * 0.1,
        ease: [0.16, 1, 0.3, 1],
      }}
      className="group relative flex w-[280px] shrink-0 cursor-pointer flex-col overflow-hidden rounded-[24px] bg-[var(--card)] shadow-[var(--shadow-sm)] transition-all duration-500 ease-out hover:shadow-[var(--shadow-lg)] hover:-translate-y-1 hover:border-[var(--border-strong)] border border-transparent"
    >
      {/* Image Container */}
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
          <div className="flex h-[48px] w-[48px] scale-75 items-center justify-center rounded-full bg-white/95 opacity-0 backdrop-blur-sm shadow-lg transition-all duration-300 group-hover:scale-100 group-hover:opacity-100 hover:bg-white hover:scale-105">
            <i className="font_family icon-bofang ml-[2px] text-[18px] text-[var(--foreground)]"></i>
          </div>
        </div>
        <Image
          style={{ display: "none" }}
          preview={{
            visible: videoModalOpen === videoUrl,
            destroyOnHidden: true,
            imageRender: () => <video muted width="80%" controls autoPlay src={videoUrl} />,
            toolbarRender: () => null,
            onVisibleChange: onCloseVideo,
          }}
          src={image}
        />
      </div>

      {/* Content */}
      <div className="flex flex-col gap-3 p-5">
        <div className="flex items-start justify-between gap-3">
          <h3 className="text-[16px] font-medium leading-tight text-[var(--chat-text)] line-clamp-1 font-[var(--font-sans)]">{title}</h3>
          <span className={`inline-block shrink-0 rounded-full px-2.5 py-1 text-[11px] font-medium ${tagColor}`}>{tag}</span>
        </div>
        <p className="line-clamp-2 text-[13px] leading-[1.6] text-[var(--chat-text-soft)]">{description}</p>
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

  // ---- API 会话管理（渐进式迁移）----
  const {
    apiMode,
    remoteConversations,
    detailCache,
    draftConversations,
    fixRoles,
    loadConversationDetail,
    cacheConversationDetail,
    removeConversationDetail,
    createDraftConversation,
    upsertDraftConversation,
    removeDraftConversation,
    createRemoteConversation,
    deleteRemoteConversation,
  } = useAgentConversation();
  const [detailLoading, setDetailLoading] = useState(false);
  const [currentConversationId, setCurrentConversationId] = useState(
    initialRef.current.currentConversationId
  );

  const [inputInfo, setInputInfo] = useState<CHAT.TInputInfo>(EMPTY_INPUT);
  const [product, setProduct] = useState(
    () => productList.find((item) => item.type === initialRef.current.productType) ?? defaultProduct
  );
  const [displayOutput, setDisplayOutput] = useState(
    () => productList.find((item) => item.type === "html") ?? defaultProduct
  );

  const [videoModalOpen, setVideoModalOpen] = useState<string>();
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [searchPanelOpen, setSearchPanelOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);

  const defaultFixRole = useMemo(
    () => fixRoles.find((item) => item.defaultRole) ?? fixRoles[0],
    [fixRoles]
  );

  const conversations = useMemo(
    () =>
      resolveConversationHistories({
        summaries: remoteConversations,
        detailCache,
        drafts: draftConversations,
        fallbackChatRole: toConversationRole(defaultFixRole),
      }),
    [defaultFixRole, detailCache, draftConversations, remoteConversations]
  );

  const currentConversation = useMemo(
    () => conversations.find((item) => item.id === currentConversationId) || conversations[0],
    [conversations, currentConversationId]
  );

  const currentConversationRole = useMemo(() => {
    if (!currentConversation || currentConversation.productType !== "chat") {
      return null;
    }
    return currentConversation.role || toConversationRole(defaultFixRole);
  }, [currentConversation, defaultFixRole]);

  const sortedConversations = useMemo(
    () => [...conversations].sort((a, b) => b.updatedAt - a.updatedAt),
    [conversations]
  );

  const remoteMessageCountMap = useMemo(
    () => new Map(remoteConversations.map((item) => [item.sessionId, item.messageCount])),
    [remoteConversations]
  );

  const remoteSessionIdSet = useMemo(
    () => new Set(remoteConversations.map((item) => item.sessionId)),
    [remoteConversations]
  );

  const hasConversationContent = useMemo(() => {
    if (hasLocalConversationContent(currentConversation)) return true;
    if (!currentConversation || !apiMode) return false;
    return (remoteMessageCountMap.get(currentConversation.sessionId) ?? 0) > 0;
  }, [currentConversation, apiMode, remoteMessageCountMap]);

  useEffect(() => {
    if (!currentConversation) return;
    const matched = productList.find((item) => item.type === currentConversation.productType);
    if (!matched) return;
    setProduct((prev) => (prev.type === matched.type ? prev : matched));
    if (OUTPUT_TYPES.includes(matched.type)) {
      setDisplayOutput(matched);
    }
  }, [currentConversation]);

  const updateConversation = useCallback(
    (_conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      const remoteMessageCount =
        remoteMessageCountMap.get(nextConversation.sessionId) ?? 0;
      const hasRemoteSummary = remoteSessionIdSet.has(nextConversation.sessionId);
      const keepAsDraft =
        !hasRemoteSummary ||
        (remoteMessageCount === 0 && isDraftConversation(nextConversation));

      if (keepAsDraft) {
        upsertDraftConversation(nextConversation);
        return;
      }

      cacheConversationDetail(nextConversation);
      removeDraftConversation(nextConversation.sessionId);
    },
    [
      cacheConversationDetail,
      remoteMessageCountMap,
      remoteSessionIdSet,
      removeDraftConversation,
      upsertDraftConversation,
    ]
  );

  useEffect(() => {
    if (!currentConversation || currentConversation.productType !== "chat" || currentConversation.role || !defaultFixRole) {
      return;
    }
    updateConversation(currentConversation.id, {
      ...currentConversation,
      role: toConversationRole(defaultFixRole),
      updatedAt: Date.now(),
    });
  }, [currentConversation, defaultFixRole, updateConversation]);

  useEffect(() => {
    if (!conversations.length) {
      const nextDraft = createDraftConversation({
        productType: product.type,
        deepThink: false,
        role: product.type === "chat" ? toConversationRole(defaultFixRole) : null,
      });
      setCurrentConversationId(nextDraft.id);
      setInputInfo({ ...EMPTY_INPUT });
      return;
    }

    const exists = conversations.some((item) => item.id === currentConversationId);
    if (exists) {
      return;
    }

    const draftConversation = conversations.find(
      (conversation) =>
        isDraftConversation(conversation) &&
        (remoteMessageCountMap.get(conversation.sessionId) ?? 0) === 0
    );
    if (draftConversation) {
      setCurrentConversationId(draftConversation.id);
      return;
    }

    const nextDraft = createDraftConversation({
      productType: product.type,
      deepThink: false,
      role: product.type === "chat" ? toConversationRole(defaultFixRole) : null,
    });
    setCurrentConversationId(nextDraft.id);
    setInputInfo({ ...EMPTY_INPUT });
  }, [
    conversations,
    createDraftConversation,
    currentConversationId,
    defaultFixRole,
    product.type,
    remoteMessageCountMap,
  ]);

  const updateCurrentConversationMeta = useCallback(
    (meta: Partial<CHAT.ConversationHistory>) => {
      if (!currentConversation) return;
      updateConversation(currentConversation.id, {
        ...currentConversation,
        ...meta,
        updatedAt: Date.now(),
      });
    },
    [currentConversation, updateConversation]
  );

  const createNewChat = useCallback(() => {
    const existedEmpty = sortedConversations.find(
      (item) => isDraftConversation(item) && (remoteMessageCountMap.get(item.sessionId) ?? 0) === 0
    );
    if (existedEmpty) {
      setCurrentConversationId(existedEmpty.id);
      setInputInfo({ ...EMPTY_INPUT });
      setHistoryDrawerOpen(false);
      return;
    }

    const next = createDraftConversation({
      productType: product.type,
      deepThink: false,
      role: product.type === "chat" ? toConversationRole(defaultFixRole) : null,
    });
    if (apiMode) {
      const agentType = product.type === "chat" ? 0 : next.deepThink ? 1 : 2;
      createRemoteConversation(
        next.sessionId,
        agentType,
        product.type,
        "新对话",
        product.type === "chat" ? next.role?.agentId : undefined
      );
    }
    setCurrentConversationId(next.id);
    setInputInfo({ ...EMPTY_INPUT });
    setHistoryDrawerOpen(false);
  }, [
    apiMode,
    createDraftConversation,
    createRemoteConversation,
    defaultFixRole,
    product.type,
    remoteMessageCountMap,
    sortedConversations,
  ]);

  const onInputConsumed = useCallback(() => {
    setInputInfo({ ...EMPTY_INPUT });
  }, []);

  const changeInputInfo = useCallback(
    (info: CHAT.TInputInfo) => {
      if (!currentConversation) return;
      const outputStyle = info.outputStyle || product.type;
      const isChatMode = outputStyle === "chat";
      const deepThink = isChatMode ? false : info.deepThink;

      updateCurrentConversationMeta({
        productType: outputStyle,
        deepThink,
        role: outputStyle === "chat" ? currentConversationRole : null,
      });

      setInputInfo({
        ...info,
        outputStyle,
        deepThink,
        aiAgentId: outputStyle === "chat" ? currentConversationRole?.agentId : undefined,
      });
    },
    [currentConversation, product.type, updateCurrentConversationMeta, currentConversationRole]
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

  const handleSelectConversation = useCallback(
    (conversationId: string) => {
      setCurrentConversationId(conversationId);
      setHistoryDrawerOpen(false);
      setInputInfo({ ...EMPTY_INPUT });

      // API模式下懒加载会话详情
      if (apiMode) {
        const conv = conversations.find((c) => c.id === conversationId);
        if (conv && !hasLocalConversationContent(conv)) {
          const remote = remoteConversations.find((rc) => rc.sessionId === conv.sessionId);
          if (remote && remote.messageCount > 0) {
            setDetailLoading(true);
            loadConversationDetail(conv.sessionId).finally(() =>
              setDetailLoading(false)
            );
          }
        }
      }
    },
    [apiMode, conversations, remoteConversations, loadConversationDetail]
  );

  const handleDeleteConversation = useCallback(
    (conversationId: string) => {
      const conversation = conversations.find((item) => item.id === conversationId);
      if (!conversation) {
        return;
      }

      if (apiMode && remoteSessionIdSet.has(conversation.sessionId)) {
        deleteRemoteConversation(conversation.sessionId);
      }

      removeConversationDetail(conversation.sessionId);
      removeDraftConversation(conversation.sessionId);

      const filtered = conversations.filter(
        (item) =>
          item.id !== conversationId && item.sessionId !== conversation.sessionId
      );

      if (filtered.length > 0) {
        const latest = [...filtered].sort((a, b) => b.updatedAt - a.updatedAt)[0];
        if (conversationId === currentConversationId) {
          setCurrentConversationId(latest.id);
          setInputInfo({ ...EMPTY_INPUT });
        }
        return;
      }

      const fallback = createDraftConversation({
        productType: product.type,
        deepThink: false,
        role: product.type === "chat" ? toConversationRole(defaultFixRole) : null,
      });
      setCurrentConversationId(fallback.id);
      setInputInfo({ ...EMPTY_INPUT });
    },
    [
      apiMode,
      conversations,
      createDraftConversation,
      currentConversationId,
      defaultFixRole,
      deleteRemoteConversation,
      product.type,
      remoteSessionIdSet,
      removeConversationDetail,
      removeDraftConversation,
    ]
  );

  const handleInputSelectionChange = useCallback(
    ({ product: nextProduct, deepThink: nextDeepThink }: { product: CHAT.Product; deepThink: boolean }) => {
      setProduct(nextProduct);
      if (OUTPUT_TYPES.includes(nextProduct.type)) {
        setDisplayOutput(nextProduct);
      }
      if (!currentConversation) return;
      const nextRole = nextProduct.type === "chat" ? currentConversation.role || toConversationRole(defaultFixRole) : null;
      updateCurrentConversationMeta({
        productType: nextProduct.type,
        deepThink: nextProduct.type === "chat" || nextProduct.type === "dataAgent" ? false : nextDeepThink,
        role: nextRole,
      });
    },
    [currentConversation, updateCurrentConversationMeta, defaultFixRole]
  );

  const handleRoleSelect = useCallback(
    (role: CHAT.FixRole) => {
      if (!currentConversation) return;
      const nextRole = toConversationRole(role);
      const hasRemoteMessages = (remoteMessageCountMap.get(currentConversation.sessionId) ?? 0) > 0;
      const hasMessages = hasLocalConversationContent(currentConversation) || hasRemoteMessages;

      if (currentConversation.productType === "chat" && hasMessages) {
        const nextConversation = createDraftConversation({
          productType: "chat",
          deepThink: false,
          role: nextRole,
        });
        if (apiMode) {
          createRemoteConversation(nextConversation.sessionId, 0, "chat", "新对话", role.agentId);
        }
        setCurrentConversationId(nextConversation.id);
        setInputInfo({ ...EMPTY_INPUT });
        return;
      }

      updateCurrentConversationMeta({
        productType: "chat",
        deepThink: false,
        role: nextRole,
      });
      setProduct(productList.find((item) => item.type === "chat") ?? defaultProduct);
    },
    [
      apiMode,
      createDraftConversation,
      createRemoteConversation,
      currentConversation,
      remoteMessageCountMap,
      updateCurrentConversationMeta,
    ]
  );

  const threadListItems = useMemo<LocalThreadListItem[]>(
    () =>
      sortedConversations.map((item) => ({
        id: item.id,
        title: item.chatTitle || item.title || "新对话",
        subtitle: getModeName(item.productType),
        timestamp: formatHistoryTime(item.updatedAt),
        updatedAt: item.updatedAt,
        isActive: item.id === currentConversationId,
      })),
    [currentConversationId, sortedConversations]
  );

  const filteredThreadItems = useMemo(() => {
    const keyword = searchQuery.trim().toLowerCase();
    if (!keyword) return threadListItems;
    return threadListItems.filter((item) => {
      const title = item.title.toLowerCase();
      const subtitle = item.subtitle.toLowerCase();
      return title.includes(keyword) || subtitle.includes(keyword);
    });
  }, [searchQuery, threadListItems]);

  const groupedFilteredThreadItems = useMemo(
    () => groupConversationHistoryItems(filteredThreadItems),
    [filteredThreadItems]
  );

  const openSearchPanel = useCallback(() => {
    setSearchPanelOpen(true);
    setSearchQuery("");
  }, []);

  const closeSearchPanel = useCallback(() => {
    setSearchPanelOpen(false);
  }, []);

  const renderWelcome = () => {
    return (
      <div className="h-full w-full px-6 md:px-12 lg:px-16">
        <div className="mx-auto flex h-full w-full max-w-[1000px] flex-col items-center justify-center py-12">
          {/* Hero Section */}
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

          {/* Input Section */}
          <motion.div
            initial={{
              opacity: 0,
              y: 24,
              scale: 0.98
            }}
            animate={{
              opacity: 1,
              y: 0,
              scale: 1
            }}
            transition={{
              duration: 0.8,
              delay: 0.5,
              ease: [0.16, 1, 0.3, 1]
            }}
            className="mb-12 w-full max-w-[920px]"
          >
            <AiChatSurface className="w-full rounded-[32px] bg-[var(--chat-surface)]/90 p-5 shadow-none">
              <GeneralInput
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

          {/* Suggested Questions - Only for dataAgent */}
          <motion.div
            initial={false}
            animate={{
              opacity: product.type === "dataAgent" ? 1 : 0,
              y: product.type === "dataAgent" ? 0 : -10,
            }}
            transition={{
              duration: 0.3,
              ease: [0.16, 1, 0.3, 1]
            }}
            className={classNames(
              "w-full max-w-[800px] mx-auto overflow-hidden",
              product.type === "dataAgent" ? "max-h-[100px] mb-12 pointer-events-auto" : "max-h-0 mb-0 pointer-events-none"
            )}
          >
            <div className="flex flex-wrap justify-center gap-3">
              {chatQustions.map((item, index) => (
                <div
                  key={index}
                  className="flex cursor-pointer items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-5 py-2.5 text-[13px] text-[var(--chat-text-soft)] transition-all duration-300 hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)] hover:shadow-[var(--shadow-sm)]"
                  onClick={() => toSendMessage(item)}
                >
                  {item.type === 2 && <i className="font_family icon-shendusikao text-[12px] text-[var(--primary)]" />}
                  {item.label}
                </div>
              ))}
            </div>
          </motion.div>

          {/* Cases Section (temporary hidden) */}
          {SHOW_FEATURED_CASES && (
            <div className="w-full max-w-[1000px] mx-auto pb-24 mt-8">
              <motion.div
                initial={{
                  opacity: 0,
                  y: 20
                }}
                animate={{
                  opacity: 1,
                  y: 0
                }}
                transition={{
                  duration: 0.7,
                  delay: 0.75,
                  ease: [0.16, 1, 0.3, 1]
                }}
                className="mb-10 text-center"
              >
                <h2 className="mb-3 text-[28px] font-normal tracking-[-0.02em] text-[var(--chat-text)]" style={{ fontFamily: "var(--font-display)" }}>精选案例</h2>
                <p className="text-[15px] text-[var(--chat-text-soft)]" style={{ fontFamily: "var(--font-sans)" }}>和 Reactor 一起，让效率飞起来</p>
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

  if (!currentConversation) return null;

  return (
    <div className="h-full w-full bg-[var(--page-gradient)] text-foreground">
      {searchPanelOpen && (
        <div
          className="fixed inset-0 z-[120] flex items-start justify-center bg-black/12 px-4 pt-6 sm:px-8 sm:pt-10"
          onClick={closeSearchPanel}
        >
          <div
            className="w-full max-w-[980px] overflow-hidden rounded-[22px] border border-[var(--chat-border)] bg-[var(--chat-surface)] shadow-[0_24px_80px_-36px_rgba(15,23,42,0.5)]"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center border-b border-[var(--chat-border)] px-6 py-4">
              <div className="flex min-w-0 flex-1 items-center gap-3 text-[var(--chat-text-soft)]">
                <SearchIcon className="h-6 w-6 shrink-0" />
                <input
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  autoFocus
                  placeholder="搜索聊天..."
                  className="w-full border-none bg-transparent text-[16px] leading-none tracking-tight text-[var(--chat-text)] outline-none placeholder:text-[var(--chat-text-muted)]"
                />
              </div>
              <button
                onClick={closeSearchPanel}
                className="ml-4 flex h-9 w-9 items-center justify-center rounded-lg text-[var(--chat-text-muted)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
              >
                <XIcon className="h-5 w-5" />
              </button>
            </div>

            <div className="max-h-[70vh] overflow-y-auto px-4 py-4">
              <button
                type="button"
                onClick={() => {
                  createNewChat();
                  closeSearchPanel();
                }}
                className="mb-4 flex h-14 w-full items-center gap-3 rounded-[14px] bg-[var(--chat-surface-soft)] px-5 text-left font-medium text-[var(--chat-text)] transition-colors hover:bg-[var(--chat-surface-muted)]"
              >
                <Edit3Icon className="h-6 w-6 shrink-0 text-[var(--chat-text-soft)]" />
                <span className="text-[16px] leading-none tracking-tight">新聊天</span>
              </button>

              {filteredThreadItems.length ? (
                <div className="space-y-4">
                  {groupedFilteredThreadItems.map((group) =>
                    group.items.length ? (
                      <div key={group.key}>
                        <div className="mb-3 px-2 text-[16px] leading-none text-[var(--chat-text-muted)]">
                          {group.label}
                        </div>
                        <div className="space-y-1">
                          {group.items.map((item) => (
                            <button
                              key={item.id}
                              type="button"
                              onClick={() => {
                                handleSelectConversation(item.id);
                                closeSearchPanel();
                              }}
                              className="flex w-full items-center gap-3 rounded-[12px] px-3 py-3 text-left text-[var(--chat-text)] transition-colors hover:bg-[var(--chat-surface-soft)]"
                            >
                              <MessageCircleIcon className="h-6 w-6 shrink-0 text-[var(--chat-text-soft)]" />
                              <span className="truncate text-[16px] leading-none tracking-tight">{item.title}</span>
                            </button>
                          ))}
                        </div>
                      </div>
                    ) : null
                  )}
                </div>
              ) : (
                <div className="rounded-[12px] border border-dashed border-[var(--chat-border)] px-4 py-6 text-[16px] text-[var(--chat-text-muted)]">
                  未找到匹配的历史对话
                </div>
              )}
            </div>
          </div>
        </div>
      )}
      <div className="flex h-full w-full">
        {/* Desktop Sidebar - Resizable and Collapsible */}
        <div className="hidden h-full shrink-0 lg:block">
          <ResizableSidebar
            items={threadListItems}
            onCreate={createNewChat}
            onSearchOpen={openSearchPanel}
            onSelect={handleSelectConversation}
            onDelete={handleDeleteConversation}
            isCollapsed={isSidebarCollapsed}
            onCollapsedChange={setIsSidebarCollapsed}
            defaultWidth={240}
            minWidth={240}
            maxWidth={420}
          />
        </div>

        {/* Mobile Drawer */}
        <Drawer
          title={null}
          placement="left"
          open={historyDrawerOpen}
          onClose={() => setHistoryDrawerOpen(false)}
          width={240}
          rootClassName="lg:hidden"
          className="[&_.ant-drawer-body]:p-0 [&_.ant-drawer-content]:bg-[var(--chat-surface-soft)]"
        >
          <ResizableSidebar
            items={threadListItems}
            onCreate={createNewChat}
            onSearchOpen={openSearchPanel}
            onSelect={handleSelectConversation}
            onDelete={handleDeleteConversation}
            isCollapsed={false}
            onCollapsedChange={() => {}}
          />
        </Drawer>

        {/* Main Content Area */}
        <div className="flex h-full min-w-0 flex-1 flex-col bg-transparent">
          {/* Mobile Header - Only show when sidebar is collapsed or on mobile */}
          <div className="flex items-center justify-between px-4 pb-3 pt-4 lg:hidden">
            <button
              className="flex h-9 items-center gap-2 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 text-[13px] text-[var(--chat-text-soft)] shadow-sm transition-all duration-300 hover:border-[var(--chat-border-strong)] hover:text-[var(--chat-text)]"
              onClick={() => setHistoryDrawerOpen(true)}
            >
              <span>历史对话</span>
            </button>
          </div>

          {/* Content */}
          <div className="min-h-0 flex-1 overflow-auto">
            {!hasConversationContent && inputInfo.message.length === 0 ? (
              renderWelcome()
            ) : detailLoading ? (
              <div className="flex h-full items-center justify-center">
                <div className="text-[14px] text-[var(--chat-text-soft)]">加载对话历史...</div>
              </div>
            ) : (
              <ChatView
                inputInfo={inputInfo}
                product={product}
                conversation={currentConversation}
                chatRoles={fixRoles}
                onConversationChange={updateConversation}
                onRoleSelect={handleRoleSelect}
                onInputConsumed={onInputConsumed}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
});

Home.displayName = "Home";

export default Home;
