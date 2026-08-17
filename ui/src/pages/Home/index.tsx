import {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { AnimatePresence, motion } from "motion/react";
import { Menu } from "lucide-react";
import ChatView from "@/components/ChatView";
import { DURATION, EASE_OUT, useMotionConfig } from "@/lib/motion";
import WorkspaceMRag from "@/pages/WorkspaceMRag";
import WorkspaceImageGeneration from "@/pages/WorkspaceImageGeneration";
import WorkspaceSop from "@/pages/WorkspaceSop";
import SubAgentAdmin from "@/pages/SubAgentAdmin";
import ModelAdmin from "@/pages/ModelAdmin";
import CapabilityLibrary from "@/pages/CapabilityLibrary";
import FeaturedConversations from "@/pages/FeaturedConversations";
import {
  GENERIC_TASK_PRODUCT,
  getProductByType,
  isOutputProductType,
  toRequestOutputStyle,
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
  visitorApi,
  type VisitorBootstrapInfo,
  type ConversationSessionItem,
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
import { readActiveRun } from "@/utils/activeRunStorage";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import {
  deriveConversationMetaFromInput,
  mergeLocalRecentConversations,
  mergeRecentSessions,
  shouldApplyConversationToView,
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
import type { PanelItemType } from "@/components/ActionPanel";
import {
  workspaceFileKey,
  type WorkspaceFileItem,
} from "@/components/ActionView/workspaceFiles";
import {
  buildFeaturedConversationFormState,
  canFeatureConversationSession,
  type FeaturedConversationFormState,
  toFeaturedConversationUpsertPayload,
  validateFeaturedConversationForm,
} from "./featuredConversationAdminModel";

type HomeProps = Record<string, never>;

type SidebarView =
  | "chat"
  | "mrag"
  | "image-generation"
  | "sop"
  | "sub-agents"
  | "models"
  | "capabilities"
  | "featured";

type InitialState = {
  productType: string;
};

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
  role?: CHAT.FixRole | CHAT.ConversationRole | null
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
    productType: partial.productType || GENERIC_TASK_PRODUCT.type,
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
  return {productType: GENERIC_TASK_PRODUCT.type,};
};

const Home: ReactorType.FC<HomeProps> = memo(() => {
  // Home 持有跨页面的会话壳状态：当前 conversation 负责聊天，侧栏/工作区
  // 状态负责视图切换，访客 bootstrap 则决定哪些受保护数据可以开始加载。
  const initialRef = useRef<InitialState>(createInitialState());
  const initializedVisitorIdRef = useRef<string | null>(null);
  const conversationBootstrapResolvedRef = useRef(false);
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
  const [sidebarPanel, setSidebarPanel] = useState<"sessions" | "task-files">(
    "sessions"
  );
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [workspaceImmersive, setWorkspaceImmersive] = useState(false);
  const [workspaceTaskList, setWorkspaceTaskList] = useState<PanelItemType[]>(
    []
  );
  const [selectedTaskFileKey, setSelectedTaskFileKey] = useState("");
  type ChatViewApi = {
    openFile: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
  };
  const chatViewApiRef = useRef<ChatViewApi | null>(null);
  const [featuredEntryId, setFeaturedEntryId] = useState("");
  const [inputInfo, setInputInfo] = useState<CHAT.TInputInfo>(EMPTY_INPUT);
  const [product, setProduct] = useState(() => getProductByType(initialRef.current.productType));
  const [displayOutput, setDisplayOutput] = useState<CHAT.Product>();
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

  const closeMobileSidebar = useCallback(() => {
    setMobileSidebarOpen(false);
  }, []);

  useEffect(() => {
    if (!mobileSidebarOpen) {
      return;
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMobileSidebarOpen(false);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [mobileSidebarOpen]);

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
  const { reduce: reduceMotion } = useMotionConfig();
  const viewFadeDuration = reduceMotion ? DURATION.reduced : 0.22;

  const contentContainerClassName =
    activeView === "chat" && canRenderChatView
      ? "min-h-0 flex-1 overflow-hidden"
      : activeView === "mrag" ||
          activeView === "image-generation" ||
          activeView === "sop" ||
          activeView === "sub-agents" ||
          activeView === "models" ||
          activeView === "capabilities" ||
          activeView === "featured"
        ? "min-h-0 flex-1 overflow-hidden"
        : "min-h-0 flex-1 overflow-auto";

  const loadFeaturedCards = useCallback(async () => {
    // 精品对话属于首页附属内容，单独维护失败边界，不影响当前会话主链路。
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
    // bootstrap 只允许一次在途请求；加载态由状态机控制，避免 effect 依赖变化
    // 时重复初始化访客身份。
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

    // 访客身份确认后才加载会话列表。disposed 保护异步结果，防止组件卸载或
    // 身份切换后，旧请求把 currentConversation 写回新的页面状态。
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
          // 活动 run 与普通会话指针都保存在当前 tab；活动 run 优先，避免首屏
          // 临时会话 ID 覆盖刷新前仍在执行的会话。
          storedSessionId: readActiveRun()?.sessionId || peekSessionId(),
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
          conversationBootstrapResolvedRef.current = true;
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
    // 输出格式已下线：历史 html/docs/ppt/table 会话归一为通用任务
    const raw = getProductByType(currentConversation.productType);
    const matched = isOutputProductType(raw.type) ? GENERIC_TASK_PRODUCT : raw;
    setProduct((prev) => (prev.type === matched.type ? prev : matched));
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
    (conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      const nextState = {
        ...nextConversation,
        updatedAt: Date.now(),
      };
      // ChatView 通过 ID 回写草稿；只有当前会话接收更新，历史会话则更新本地
      // 最近列表，避免切换会话期间的流式事件覆盖当前输入。
      // 后台流式更新只刷新本地缓存；仅当前展示的会话才写入主视图，避免其它会话活跃时界面被切走。
      upsertLocalRecentSession(nextState);
      setCurrentConversation((prev) =>
        shouldApplyConversationToView(prev.id, conversationId)
          ? nextState
          : prev
      );
    },
    [upsertLocalRecentSession]
  );

  const createNewChat = useCallback(
    (override?: Partial<CHAT.ConversationHistory>) => {
      // 创建新会话同时清空输入、任务文件和视图壳状态；override 只用于恢复
      // 已存在的 session 元数据，默认路径始终生成新的 sessionId。
      const nextSessionId = override?.sessionId || createSessionId();
      const defaultStructuredProduct = getProductByType(initialRef.current.productType);
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
      // 先切换壳状态，再异步加载详情；本地草稿优先，避免已在内存中的流式会话
      // 被历史接口返回的旧快照覆盖。
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
    if (
      conversationBootstrapLoading ||
      !conversationBootstrapResolvedRef.current
    ) {
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
        outputStyle: info.outputStyle,
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
      // 输出格式已下线，结构化类型强制回落通用任务
      const resolved = isOutputProductType(nextProduct.type)
        ? GENERIC_TASK_PRODUCT
        : nextProduct;
      setProduct(resolved);

      updateCurrentConversationMeta({
        productType: resolved.type,
        deepThink:
          resolved.type === "chat" || resolved.type === "dataAgent"
            ? false
            : nextDeepThink,
        role:
          resolved.type === "chat"
            ? currentConversation.role || toConversationRole(defaultFixRole)
            : null,
      });
    },
    [currentConversation.role, defaultFixRole, updateCurrentConversationMeta]
  );

  const handleRoleSelect = useCallback(
    (role: CHAT.FixRole) => {
      void role;
      const defaultStructuredProduct = getProductByType(initialRef.current.productType);

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

      const nextProduct = isOutputProductType(defaultStructuredProduct.type)
        ? GENERIC_TASK_PRODUCT
        : defaultStructuredProduct;
      updateCurrentConversationMeta({
        productType: nextProduct.type,
        deepThink: false,
        role: null,
      });
      setProduct(nextProduct);
      setActiveView("chat");
    },
    [createNewChat, currentConversation, updateCurrentConversationMeta]
  );

  const toSendMessage = useCallback(
    (query: SuggestedQuestion) => {
      changeInputInfo({
        message: query.label,
        outputStyle: toRequestOutputStyle(product.type),
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

  const sidebarSharedProps = {
    activeView,
    recentSessions: displayedRecentSessions,
    recentSessionsLoading,
    selectedSessionId: currentConversation.sessionId,
    visitorUsername: visitorBootstrap?.username,
    sidebarPanel,
    taskList: workspaceTaskList,
    selectedTaskFileKey,
    onNewChat: () => {
      setSidebarPanel("sessions");
      setSelectedTaskFileKey("");
      setWorkspaceImmersive(false);
      closeMobileSidebar();
      createNewChat();
    },
    onSelectSession: (session: ConversationSessionItem) => {
      setSidebarPanel("sessions");
      setSelectedTaskFileKey("");
      setWorkspaceImmersive(false);
      closeMobileSidebar();
      handleSelectRecentSession(session);
    },
    onChangeView: (view: SidebarView) => {
      if (view === "featured") {
        setFeaturedEntryId("");
      }
      setSidebarPanel("sessions");
      setWorkspaceImmersive(false);
      closeMobileSidebar();
      setActiveView(view);
    },
    onManageFeaturedConversation: handleOpenFeaturedAdmin,
    onOpenTaskFiles: () => {
      if (activeView !== "chat") {
        setActiveView("chat");
      }
      setWorkspaceImmersive(false);
      setSidebarPanel("task-files");
    },
    onCloseTaskFiles: () => setSidebarPanel("sessions"),
    onSelectTaskFile: (file: WorkspaceFileItem) => {
      setSelectedTaskFileKey(workspaceFileKey(file));
      chatViewApiRef.current?.openFile(file);
      closeMobileSidebar();
    },
    onRefreshTaskFiles: () => {
      setWorkspaceTaskList((prev) => [...prev]);
    },
  } as const;

  return (
    <div className="h-full w-full bg-[var(--page-gradient)] text-foreground">
      <div className="flex h-full w-full">
        <div
          className={
            workspaceImmersive
              ? "w-0 min-w-0 overflow-hidden opacity-0 pointer-events-none transition-[width,opacity] duration-300"
              : "hidden h-full w-[var(--chat-sidebar-width)] shrink-0 transition-[width,opacity] duration-300 lg:block"
          }
        >
          <ConversationSidebar {...sidebarSharedProps} />
        </div>

        {!workspaceImmersive && mobileSidebarOpen ? (
          <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true">
            <button
              type="button"
              className="absolute inset-0 bg-black/35 supports-backdrop-filter:backdrop-blur-[2px]"
              aria-label="关闭侧边栏遮罩"
              onClick={closeMobileSidebar}
            />
            <div className="absolute inset-y-0 left-0 flex w-[min(86vw,var(--chat-sidebar-width))] max-w-full shadow-2xl">
              <ConversationSidebar
                {...sidebarSharedProps}
                onRequestClose={closeMobileSidebar}
              />
            </div>
          </div>
        ) : null}

        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          {!workspaceImmersive ? (
            <div className="flex h-12 shrink-0 items-center gap-2 border-b border-[var(--chat-border)] bg-[var(--chat-nav)]/90 px-3 lg:hidden">
              <button
                type="button"
                onClick={() => setMobileSidebarOpen(true)}
                className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-[var(--chat-text-soft)] transition-colors hover:bg-black/5 hover:text-[var(--chat-text)]"
                aria-label="打开侧边栏"
              >
                <Menu className="h-5 w-5" />
              </button>
              <div className="min-w-0 flex-1 truncate text-[15px] font-semibold tracking-[-0.01em] text-[var(--chat-text)]">
                Reactor
              </div>
              <button
                type="button"
                onClick={() => {
                  setSidebarPanel("sessions");
                  setSelectedTaskFileKey("");
                  setWorkspaceImmersive(false);
                  createNewChat();
                }}
                className="rounded-lg px-2.5 py-1.5 text-[13px] font-medium text-[var(--chat-text-soft)] transition-colors hover:bg-black/5 hover:text-[var(--chat-text)]"
              >
                新建
              </button>
            </div>
          ) : null}
          <div className={contentContainerClassName}>
            {activeView === "mrag" ? (
              <WorkspaceMRag embedded />
            ) : activeView === "image-generation" ? (
              <WorkspaceImageGeneration embedded />
            ) : activeView === "sop" ? (
              <WorkspaceSop embedded />
            ) : activeView === "sub-agents" ? (
              <SubAgentAdmin embedded />
            ) : activeView === "models" ? (
              <ModelAdmin embedded />
            ) : activeView === "capabilities" ? (
              <CapabilityLibrary embedded />
            ) : activeView === "featured" ? (
              <FeaturedConversations
                embedded
                initialFeaturedId={featuredEntryId}
              />
            ) : (
              <AnimatePresence mode="wait" initial={false}>
                {canRenderChatView ? (
                  <motion.div
                    key="chat"
                    className="h-full min-h-0 w-full"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{
                      duration: viewFadeDuration,
                      ease: EASE_OUT
                    }}
                  >
                    <ChatView
                      inputInfo={inputInfo}
                      product={product}
                      conversation={currentConversation}
                      chatRoles={fixRoles}
                      onConversationChange={updateConversation}
                      onRoleSelect={handleRoleSelect}
                      onInputConsumed={onInputConsumed}
                      onTaskListChange={setWorkspaceTaskList}
                      onRegisterApi={(api) => {
                        chatViewApiRef.current = api;
                      }}
                      onOpenTaskFiles={() => {
                        setWorkspaceImmersive(false);
                        setSidebarPanel("task-files");
                      }}
                      onFocusModeChange={setWorkspaceImmersive}
                    />
                  </motion.div>
                ) : (
                  <motion.div
                    key="welcome"
                    className="h-full min-h-0 w-full"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{
                      duration: viewFadeDuration,
                      ease: EASE_OUT
                    }}
                  >
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
                  </motion.div>
                )}
              </AnimatePresence>
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
