import {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ComponentType,
} from "react";
import classNames from "classnames";
import {
  DatabaseZap,
  MessageSquarePlus,
  MessagesSquare,
  WandSparkles,
} from "lucide-react";
import ChatView from "@/components/ChatView";
import WorkspaceMRag from "@/pages/WorkspaceMRag";
import WorkspaceImageGeneration from "@/pages/WorkspaceImageGeneration";
import { defaultProduct, productList } from "@/utils/constants";
import {
  createSessionId,
  getUniqId,
  peekSessionId,
  setSessionId,
} from "@/utils";
import {
  conversationHistoryApi,
  roleLibraryApi,
  visitorApi,
  type VisitorBootstrapInfo,
  type ConversationSessionItem,
  type FixRoleItem,
} from "@/services/agentConversation";
import {
  hydrateConversationFromReplayFrames,
  isHistoryDetailEmpty,
} from "@/utils/conversationHistory";
import { deriveConversationMetaFromInput } from "./homeState";
import { resolveInitialSessionId } from "./sessionBootstrap";
import { useRecentSessions } from "./useRecentSessions";
import {
  resolveVisitorWorkspaceStage,
  shouldBootstrapVisitor,
  shouldLoadVisitorProtectedData,
} from "./visitorGate";
import VisitorBootstrapScreen from "./VisitorBootstrapScreen";
import VisitorLoginGate from "./VisitorLoginGate";
import WelcomeView from "./WelcomeView";

type HomeProps = Record<string, never>;

type SidebarView = "chat" | "mrag" | "image-generation";

type InitialState = {
  productType: string;
};

