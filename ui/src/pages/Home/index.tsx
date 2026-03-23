import { useState, useCallback, memo, useRef, useEffect, useMemo } from "react";
import GeneralInput from "@/components/GeneralInput";
import ChatView from "@/components/ChatView";
import DataListDrawer from "@/components/DataListDrawer";
import ColsAndDataDrawer from "@/components/DataListDrawer/ColsAndDataDrawer";
import {
  productList,
  defaultProduct,
  chatQustions,
  demoList,
} from "@/utils/constants";
import {
  CHAT_HISTORY_VERSION,
  createConversation,
  loadHistory,
  pruneHistory,
  saveHistory,
} from "@/utils/chatHistory";
import { Drawer, Image } from "antd";
import classNames from "classnames";
import { MenuIcon, PlusIcon, Trash2Icon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

type HomeProps = Record<string, never>;

const OUTPUT_TYPES = ["html", "docs", "ppt", "table"];
const EMPTY_INPUT: CHAT.TInputInfo = {
  message: "",
  deepThink: false,
};

const outputDescMap: Record<string, string> = {
  html: "生成可交互的 HTML 网页报告",
  docs: "以 Markdown 格式输出结构化文档",
  ppt: "自动生成可演示的 PPT 文档",
  table: "以结构化表格形式呈现结论",
};

const tagColorMap: Record<string, string> = {
  专业研究: "bg-[rgba(64,64,255,0.08)] text-[#4040ff]",
  数据分析: "bg-[rgba(16,185,129,0.08)] text-[#059669]",
  竞品调研: "bg-[rgba(245,158,11,0.08)] text-[#d97706]",
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

const CaseCard = memo(
  ({
    title,
    description,
    tag,
    image,
    url,
    videoUrl,
    videoModalOpen,
    onOpenVideo,
    onCloseVideo,
  }: CaseCardProps) => {
    const tagColor = tagColorMap[tag] ?? "bg-gray-100 text-gray-500";
    return (
      <div className="group flex flex-col rounded-2xl bg-white shadow-[0_2px_16px_rgba(64,64,255,0.06)] hover:shadow-[0_12px_36px_rgba(64,64,255,0.14)] hover:-translate-y-[6px] transition-all duration-300 ease-in-out cursor-pointer w-[210px] shrink-0 border border-[rgba(233,233,240,0.9)] overflow-hidden">
        <div className="relative overflow-hidden h-[148px]">
          <img
            src={image}
            className="w-full h-full object-cover group-hover:scale-[1.06] transition-transform duration-500 ease"
          />
          <div
            className="absolute inset-0 flex items-center justify-center bg-transparent group-hover:bg-[rgba(0,0,0,0.45)] transition-all duration-300"
            onClick={() => onOpenVideo(videoUrl)}
          >
            <div className="opacity-0 group-hover:opacity-100 w-[44px] h-[44px] rounded-full bg-white/20 backdrop-blur-sm flex items-center justify-center border border-white/50 transition-all duration-300 scale-75 group-hover:scale-100">
              <i className="font_family icon-bofang text-[#fff] text-[18px] ml-[2px]"></i>
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
        <div className="p-[16px] flex flex-col gap-[8px]">
          <div className="flex items-center justify-between gap-[8px]">
            <div className="text-[14px] font-bold text-[#18181b] truncate">{title}</div>
            <span
              className={`shrink-0 inline-block px-[8px] leading-[22px] text-[11px] rounded-[6px] font-medium ${tagColor}`}
            >
              {tag}
            </span>
          </div>
          <div className="text-[12px] text-[#71717a] line-clamp-2 leading-[20px]">{description}</div>
          <div
            className="text-[#4040ff] group-hover:text-[#656cff] text-[12px] flex items-center gap-[3px] cursor-pointer transition-colors duration-200 pt-[2px]"
            onClick={() => window.open(url)}
          >
            <span>查看报告</span>
            <i className="font_family icon-xinjianjiantou text-[10px]"></i>
          </div>
        </div>
      </div>
    );
  }
);

type InitialState = {
  conversations: CHAT.ConversationHistory[];
  currentConversationId: string;
  productType: string;
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
    () =>
      productList.find((item) => item.type === initialRef.current.productType) ??
      defaultProduct
  );
  const [videoModalOpen, setVideoModalOpen] = useState<string>();
  const [dbsShow, setDbsShow] = useState(false);
  const [dataShow, setDataShow] = useState(false);
  const [outputMenuOpen, setOutputMenuOpen] = useState(false);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [displayOutput, setDisplayOutput] = useState(
    () => productList.find((item) => item.type === "html") ?? defaultProduct
  );
  const [curModel, setCurModel] = useState<CHAT.ModelInfo>({
    modelName: "",
    modelCode: "",
    schemaList: [],
  });
  const outputMenuRef = useRef<HTMLDivElement>(null);

  const currentConversation = useMemo(() => {
    return (
      conversations.find((item) => item.id === currentConversationId) || conversations[0]
    );
  }, [conversations, currentConversationId]);

  const sortedConversations = useMemo(() => {
    return [...conversations].sort((a, b) => b.updatedAt - a.updatedAt);
  }, [conversations]);

  const hasConversationContent = useMemo(() => {
    if (!currentConversation) return false;
    return (
      currentConversation.chatList.length > 0 ||
      currentConversation.dataChatList.length > 0
    );
  }, [currentConversation]);

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
      saveHistory({
        version: CHAT_HISTORY_VERSION,
        conversations,
      });
    }, 700);
    return () => window.clearTimeout(timer);
  }, [conversations]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (outputMenuRef.current && !outputMenuRef.current.contains(e.target as Node)) {
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
    const next = createConversation({
      productType: product.type,
      deepThink: false,
    });
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
      setInputInfo({
        ...info,
        outputStyle,
        deepThink,
      });
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

  const showDetail = useCallback((modelInfo: CHAT.ModelInfo) => {
    setCurModel(modelInfo);
    setDataShow(true);
  }, []);

  const handleOpenVideo = useCallback((url: string) => {
    setVideoModalOpen(url);
  }, []);

  const handleCloseVideo = useCallback(() => {
    setVideoModalOpen(undefined);
  }, []);

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
        const fallback = createConversation({
          productType: product.type,
          deepThink: false,
        });
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

  const primaryProducts = useMemo(
    () => productList.filter((item) => !OUTPUT_TYPES.includes(item.type)),
    []
  );
  const outputProducts = useMemo(
    () => productList.filter((item) => OUTPUT_TYPES.includes(item.type)),
    []
  );
  const isOutputActive = product.type === displayOutput.type;

  const renderHistoryList = (isMobile = false) => {
    return (
      <div className={classNames("h-full flex flex-col", isMobile ? "p-0" : "p-12")}>
        <Button
          variant="outline"
          className="w-full h-[40px] mb-12 text-[#4040ff] border-[#E9E9F0] hover:bg-[#f7f7fb] hover:text-[#4040ff]"
          onClick={createNewChat}
        >
          <PlusIcon size={14} />
          新建对话
        </Button>
        <div className="flex-1 overflow-y-auto no-scrollbar pr-[2px]">
          {sortedConversations.map((item) => {
            const isActive = item.id === currentConversationId;
            const title = item.chatTitle || item.title || "新对话";
            return (
              <Card
                key={item.id}
                className={classNames(
                  "mb-8 cursor-pointer transition-all rounded-[12px] bg-white py-0",
                  isActive
                    ? "ring-[#cfd0ff] bg-[rgba(64,64,255,0.05)]"
                    : "ring-[#ececf2] hover:ring-[#d9daf8] hover:bg-[#fafafe]"
                )}
                onClick={() => handleSelectConversation(item.id)}
              >
                <CardContent className="px-10 py-8">
                  <div className="flex items-start gap-8">
                    <div className="text-[13px] text-[#18181b] font-medium truncate flex-1 min-w-0">
                      {title}
                    </div>
                    <Button
                      variant="ghost"
                      size="icon-xs"
                      className="shrink-0 text-[#a1a1aa] hover:text-[#ef4444] hover:bg-[#fff1f2]"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDeleteConversation(item.id);
                      }}
                      title="删除会话"
                      aria-label="删除会话"
                    >
                      <Trash2Icon size={13} />
                    </Button>
                  </div>
                  <div className="mt-4 flex items-center justify-between gap-8">
                    <span className="text-[11px] text-[#72727d] truncate">
                      {getModeName(item.productType)}
                    </span>
                    <span className="text-[11px] text-[#a1a1aa] shrink-0">
                      {formatHistoryTime(item.updatedAt)}
                    </span>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      </div>
    );
  };

  const renderWelcome = () => {
    return (
      <div
        className="pt-[80px] px-16 flex flex-col items-center w-full min-h-full overflow-y-auto"
        style={{
          background:
            "radial-gradient(ellipse 80% 40% at 50% 0%, rgba(196,196,255,0.18) 0%, transparent 70%)",
        }}
      >
        <div className="flex flex-col items-center">
          <h1
            className="mb-[14px] text-[58px] font-black tracking-[-1.5px] leading-none select-none"
            style={{
              background: "linear-gradient(130deg, #4040ff 0%, #a855f7 50%, #06b6d4 100%)",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent",
              backgroundClip: "text",
              WebkitFontSmoothing: "antialiased",
              MozOsxFontSmoothing: "grayscale",
            }}
          >
            Reactor
          </h1>
          <p className="text-[15px] text-[#71717a] text-center max-w-[420px] leading-[1.75] mb-[32px]">
            AI 智能体平台，一句话完成数据分析、研究调查和内容创作
          </p>
        </div>

        <div className="w-full max-w-[760px] rounded-xl shadow-[0_18px_39px_0_rgba(198,202,240,0.1)]">
          <GeneralInput
            placeholder={product.placeholder}
            showBtn={true}
            size="big"
            disabled={false}
            product={product}
            send={changeInputInfo}
            dbsShow={setDbsShow}
          />
        </div>

        <div className="w-full max-w-[760px] flex items-center gap-[8px] mt-[12px]">
          <div className="relative" ref={outputMenuRef}>
            <div
              className={classNames(
                "h-[36px] px-[16px] cursor-pointer flex items-center gap-[6px] border rounded-[8px] text-[13px] font-medium transition-all duration-200 select-none whitespace-nowrap",
                isOutputActive
                  ? "border-[#4040ff] bg-[rgba(64,64,255,0.07)] text-[#4040ff] shadow-[0_0_0_1px_rgba(64,64,255,0.08)]"
                  : "border-[#E9E9F0] text-[#52525b] hover:border-[#c5c5ff] hover:text-[#4040ff] hover:bg-[rgba(64,64,255,0.02)]",
                outputMenuOpen &&
                  !isOutputActive &&
                  "border-[#c5c5ff] text-[#4040ff] bg-[rgba(64,64,255,0.02)]"
              )}
              onClick={() => {
                if (!isOutputActive) handleSelectProduct(displayOutput);
                setOutputMenuOpen((v) => !v);
              }}
            >
              <i className={`font_family ${displayOutput.img} ${displayOutput.color} text-[14px]`}></i>
              <span>{displayOutput.name}</span>
              <svg
                className={classNames("w-[12px] h-[12px] ml-[1px] transition-transform duration-200", {
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
              <div className="absolute left-0 top-[42px] z-50 w-[220px] bg-white rounded-[12px] border border-[#E9E9F0] shadow-[0_8px_32px_rgba(0,0,0,0.10)] p-[6px] animate-in fade-in slide-in-from-top-1 duration-150">
                {outputProducts.map((item) => {
                  const isSelected = product.type === item.type;
                  return (
                    <div
                      key={item.type}
                      className={classNames(
                        "flex items-start gap-[10px] px-[10px] py-[9px] rounded-[8px] cursor-pointer transition-all duration-150 group/item",
                        isSelected ? "bg-[rgba(64,64,255,0.06)]" : "hover:bg-[#f7f7fb]"
                      )}
                      onClick={() => {
                        handleSelectProduct(item);
                        setOutputMenuOpen(false);
                      }}
                    >
                      <div
                        className={classNames(
                          "w-[28px] h-[28px] rounded-[7px] flex items-center justify-center shrink-0 mt-[1px]",
                          isSelected
                            ? "bg-[rgba(64,64,255,0.1)]"
                            : "bg-[#f4f4f9] group-hover/item:bg-[rgba(64,64,255,0.06)]"
                        )}
                      >
                        <i className={`font_family ${item.img} ${item.color} text-[14px]`}></i>
                      </div>
                      <div className="flex-1 min-w-0">
                        <div
                          className={classNames(
                            "text-[13px] font-medium leading-[20px]",
                            isSelected ? "text-[#4040ff]" : "text-[#18181b]"
                          )}
                        >
                          {item.name}
                        </div>
                        <div className="text-[11px] text-[#a1a1aa] leading-[17px] mt-[1px]">
                          {outputDescMap[item.type]}
                        </div>
                      </div>
                      {isSelected && (
                        <svg
                          className="w-[14px] h-[14px] shrink-0 mt-[6px] text-[#4040ff]"
                          viewBox="0 0 14 14"
                          fill="none"
                        >
                          <path
                            d="M2.5 7L5.5 10L11.5 4"
                            stroke="currentColor"
                            strokeWidth="1.6"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                        </svg>
                      )}
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
                "h-[36px] px-[16px] cursor-pointer flex items-center justify-center gap-[5px] border rounded-[8px] text-[13px] font-medium transition-all duration-200 select-none whitespace-nowrap",
                item.type === product.type
                  ? "border-[#4040ff] bg-[rgba(64,64,255,0.07)] text-[#4040ff] shadow-[0_0_0_1px_rgba(64,64,255,0.08)]"
                  : "border-[#E9E9F0] text-[#52525b] hover:border-[#c5c5ff] hover:text-[#4040ff] hover:bg-[rgba(64,64,255,0.02)]"
              )}
              onClick={() => handleSelectProduct(item)}
            >
              <i className={`font_family ${item.img} ${item.color} text-[14px]`}></i>
              <span>{item.name}</span>
            </div>
          ))}
        </div>

        <div className="mt-[52px] mb-[80px] relative w-full flex flex-col items-center">
          <div
            className={classNames(
              "p-0 w-full overflow-hidden transition-all duration-400 opacity-0 max-h-0",
              {
                "opacity-100 max-h-[60px] mb-[16px]": product.type === "dataAgent",
              }
            )}
          >
            <div className="flex gap-x-[10px] justify-center flex-wrap">
              {chatQustions.map((item, i) => (
                <div
                  key={i}
                  className="text-[#52525B] cursor-pointer border border-[#E9E9F0] rounded-[20px] px-[14px] py-[5px] text-[13px] whitespace-nowrap flex items-center gap-[4px] hover:border-[#4040ff] hover:text-[#4040ff] hover:bg-[rgba(64,64,255,0.04)] transition-all duration-200"
                  onClick={() => toSendMessage(item)}
                >
                  {item.type === 2 && <i className="font_family icon-shendusikao text-[12px]"></i>}
                  {item.label}
                </div>
              ))}
            </div>
          </div>

          <div className="text-center mb-[28px]">
            <div className="flex items-center justify-center gap-[12px] mb-[6px]">
              <div className="w-[40px] h-[1px] bg-gradient-to-r from-transparent to-[rgba(64,64,255,0.25)]"></div>
              <h2 className="text-[20px] font-bold text-[#18181b] tracking-wide">精选案例</h2>
              <div className="w-[40px] h-[1px] bg-gradient-to-l from-transparent to-[rgba(64,64,255,0.25)]"></div>
            </div>
            <p className="text-[13px] text-[#a1a1aa]">和 Genie 一起，让效率飞起来</p>
          </div>

          <div className="flex gap-[20px] flex-wrap justify-center">
            {demoList.map((demo, i) => (
              <CaseCard
                key={i}
                {...demo}
                videoModalOpen={videoModalOpen}
                onOpenVideo={handleOpenVideo}
                onCloseVideo={handleCloseVideo}
              />
            ))}
          </div>
        </div>

        <DataListDrawer show={dbsShow} dbsShow={setDbsShow} showDetail={showDetail}></DataListDrawer>
        {dataShow && (
          <ColsAndDataDrawer
            show={dataShow}
            dataShow={setDataShow}
            modelInfo={curModel}
          ></ColsAndDataDrawer>
        )}
      </div>
    );
  };

  if (!currentConversation) {
    return null;
  }

  return (
    <div className="h-full w-full flex bg-[#fafafd]">
      <aside className="hidden md:flex w-[300px] shrink-0 border-r border-[#ececf2] bg-[#f6f7fb]">
        {renderHistoryList()}
      </aside>

      <Drawer
        title="对话历史"
        placement="left"
        open={historyDrawerOpen}
        onClose={() => setHistoryDrawerOpen(false)}
        width={300}
        rootClassName="md:hidden"
      >
        {renderHistoryList(true)}
      </Drawer>

      <div className="flex-1 min-w-0 h-full flex flex-col">
        <div className="md:hidden px-12 pt-12 pb-4">
          <button
            className="h-[36px] px-10 rounded-[8px] border border-[#E9E9F0] bg-white text-[#52525b] text-[13px] flex items-center gap-[6px]"
            onClick={() => setHistoryDrawerOpen(true)}
          >
            <MenuIcon size={14} />
            历史
          </button>
        </div>

        <div className="flex-1 min-h-0 overflow-auto">
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
  );
});

Home.displayName = "Home";

export default Home;
