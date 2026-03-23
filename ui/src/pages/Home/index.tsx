import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Drawer, Image } from "antd";
import classNames from "classnames";

import ChatView from "@/components/ChatView";
import GeneralInput from "@/components/GeneralInput";
import DataListDrawer from "@/components/DataListDrawer";
import ColsAndDataDrawer from "@/components/DataListDrawer/ColsAndDataDrawer";
import ResizableSidebar from "@/components/ResizableSidebar";
import { AiChatSurface } from "@/components/ai-elements/ai-chat-surface";
import type { LocalThreadListItem } from "@/components/assistant-ui/thread-list";
import { chatQustions, defaultProduct, demoList, productList } from "@/utils/constants";
import {
  CHAT_HISTORY_VERSION,
  createConversation,
  loadHistory,
  pruneHistory,
  saveHistory,
} from "@/utils/chatHistory";

type HomeProps = Record<string, never>;

type InitialState = {
  conversations: CHAT.ConversationHistory[];
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
};

const OUTPUT_TYPES = ["html", "docs", "ppt", "table"];
const EMPTY_INPUT: CHAT.TInputInfo = {
  message: "",
  deepThink: false,
};

const outputDescMap: Record<string, string> = {
  html: "Generate interactive HTML report",
  docs: "Output structured markdown document",
  ppt: "Generate PPT-style presentation",
  table: "Output structured table results",
};