const OUTPUT_TYPES = ["html", "docs", "ppt", "table"];
const EMPTY_INPUT: CHAT.TInputInfo = {
  message: "",
  deepThink: false,
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
    sessionId: partial.sessionId || createSessionId(),
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

const Home: ReactorType.FC<HomeProps> = memo(() => {
  const initialRef = useRef<InitialState>(createInitialState());
  const initializedVisitorIdRef = useRef<string | null>(null);
  const [fixRoles, setFixRoles] = useState<CHAT.FixRole[]>([]);
  const {
    recentSessions,
    recentSessionsLoading,
    refreshRecentSessions,
  } = useRecentSessions();
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
  const [visitorBootstrap, setVisitorBootstrap] = useState<VisitorBootstrapInfo>();
  const [visitorBootstrapLoaded, setVisitorBootstrapLoaded] = useState(false);
  const [visitorBootstrapLoading, setVisitorBootstrapLoading] = useState(false);
  const [visitorNamingLoading, setVisitorNamingLoading] = useState(false);
  const [conversationBootstrapLoading, setConversationBootstrapLoading] =
    useState(false);

  const visitorWorkspaceStage = resolveVisitorWorkspaceStage({
    bootstrapLoaded: visitorBootstrapLoaded,
    bootstrapLoading: visitorBootstrapLoading,
    visitorNamed: visitorBootstrap?.named,
  });
  const visitorProtectedDataReady = shouldLoadVisitorProtectedData({
    bootstrapLoaded: visitorBootstrapLoaded,
    bootstrapLoading: visitorBootstrapLoading,
    visitorNamed: visitorBootstrap?.named,
  });

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

  const contentContainerClassName =
    activeView === "chat" && canRenderChatView
      ? "min-h-0 flex-1 overflow-hidden"
      : "min-h-0 flex-1 overflow-auto";

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
    if (!shouldBootstrapVisitor({
      bootstrapLoaded: visitorBootstrapLoaded,
      bootstrapLoading: visitorBootstrapLoading,
    })) {
      return;
    }
    setVisitorBootstrapLoading(true);
    visitorApi
      .bootstrap()
      .then((info) => {
        setVisitorBootstrap(info);
        setVisitorBootstrapLoaded(true);
      })
      .catch((error) => {
        console.error("加载访客状态失败", error);
      })
      .finally(() => {
        setVisitorBootstrapLoading(false);
      });
  }, [visitorBootstrapLoaded, visitorBootstrapLoading]);

  useEffect(() => {
    if (!visitorProtectedDataReady) {
      initializedVisitorIdRef.current = null;
      return;
    }
    const visitorId = visitorBootstrap?.visitorId;
    if (!visitorId || initializedVisitorIdRef.current === visitorId) {
      return;
    }

    let disposed = false;
    initializedVisitorIdRef.current = visitorId;
    setConversationBootstrapLoading(true);

    refreshRecentSessions(true)
      .then((sessions) => {
        if (disposed) {
          return;
        }

        const initialSessionId = resolveInitialSessionId({
          recentSessions: sessions,
          storedSessionId: peekSessionId(),
        });

        if (!initialSessionId) {
          setCurrentConversation(
            createConversation({
              productType: initialRef.current.productType,
            })
          );
          return;
        }

        return conversationHistoryApi
          .getSessionDetail(initialSessionId)
          .then((detail) => {
            if (disposed || !detail || isHistoryDetailEmpty(detail)) {
              return;
            }
            setCurrentConversation(hydrateConversationFromReplayFrames(detail));
          })
          .catch((error) => {
            console.error("加载默认会话详情失败", error);
            if (disposed) {
              return;
            }
            setCurrentConversation(
              createConversation({
                productType: initialRef.current.productType,
              })
            );
          });
      })
      .finally(() => {
        if (!disposed) {
          setConversationBootstrapLoading(false);
        }
      });

    return () => {
      disposed = true;
    };
  }, [
    refreshRecentSessions,
    visitorBootstrap?.visitorId,
    visitorProtectedDataReady,
  ]);

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
      const nextSessionId = override?.sessionId || createSessionId();
      setActiveView("chat");
      setCurrentConversation(
        createConversation({
          sessionId: nextSessionId,
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

  const handleSelectRecentSession = useCallback(
    (session: ConversationSessionItem) => {
      conversationHistoryApi
        .getSessionDetail(session.sessionId)
        .then((detail) => {
          if (!detail || isHistoryDetailEmpty(detail)) {
            return;
          }
          setCurrentConversation(hydrateConversationFromReplayFrames(detail));
          setActiveView("chat");
          resetInput();
        })
        .catch((error) => {
          console.error("加载历史会话详情失败", error);
        });
    },
    [resetInput]
  );

  useEffect(() => {
    if (conversationBootstrapLoading) {
      return;
    }
    setSessionId(currentConversation.sessionId);
  }, [conversationBootstrapLoading, currentConversation.sessionId]);

  const handleSubmitVisitorName = useCallback((username: string) => {
    setVisitorNamingLoading(true);
    visitorApi
      .naming(username.trim())
      .then((info) => {
        setVisitorBootstrap(info);
      })
      .catch((error) => {
        console.error("提交访客用户名失败", error);
      })
      .finally(() => {
        setVisitorNamingLoading(false);
      });
  }, []);

  const changeInputInfo = useCallback(
    (info: CHAT.TInputInfo) => {
      const nextMeta = deriveConversationMetaFromInput(info, {
        productType: product.type,
        currentRole: currentConversationRole,
      });

      updateCurrentConversationMeta(nextMeta);

      setInputInfo({
        ...info,
        outputStyle: nextMeta.productType,
        deepThink: nextMeta.deepThink,
        aiAgentId: nextMeta.productType === "chat"
          ? currentConversationRole?.agentId
          : undefined,
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
    (query: { label: string; type: number }) => {
      changeInputInfo({
        message: query.label,
        outputStyle: "dataAgent",
        deepThink: query.type === 2,
      });
    },
    [changeInputInfo]
  );

  if (visitorWorkspaceStage === "bootstrapping") {
    return <VisitorBootstrapScreen />;
  }

  if (visitorWorkspaceStage === "ready" && conversationBootstrapLoading) {
    return <VisitorBootstrapScreen />;
  }

  if (visitorWorkspaceStage === "naming") {
    return (
      <VisitorLoginGate
        loading={visitorNamingLoading}
        onSubmit={handleSubmitVisitorName}
      />
    );
  }

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

        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
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
                      : "当前工作台：绘图智能体"}
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

          <div className={contentContainerClassName}>
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
              <WelcomeView
                currentConversation={currentConversation}
                product={product}
                displayOutput={displayOutput}
                currentConversationRole={currentConversationRole}
                fixRoles={fixRoles}
                recentSessions={recentSessions}
                recentSessionsLoading={recentSessionsLoading}
                visitorUsername={visitorBootstrap?.username}
                videoModalOpen={videoModalOpen}
                onSelectionChange={handleInputSelectionChange}
                onRoleSelect={handleRoleSelect}
                onSend={changeInputInfo}
                onSelectRecentSession={handleSelectRecentSession}
                onSendQuestion={toSendMessage}
                onOpenVideo={setVideoModalOpen}
                onCloseVideo={() => setVideoModalOpen(undefined)}
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
