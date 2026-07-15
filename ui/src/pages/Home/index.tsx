import {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import ChatView from "@/components/ChatView";
import WorkspaceMRag from "@/pages/WorkspaceMRag";
import WorkspaceImageGeneration from "@/pages/WorkspaceImageGeneration";
import FeaturedConversations from "@/pages/FeaturedConversations";
import {
  defaultProduct,
  productList,
  type SuggestedQuestion,
} from "@/utils/constants";
import {
  createSessionId,
  getUniqId,
  peekSessionId,
  setSessionId,
  showMessage,
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
  featuredConversationApi,
  type FeaturedConversationCard,
} from "@/services/featuredConversation";
import {
  featuredConversationAdminApi,
  type FeaturedConversationAdminRecord,
} from "@/services/featuredConversationAdmin";
import {
  hydrateConversationFromReplayFrames,
  isHistoryDetailEmpty,
} from "@/utils/conversationHistory";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import {
  deriveConversationMetaFromInput,
  mergeLocalRecentConversations,
  mergeRecentSessions,
  toRecentSessionItem,
} from "./homeState";
import FeaturedConversationAdminPanel from "./FeaturedConversationAdminPanel";
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
import ConversationSidebar from "./ConversationSidebar";
import {
  buildFeaturedConversationFormState,
  canFeatureConversationSession,
  type FeaturedConversationFormState,
  toFeaturedConversationUpsertPayload,
  validateFeaturedConversationForm,
} from "./featuredConversationAdminModel";

type HomeProps = Record<string, never>;

type SidebarView = "chat" | "mrag" | "image-generation" | "featured";

type InitialState = {
  productType: string;
};

const OUTPUT_TYPES = ["html", "docs", "ppt", "table"];
const EMPTY_INPUT: CHAT.TInputInfo = {
  message: "",
  deepThink: false,
};
const EMPTY_FEATURED_FORM: FeaturedConversationFormState = {
  sessionId: "",
  title: "",
  summary: "",
  coverUrl: "",
  tagsText: "",
  sortOrder: "100",
  operator: "ui-featured-manager",
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
  return {productType: initialProduct.type,};
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
  const [localRecentConversations, setLocalRecentConversations] = useState<
    CHAT.ConversationHistory[]
  >([]);
  const [activeView, setActiveView] = useState<SidebarView>("chat");
  const [featuredEntryId, setFeaturedEntryId] = useState("");
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
  const [featuredCards, setFeaturedCards] = useState<FeaturedConversationCard[]>(
    []
  );
  const [featuredAdminDialogOpen, setFeaturedAdminDialogOpen] = useState(false);
  const [featuredAdminLoading, setFeaturedAdminLoading] = useState(false);
  const [featuredAdminSubmitting, setFeaturedAdminSubmitting] = useState(false);
  const [featuredAdminTargetSession, setFeaturedAdminTargetSession] =
    useState<ConversationSessionItem | null>(null);
  const [featuredAdminRecord, setFeaturedAdminRecord] =
    useState<FeaturedConversationAdminRecord | null>(null);
  const [featuredAdminForm, setFeaturedAdminForm] =
    useState<FeaturedConversationFormState>(EMPTY_FEATURED_FORM);
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
      createConversation({productType: initialRef.current.productType,})
    );

  const currentConversationRole = useMemo(() => {
    if (currentConversation.productType !== "chat") {
      return null;
    }
    return currentConversation.role || toConversationRole(defaultFixRole);
  }, [currentConversation.productType, currentConversation.role, defaultFixRole]);

  const displayedRecentSessions = useMemo(
    () =>
      mergeRecentSessions(
        recentSessions,
        localRecentConversations
          .map(toRecentSessionItem)
          .filter((item): item is ConversationSessionItem => Boolean(item))
      ),
    [localRecentConversations, recentSessions]
  );

  const canRenderChatView =
    activeView === "chat" &&
    (hasConversationContent(currentConversation) || inputInfo.message.length > 0);

  const contentContainerClassName =
    activeView === "chat" && canRenderChatView
      ? "min-h-0 flex-1 overflow-hidden"
      : activeView === "mrag" ||
          activeView === "image-generation" ||
          activeView === "featured"
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

  const loadFeaturedCards = useCallback(async () => {
    try {
      const cards = await featuredConversationApi.listHome(6);
      setFeaturedCards(cards || []);
    } catch (error) {
      console.error("加载精品对话失败", error);
      setFeaturedCards([]);
    }
  }, []);

  useEffect(() => {
    void loadFeaturedCards();
  }, [loadFeaturedCards]);

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
            createConversation({productType: initialRef.current.productType,})
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
              createConversation({productType: initialRef.current.productType,})
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

  const upsertLocalRecentSession = useCallback(
    (conversation: CHAT.ConversationHistory) => {
      if (!conversation.sessionId) {
        return;
      }

      setLocalRecentConversations((prev) =>
        mergeLocalRecentConversations(prev, conversation)
      );
    },
    []
  );

  const updateConversation = useCallback(
    (_conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      const nextState = {
        ...nextConversation,
        updatedAt: Date.now(),
      };
      setCurrentConversation(nextState);
      upsertLocalRecentSession(nextState);
    },
    [upsertLocalRecentSession]
  );

  const createNewChat = useCallback(
    (override?: Partial<CHAT.ConversationHistory>) => {
      const nextSessionId = override?.sessionId || createSessionId();
      const defaultStructuredProduct =
        productList.find((item) => item.type === initialRef.current.productType) ??
        defaultProduct;
      const nextProductType =
        override?.productType ||
        (product.type === "chat" ? defaultStructuredProduct.type : product.type);
      setActiveView("chat");
      const nextConversation = createConversation({
        sessionId: nextSessionId,
        productType: nextProductType,
        deepThink:
          nextProductType === "chat" || nextProductType === "dataAgent"
            ? false
            : override?.deepThink ?? false,
        role:
          override?.role ||
          (nextProductType === "chat"
            ? toConversationRole(defaultFixRole)
            : null),
        ...override,
      });
      setCurrentConversation(nextConversation);
      upsertLocalRecentSession(nextConversation);
      resetInput();
    },
    [defaultFixRole, product.type, resetInput, upsertLocalRecentSession]
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
      const localConversation = localRecentConversations.find(
        (item) => item.sessionId === session.sessionId
      );
      if (localConversation) {
        setCurrentConversation(localConversation);
        setActiveView("chat");
        resetInput();
        return;
      }

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
    [localRecentConversations, resetInput]
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
      void role;
      const defaultStructuredProduct =
        productList.find((item) => item.type === initialRef.current.productType) ??
        defaultProduct;

      if (
        currentConversation.productType === "chat" &&
        hasConversationContent(currentConversation)
      ) {
        createNewChat({
          productType: defaultStructuredProduct.type,
          deepThink: false,
          role: null,
        });
        return;
      }

      updateCurrentConversationMeta({
        productType: defaultStructuredProduct.type,
        deepThink: false,
        role: null,
      });
      setProduct(defaultStructuredProduct);
      if (OUTPUT_TYPES.includes(defaultStructuredProduct.type)) {
        setDisplayOutput(defaultStructuredProduct);
      }
      setActiveView("chat");
    },
    [createNewChat, currentConversation, updateCurrentConversationMeta]
  );

  const toSendMessage = useCallback(
    (query: SuggestedQuestion) => {
      changeInputInfo({
        message: query.label,
        outputStyle: product.type,
        deepThink: Boolean(query.deepThink),
      });
    },
    [changeInputInfo, product.type]
  );

  const syncFeaturedAdminRecord = useCallback(
    async (session: ConversationSessionItem, operator?: string) => {
      const page = await featuredConversationAdminApi.queryList({
        sessionId: session.sessionId,
        pageNo: 1,
        pageSize: 1,
      });
      const record = page.list?.[0] || null;
      setFeaturedAdminRecord(record);
      setFeaturedAdminForm(
        buildFeaturedConversationFormState({
          session,
          existingRecord: record,
          operator,
        })
      );
      return record;
    },
    []
  );

  const resetFeaturedAdminDialog = useCallback(() => {
    setFeaturedAdminDialogOpen(false);
    setFeaturedAdminLoading(false);
    setFeaturedAdminSubmitting(false);
    setFeaturedAdminTargetSession(null);
    setFeaturedAdminRecord(null);
    setFeaturedAdminForm((prev) => ({
      ...EMPTY_FEATURED_FORM,
      operator: prev.operator || EMPTY_FEATURED_FORM.operator,
    }));
  }, []);

  const handleFeaturedAdminFormChange = useCallback(
    (patch: Partial<FeaturedConversationFormState>) => {
      setFeaturedAdminForm((prev) => ({
        ...prev,
        ...patch,
      }));
    },
    []
  );

  const handleOpenFeaturedAdmin = useCallback(
    (session: ConversationSessionItem) => {
      if (!canFeatureConversationSession(session)) {
        showMessage()?.error("请先让该会话至少产生一轮内容，再设为精品");
        return;
      }

      const operator = visitorBootstrap?.username || featuredAdminForm.operator;
      setFeaturedAdminDialogOpen(true);
      setFeaturedAdminLoading(true);
      setFeaturedAdminTargetSession(session);
      setFeaturedAdminRecord(null);
      setFeaturedAdminForm(
        buildFeaturedConversationFormState({
          session,
          operator,
        })
      );

      syncFeaturedAdminRecord(session, operator)
        .catch((error) => {
          console.error("加载精品对话配置失败", error);
          showMessage()?.error("加载精品对话配置失败");
        })
        .finally(() => {
          setFeaturedAdminLoading(false);
        });
    },
    [featuredAdminForm.operator, syncFeaturedAdminRecord, visitorBootstrap?.username]
  );

  const handleSaveFeaturedDraft = useCallback(
    async (publishAfterSave: boolean) => {
      if (!featuredAdminTargetSession) {
        return;
      }

      const validationError = validateFeaturedConversationForm(featuredAdminForm);
      if (validationError) {
        showMessage()?.error(validationError);
        return;
      }

      setFeaturedAdminSubmitting(true);
      try {
        const payload = toFeaturedConversationUpsertPayload(
          featuredAdminForm,
          featuredAdminRecord
        );

        if (featuredAdminRecord) {
          await featuredConversationAdminApi.update(payload);
        } else {
          await featuredConversationAdminApi.create(payload);
        }

        let latestRecord = await syncFeaturedAdminRecord(
          featuredAdminTargetSession,
          featuredAdminForm.operator
        );

        if (publishAfterSave) {
          if (!latestRecord?.featuredId) {
            throw new Error("未查询到新创建的精品记录");
          }
          if (latestRecord.status?.toUpperCase() !== "ONLINE") {
            await featuredConversationAdminApi.online(
              latestRecord.featuredId,
              featuredAdminForm.operator.trim()
            );
            latestRecord = await syncFeaturedAdminRecord(
              featuredAdminTargetSession,
              featuredAdminForm.operator
            );
          }
          showMessage()?.success("精品对话已上线");
        } else {
          showMessage()?.success(
            featuredAdminRecord ? "精品对话已更新" : "精品草稿已创建"
          );
        }

        await loadFeaturedCards();
      } catch (error) {
        console.error("保存精品对话失败", error);
      } finally {
        setFeaturedAdminSubmitting(false);
      }
    },
    [
      featuredAdminForm,
      featuredAdminRecord,
      featuredAdminTargetSession,
      loadFeaturedCards,
      syncFeaturedAdminRecord,
    ]
  );

  const handleToggleFeaturedStatus = useCallback(async () => {
    if (!featuredAdminTargetSession || !featuredAdminRecord?.featuredId) {
      return;
    }

    const operator = featuredAdminForm.operator.trim();
    if (!operator) {
      showMessage()?.error("请填写操作人");
      return;
    }

    setFeaturedAdminSubmitting(true);
    try {
      if (featuredAdminRecord.status?.toUpperCase() === "ONLINE") {
        await featuredConversationAdminApi.offline(
          featuredAdminRecord.featuredId,
          operator
        );
        showMessage()?.success("精品对话已下线");
      } else {
        await featuredConversationAdminApi.online(
          featuredAdminRecord.featuredId,
          operator
        );
        showMessage()?.success("精品对话已上线");
      }

      await syncFeaturedAdminRecord(featuredAdminTargetSession, operator);
      await loadFeaturedCards();
    } catch (error) {
      console.error("切换精品对话状态失败", error);
    } finally {
      setFeaturedAdminSubmitting(false);
    }
  }, [
    featuredAdminForm.operator,
    featuredAdminRecord,
    featuredAdminTargetSession,
    loadFeaturedCards,
    syncFeaturedAdminRecord,
  ]);

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
        <ConversationSidebar
          activeView={activeView}
          recentSessions={displayedRecentSessions}
          recentSessionsLoading={recentSessionsLoading}
          selectedSessionId={currentConversation.sessionId}
          visitorUsername={visitorBootstrap?.username}
          onNewChat={createNewChat}
          onSelectSession={handleSelectRecentSession}
          onChangeView={(view) => {
            if (view === "featured") {
              setFeaturedEntryId("");
            }
            setActiveView(view);
          }}
          onManageFeaturedConversation={handleOpenFeaturedAdmin}
        />

        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <div className={contentContainerClassName}>
            {activeView === "mrag" ? (
              <WorkspaceMRag embedded />
            ) : activeView === "image-generation" ? (
              <WorkspaceImageGeneration embedded />
            ) : activeView === "featured" ? (
              <FeaturedConversations
                embedded
                initialFeaturedId={featuredEntryId}
              />
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
                visitorUsername={visitorBootstrap?.username}
                videoModalOpen={videoModalOpen}
                onSelectionChange={handleInputSelectionChange}
                onRoleSelect={handleRoleSelect}
                onSend={changeInputInfo}
                onSendQuestion={toSendMessage}
                onOpenVideo={setVideoModalOpen}
                onCloseVideo={() => setVideoModalOpen(undefined)}
                featuredCards={featuredCards}
                onOpenFeaturedConversations={() => {
                  setFeaturedEntryId("");
                  setActiveView("featured");
                }}
                onOpenFeaturedDetail={(featuredId) => {
                  setFeaturedEntryId(featuredId);
                  setActiveView("featured");
                }}
              />
            )}
          </div>
        </div>
      </div>
      <Dialog
        open={featuredAdminDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            resetFeaturedAdminDialog();
          } else {
            setFeaturedAdminDialogOpen(true);
          }
        }}
      >
        <DialogContent
          className="sm:max-w-[760px]"
          showCloseButton={!featuredAdminSubmitting}
        >
          {featuredAdminTargetSession ? (
            <FeaturedConversationAdminPanel
              session={featuredAdminTargetSession}
              form={featuredAdminForm}
              record={featuredAdminRecord}
              loading={featuredAdminLoading}
              submitting={featuredAdminSubmitting}
              onChange={handleFeaturedAdminFormChange}
              onClose={resetFeaturedAdminDialog}
              onSaveDraft={() => {
                void handleSaveFeaturedDraft(false);
              }}
              onPublish={() => {
                if (featuredAdminRecord) {
                  void handleToggleFeaturedStatus();
                } else {
                  void handleSaveFeaturedDraft(true);
                }
              }}
            />
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
});

Home.displayName = "Home";

export default Home;