const tagColorMap: Record<string, string> = {
  专业研究: "bg-[#f3f4f6] text-[#374151]",
  数据分析: "bg-[#ecfeff] text-[#0f766e]",
  竞品调研: "bg-[#fff7ed] text-[#c2410c]",
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

const createInitialState = (): InitialState => {
  const initialProduct = productList.find((item) => item.type === "html") ?? defaultProduct;
  const loaded = loadHistory().conversations;
  const seeded =
    loaded.length > 0
      ? pruneHistory(loaded)
      : [createConversation({ productType: initialProduct.type, deepThink: false })];
  const latest = [...seeded].sort((a, b) => b.updatedAt - a.updatedAt)[0];
  return {
    conversations: seeded,
    currentConversationId: latest.id,
    productType: latest.productType || initialProduct.type,
  };
};

const CaseCard = memo((props: CaseCardProps) => {
  const { title, description, tag, image, url, videoUrl, videoModalOpen, onOpenVideo, onCloseVideo } = props;
  const tagColor = tagColorMap[tag] ?? "bg-[#f5f5f7] text-[#86868b]";

  return (
    <div className="group relative flex w-[260px] shrink-0 cursor-pointer flex-col overflow-hidden rounded-[24px] bg-white shadow-[0_4px_24px_rgba(0,0,0,0.04),0_1px_2px_rgba(0,0,0,0.02)] transition-all duration-500 ease-out hover:shadow-[0_12px_40px_rgba(0,0,0,0.08),0_4px_12px_rgba(0,0,0,0.04)] hover:-translate-y-1">
      {/* Image Container */}
      <div className="relative h-[160px] overflow-hidden">
        <img
          src={image}
          className="h-full w-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
          alt={title}
        />
        <div
          className="absolute inset-0 flex items-center justify-center bg-black/0 transition-all duration-300 group-hover:bg-black/20"
          onClick={() => onOpenVideo(videoUrl)}
        >
          <div className="flex h-[48px] w-[48px] scale-75 items-center justify-center rounded-full bg-white/90 opacity-0 backdrop-blur-sm shadow-lg transition-all duration-300 group-hover:scale-100 group-hover:opacity-100 hover:bg-white hover:scale-105">
            <i className="font_family icon-bofang ml-[2px] text-[18px] text-[#1d1d1f]"></i>
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
          <h3 className="text-[15px] font-semibold leading-tight text-[#1d1d1f] line-clamp-1">{title}</h3>
          <span className={`inline-block shrink-0 rounded-full px-2.5 py-1 text-[11px] font-medium ${tagColor}`}>{tag}</span>
        </div>
        <p className="line-clamp-2 text-[13px] leading-[1.6] text-[#86868b]">{description}</p>
        <div
          className="flex cursor-pointer items-center gap-1.5 pt-1 text-[13px] font-medium text-[#0071e3] transition-colors duration-200 hover:text-[#0077ed]"
          onClick={() => window.open(url)}
        >
          <span>查看报告</span>
          <i className="font_family icon-xinjianjiantou text-[10px] transition-transform duration-200 group-hover:translate-x-0.5"></i>
        </div>
      </div>
    </div>
  );
});

const Home: GenieType.FC<HomeProps> = memo(() => {
  const initialRef = useRef<InitialState>(createInitialState());

  const [conversations, setConversations] = useState<CHAT.ConversationHistory[]>(
    initialRef.current.conversations
  );
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
  const [dbsShow, setDbsShow] = useState(false);
  const [dataShow, setDataShow] = useState(false);
  const [outputMenuOpen, setOutputMenuOpen] = useState(false);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  const [curModel, setCurModel] = useState<CHAT.ModelInfo>({
    modelName: "",
    modelCode: "",
    schemaList: [],
  });

  const outputMenuRef = useRef<HTMLDivElement>(null);

  const currentConversation = useMemo(
    () => conversations.find((item) => item.id === currentConversationId) || conversations[0],
    [conversations, currentConversationId]
  );

  const sortedConversations = useMemo(
    () => [...conversations].sort((a, b) => b.updatedAt - a.updatedAt),
    [conversations]
  );

  const hasConversationContent = useMemo(() => {
    if (!currentConversation) return false;
    return currentConversation.chatList.length > 0 || currentConversation.dataChatList.length > 0;
  }, [currentConversation]);

  // 自动折叠侧边栏：进入对话后自动折叠
  useEffect(() => {
    if (hasConversationContent) {
      setIsSidebarCollapsed(true);
    }
  }, [hasConversationContent]);

  useEffect(() => {
    if (!currentConversation) return;
    const matched = productList.find((item) => item.type === currentConversation.productType);
    if (!matched) return;
    setProduct((prev) => (prev.type === matched.type ? prev : matched));
    if (OUTPUT_TYPES.includes(matched.type)) {
      setDisplayOutput(matched);
    }
  }, [currentConversation]);

  useEffect(() => {
    if (!conversations.length) return;
    const exists = conversations.some((item) => item.id === currentConversationId);
    if (!exists) {
      const latest = [...conversations].sort((a, b) => b.updatedAt - a.updatedAt)[0];
      setCurrentConversationId(latest.id);
    }
  }, [conversations, currentConversationId]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      saveHistory({ version: CHAT_HISTORY_VERSION, conversations });
    }, 700);
    return () => window.clearTimeout(timer);
  }, [conversations]);

  useEffect(() => {
    const handler = (event: MouseEvent) => {
      if (outputMenuRef.current && !outputMenuRef.current.contains(event.target as Node)) {
        setOutputMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const updateConversation = useCallback(
    (conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      setConversations((prev) => {
        const exists = prev.some((item) => item.id === conversationId);
        const merged = exists
          ? prev.map((item) => (item.id === conversationId ? nextConversation : item))
          : [nextConversation, ...prev];
        return pruneHistory(merged);
      });
    },
    []
  );

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
      (item) => item.chatList.length === 0 && item.dataChatList.length === 0
    );
    if (existedEmpty) {
      setCurrentConversationId(existedEmpty.id);
      setInputInfo({ ...EMPTY_INPUT });
      setHistoryDrawerOpen(false);
      return;
    }

    const next = createConversation({ productType: product.type, deepThink: false });
    setConversations((prev) => pruneHistory([next, ...prev]));
    setCurrentConversationId(next.id);
    setInputInfo({ ...EMPTY_INPUT });
    setHistoryDrawerOpen(false);
  }, [product.type, sortedConversations]);

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
      });

      setInputInfo({ ...info, outputStyle, deepThink });
    },
    [currentConversation, product.type, updateCurrentConversationMeta]
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

  const handleSelectConversation = useCallback((conversationId: string) => {
    setCurrentConversationId(conversationId);
    setHistoryDrawerOpen(false);
    setInputInfo({ ...EMPTY_INPUT });
  }, []);

  const handleDeleteConversation = useCallback(
    (conversationId: string) => {
      setConversations((prev) => {
        const filtered = prev.filter((item) => item.id !== conversationId);
        if (filtered.length > 0) {
          const latest = [...filtered].sort((a, b) => b.updatedAt - a.updatedAt)[0];
          if (conversationId === currentConversationId) {
            setCurrentConversationId(latest.id);
            setInputInfo({ ...EMPTY_INPUT });
          }
          return filtered;
        }

        const fallback = createConversation({ productType: product.type, deepThink: false });
        setCurrentConversationId(fallback.id);
        setInputInfo({ ...EMPTY_INPUT });
        return [fallback];
      });
    },
    [currentConversationId, product.type]
  );

  const handleSelectProduct = useCallback(
    (nextProduct: CHAT.Product) => {
      setProduct(nextProduct);
      if (OUTPUT_TYPES.includes(nextProduct.type)) {
        setDisplayOutput(nextProduct);
      }
      if (!currentConversation) return;
      updateCurrentConversationMeta({
        productType: nextProduct.type,
        deepThink: nextProduct.type === "chat" ? false : currentConversation.deepThink,
      });
    },
    [currentConversation, updateCurrentConversationMeta]
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

  const primaryProducts = useMemo(
    () => productList.filter((item) => !OUTPUT_TYPES.includes(item.type)),
    []
  );
  const outputProducts = useMemo(
    () => productList.filter((item) => OUTPUT_TYPES.includes(item.type)),
    []
  );

  const isOutputActive = product.type === displayOutput.type;

  const renderWelcome = () => {
    return (
      <div
        className="min-h-full w-full overflow-y-auto px-6 pt-12 md:px-12 md:pt-20 lg:px-16 lg:pt-24"
        style={{
          background: "radial-gradient(ellipse 80% 50% at 50% -10%, rgba(0, 113, 227, 0.05), transparent 60%)",
        }}
      >
        <div className="mx-auto flex w-full max-w-[960px] flex-col items-center">
          {/* Hero Section */}
          <div className="mb-8 text-center">
            <h1
              className="mb-4 text-[48px] font-semibold leading-none tracking-[-0.025em] text-[#1d1d1f] md:text-[64px] lg:text-[72px]"
              style={{ fontFamily: "var(--font-brand)" }}
            >
              Reactor
            </h1>
            <p className="mx-auto max-w-[480px] text-[17px] leading-[1.6] text-[#86868b] md:text-[19px]">
              AI 智能体平台，一句话完成数据分析、研究调查和内容创作
            </p>
          </div>

          {/* Input Section */}
          <div className="mb-6 w-full max-w-[800px]">
            <AiChatSurface className="w-full rounded-[28px] bg-white/80 p-3 shadow-[0_8px_32px_rgba(0,0,0,0.06),0_2px_8px_rgba(0,0,0,0.04)] backdrop-blur-xl">
              <GeneralInput
                placeholder={product.placeholder}
                showBtn={true}
                size="big"
                disabled={false}
                product={product}
                send={changeInputInfo}
                dbsShow={setDbsShow}
              />
            </AiChatSurface>
          </div>

          {/* Mode Selector */}
          <div className="mb-16 flex w-full max-w-[800px] flex-wrap items-center justify-center gap-2.5">
            <div className="relative" ref={outputMenuRef}>
              <div
                className={classNames(
                  "h-[36px] px-4 cursor-pointer flex items-center gap-2 rounded-full border text-[14px] font-medium transition-all duration-200 select-none",
                  isOutputActive
                    ? "border-transparent bg-[#1d1d1f] text-white shadow-[0_4px_12px_rgba(0,0,0,0.15)]"
                    : "border-[#e8e8ed] bg-white text-[#86868b] hover:border-[#d2d2d7] hover:text-[#1d1d1f]"
                )}
                onClick={() => {
                  if (!isOutputActive) handleSelectProduct(displayOutput);
                  setOutputMenuOpen((v) => !v);
                }}
              >
                <i className={`font_family ${displayOutput.img} ${isOutputActive ? "text-white" : displayOutput.color} text-[14px]`} />
                <span>{displayOutput.name}</span>
                <svg
                  className={classNames("w-3.5 h-3.5 ml-0.5 transition-transform duration-200", {
                    "rotate-180": outputMenuOpen,
                  })}
                  viewBox="0 0 12 12"
                  fill="none"
                >
                  <path
                    d="M2.5 4.5L6 8L9.5 4.5"
                    stroke="currentColor"
                    strokeWidth="1.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </div>

              {outputMenuOpen && (
                <div className="absolute left-0 top-[44px] z-50 w-[240px] rounded-[18px] border border-[#e8e8ed] bg-white/98 p-2 shadow-[0_20px_48px_rgba(0,0,0,0.12)] backdrop-blur-xl animate-in fade-in slide-in-from-top-1 duration-200">
                  {outputProducts.map((item) => {
                    const isSelected = product.type === item.type;
                    return (
                      <div
                        key={item.type}
                        className={classNames(
                          "group/item flex cursor-pointer items-start gap-3 rounded-[14px] px-3 py-2.5 transition-all duration-150",
                          isSelected ? "bg-[#f5f5f7]" : "hover:bg-[#f5f5f7]"
                        )}
                        onClick={() => {
                          handleSelectProduct(item);
                          setOutputMenuOpen(false);
                        }}
                      >
                        <div
                          className={classNames(
                            "mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg",
                            isSelected ? "bg-white shadow-sm" : "bg-[#f5f5f7] group-hover/item:bg-white"
                          )}
                        >
                          <i className={`font_family ${item.img} ${item.color} text-[14px]`} />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className={classNames("text-[13px] font-medium", isSelected ? "text-[#1d1d1f]" : "text-[#1d1d1f]")}>{item.name}</div>
                          <div className="mt-0.5 text-[12px] leading-[1.4] text-[#86868b]">{outputDescMap[item.type]}</div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {primaryProducts.map((item) => (
              <div
                key={item.type}
                className={classNames(
                  "h-[36px] px-4 cursor-pointer flex items-center justify-center gap-2 rounded-full border text-[14px] font-medium transition-all duration-200 select-none",
                  item.type === product.type
                    ? "border-transparent bg-[#1d1d1f] text-white shadow-[0_4px_12px_rgba(0,0,0,0.15)]"
                    : "border-[#e8e8ed] bg-white text-[#86868b] hover:border-[#d2d2d7] hover:text-[#1d1d1f]"
                )}
                onClick={() => handleSelectProduct(item)}
              >
                <i className={`font_family ${item.img} ${item.type === product.type ? "text-white" : item.color} text-[14px]`} />
                <span>{item.name}</span>
              </div>
            ))}
          </div>

          {/* Suggested Questions - Only for dataAgent */}
          <div
            className={classNames(
              "w-full overflow-hidden transition-all duration-500",
              product.type === "dataAgent" ? "opacity-100 max-h-[100px] mb-10" : "opacity-0 max-h-0 mb-0"
            )}
          >
            <div className="flex flex-wrap justify-center gap-2.5">
              {chatQustions.map((item, index) => (
                <div
                  key={index}
                  className="flex cursor-pointer items-center gap-2 rounded-full border border-[#e8e8ed] bg-white px-4 py-2 text-[13px] text-[#86868b] transition-all duration-200 hover:border-[#d2d2d7] hover:text-[#1d1d1f] hover:shadow-sm"
                  onClick={() => toSendMessage(item)}
                >
                  {item.type === 2 && <i className="font_family icon-shendusikao text-[12px] text-[#0071e3]" />}
                  {item.label}
                </div>
              ))}
            </div>
          </div>

          {/* Cases Section */}
          <div className="w-full pb-20">
            <div className="mb-8 text-center">
              <h2 className="mb-2 text-[22px] font-semibold tracking-[-0.01em] text-[#1d1d1f]">精选案例</h2>
              <p className="text-[14px] text-[#86868b]">和 Genie 一起，让效率飞起来</p>
            </div>

            <div className="flex flex-wrap justify-center gap-6">
              {demoList.map((demo, index) => (
                <CaseCard
                  key={index}
                  {...demo}
                  videoModalOpen={videoModalOpen}
                  onOpenVideo={setVideoModalOpen}
                  onCloseVideo={() => setVideoModalOpen(undefined)}
                />
              ))}
            </div>
          </div>
        </div>

        <DataListDrawer show={dbsShow} dbsShow={setDbsShow} showDetail={(modelInfo) => {
          setCurModel(modelInfo);
          setDataShow(true);
        }} />
        {dataShow && (
          <ColsAndDataDrawer
            show={dataShow}
            dataShow={setDataShow}
            modelInfo={curModel}
          />
        )}
      </div>
    );
  };

  if (!currentConversation) return null;

  return (
    <div className="h-full w-full bg-[var(--page-shell)] text-foreground">
      <div className="flex h-full w-full">
        {/* Desktop Sidebar - Resizable and Collapsible */}
        <div className="hidden h-full shrink-0 lg:block">
          <ResizableSidebar
            items={threadListItems}
            onCreate={createNewChat}
            onSelect={handleSelectConversation}
            onDelete={handleDeleteConversation}
            isCollapsed={isSidebarCollapsed}
            onCollapsedChange={setIsSidebarCollapsed}
            defaultWidth={280}
            minWidth={240}
            maxWidth={400}
          />
        </div>

        {/* Mobile Drawer */}
        <Drawer
          title={null}
          placement="left"
          open={historyDrawerOpen}
          onClose={() => setHistoryDrawerOpen(false)}
          width={320}
          rootClassName="lg:hidden"
          className="[&_.ant-drawer-body]:p-0 [&_.ant-drawer-content]:bg-[#f5f5f7]"
        >
          <ResizableSidebar
            items={threadListItems}
            onCreate={createNewChat}
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
              className="flex h-9 items-center gap-2 rounded-full border border-[#e8e8ed] bg-white px-3 text-[13px] text-[#86868b] shadow-sm transition-all duration-200 hover:border-[#d2d2d7] hover:text-[#1d1d1f]"
              onClick={() => setHistoryDrawerOpen(true)}
            >
              <span>历史对话</span>
            </button>
          </div>

          {/* Content */}
          <div className="min-h-0 flex-1 overflow-auto">
            {!hasConversationContent && inputInfo.message.length === 0 ? (
              renderWelcome()
            ) : (
              <ChatView
                inputInfo={inputInfo}
                product={product}
                conversation={currentConversation}
                onConversationChange={updateConversation}
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
