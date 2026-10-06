import {
  lazy,
  memo,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import { AnimatePresence, motion } from "motion/react";
import { Menu } from "lucide-react";
import ChatView from "@/components/ChatView";
import Loading from "@/components/ActionPanel/Loading";
import { DURATION, EASE_OUT, useMotionConfig } from "@/lib/motion";
import {
  GENERIC_TASK_PRODUCT,
  getProductByType,
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
  type ConversationRunReplay,
  type ConversationSessionItem,
} from "@/services/agentConversation";
import { authApi } from "@/services/auth";
import { useAuth } from "@/stores/auth";
import {
  buildFeaturedConversationDetailPath,
  ROUTES,
} from "@/router/routes";
import {
  featuredConversationApi,
  type FeaturedConversationCard,
} from "@/services/featuredConversation";
import {
  featuredConversationAdminApi,
  type FeaturedConversationAdminRecord,
} from "@/services/featuredConversationAdmin";
import {
  isHistoryDetailEmpty,
  mergeConversationHistoryPage,
  mergeRunReplayIntoConversation,
} from "@/utils/conversationHistory";
import { restoreHitlForSession } from "@/utils/hitlRestore";
import { readActiveRun } from "@/utils/activeRunStorage";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import {
  deriveConversationMetaFromInput,
  getConversationDraft,
  mergeLocalRecentConversations,
  mergeRecentSessions,
  resolveLocalSessionSelection,
  shouldApplyConversationToView,
  setConversationDraft,
  toRecentSessionItem,
} from "./homeState";
import FeaturedConversationAdminPanel from "./FeaturedConversationAdminPanel";
import {
  hydrateSessionWithRunningReplay,
  resolveInitialSessionId,
} from "./sessionBootstrap";
import { useRecentSessions } from "./useRecentSessions";
import { loadCachedSessionFiles } from "./sessionWorkspaceFiles";
import WelcomeView from "./WelcomeView";
import ConversationSidebar from "./ConversationSidebar";
import type { PanelItemType } from "@/components/ActionPanel";
import {
  workspaceFileKey,
  type WorkspaceFileItem,
} from "@/components/ActionView/workspaceFiles";
import { normalizeSessionArtifactFiles } from "@/utils/taskArtifacts";
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

const LazyWorkspaceMRag = lazy(() => import("@/pages/WorkspaceMRag"));
const LazyWorkspaceImageGeneration = lazy(
  () => import("@/pages/WorkspaceImageGeneration")
);
const LazyWorkspaceSop = lazy(() => import("@/pages/WorkspaceSop"));
const LazySubAgentAdmin = lazy(() => import("@/pages/SubAgentAdmin"));
const LazyModelAdmin = lazy(() => import("@/pages/ModelAdmin"));
const LazyCapabilityLibrary = lazy(() => import("@/pages/CapabilityLibrary"));
const LazyFeaturedConversations = lazy(
  () => import("@/pages/FeaturedConversations")
);

const WorkspaceLoading: ReactorType.FC = () => (
  <Loading loading className="h-full min-h-[240px]" />
);

const WorkspaceContent: ReactorType.FC<{ children: ReactNode }> = ({ children }) =>
  <Suspense fallback={<WorkspaceLoading />}>{children}</Suspense>;

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

const HISTORY_PAGE_SIZE = 20;

const getRecentSessionSummaryKey = (
  conversation?: CHAT.ConversationHistory
) => {
  if (!conversation) {
    return "";
  }
  const summary = toRecentSessionItem(conversation);
  if (!summary) {
    return "";
  }
  return [
    summary.sessionId,
    summary.title,
    summary.status,
    summary.latestQueryText,
    summary.runCount,
    summary.finishedRunCount,
    summary.failedRunCount,
  ]
    .map((value) => String(value ?? ""))
    .join("\u001f");
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
  // 状态负责视图切换；认证状态由应用启动时的 Cookie refresh 恢复。
  const navigate = useNavigate();
  const auth = useAuth();
  const initialRef = useRef<InitialState>(createInitialState());
  const conversationBootstrapResolvedRef = useRef(false);
  const {
    recentSessions,
    recentSessionsLoading,
    refreshRecentSessions,
  } = useRecentSessions();
  const [localRecentConversations, setLocalRecentConversations] = useState<
    CHAT.ConversationHistory[]
  >([]);
  const localRecentConversationsRef = useRef<CHAT.ConversationHistory[]>([]);
  const localRecentSummaryRef = useRef<Map<string, string>>(new Map());
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
  const [conversationDrafts, setConversationDrafts] = useState<
    Record<string, string>
  >({});
  type ChatViewApi = {
    openFile: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
  };
  const chatViewApiRef = useRef<ChatViewApi | null>(null);
  const [featuredEntryId, setFeaturedEntryId] = useState("");
  const [inputInfo, setInputInfo] = useState<CHAT.TInputInfo>(EMPTY_INPUT);
  const [product, setProduct] = useState(() => getProductByType(initialRef.current.productType));
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
  const [conversationBootstrapLoading, setConversationBootstrapLoading] =
    useState(false);
  const [historyPageLoading, setHistoryPageLoading] = useState(false);
  const historyPageRequestsRef = useRef<Map<string, Promise<void>>>(new Map());
  const runReplayRequestsRef = useRef<
    Map<string, Promise<ConversationRunReplay>>
  >(new Map());
  const sessionWorkspaceFilesCacheRef = useRef<Map<string, CHAT.TFile[]>>(
    new Map()
  );
  const sessionWorkspaceFileRequestsRef = useRef<
    Map<string, Promise<CHAT.TFile[]>>
  >(new Map());
  const sessionSelectionVersionRef = useRef(0);
  const loadRunReplay = useCallback(
    (sessionId: string, requestId: string) => {
      const key = `${sessionId}::${requestId}`;
      const inFlight = runReplayRequestsRef.current.get(key);
      if (inFlight) {
        return inFlight;
      }

      const request = conversationHistoryApi
        .getRunReplay(requestId)
        .finally(() => {
          runReplayRequestsRef.current.delete(key);
        });
      runReplayRequestsRef.current.set(key, request);
      return request;
    },
    []
  );
  const reportRunningReplayError = useCallback(
    (requestId: string, error: unknown) => {
      console.error(
        `加载运行中会话回放失败 (requestId=${requestId})`,
        error
      );
    },
    []
  );

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

  const [currentConversation, setCurrentConversation] =
    useState<CHAT.ConversationHistory>(() =>
      createConversation({productType: initialRef.current.productType,})
    );
  const currentConversationRef = useRef(currentConversation);
  currentConversationRef.current = currentConversation;
  const currentInputDraft = getConversationDraft(
    conversationDrafts,
    currentConversation.sessionId
  );
  const updateCurrentInputDraft = useCallback((draft: string) => {
    const sessionId = currentConversationRef.current.sessionId;
    setConversationDrafts((previous) =>
      setConversationDraft(previous, sessionId, draft)
    );
  }, []);
  const [sessionWorkspaceFilesState, setSessionWorkspaceFilesState] = useState<{
    sessionId: string;
    files: CHAT.TFile[];
  }>({
    sessionId: "",
    files: [],
  });

  const loadSessionWorkspaceFiles = useCallback((sessionId: string) => {
    const normalizedSessionId = sessionId?.trim();
    if (!normalizedSessionId) {
      return Promise.resolve([] as CHAT.TFile[]);
    }

    return loadCachedSessionFiles<CHAT.TFile[]>(
      normalizedSessionId,
      sessionWorkspaceFilesCacheRef.current,
      sessionWorkspaceFileRequestsRef.current,
      async (id) => {
        const files = await conversationHistoryApi.getSessionFiles(id);
        return normalizeSessionArtifactFiles(files, id);
      }
    )
      .then((files) => {
        if (currentConversationRef.current.sessionId === normalizedSessionId) {
          setSessionWorkspaceFilesState({
            sessionId: normalizedSessionId,
            files,
          });
        }
        return files;
      })
      .catch((error) => {
        console.error(
          `加载会话文件清单失败 (sessionId=${normalizedSessionId})`,
          error
        );
        return [] as CHAT.TFile[];
      });
  }, []);

  useEffect(() => {
    setSessionWorkspaceFilesState({
      sessionId: currentConversation.sessionId,
      files: sessionWorkspaceFilesCacheRef.current.get(currentConversation.sessionId) || [],
    });
  }, [currentConversation.sessionId]);

  const ensureCurrentSessionWorkspaceFiles = useCallback(() => {
    if (!currentConversation.chatList.length) {
      return;
    }
    void loadSessionWorkspaceFiles(currentConversation.sessionId);
  }, [
    currentConversation.chatList.length,
    currentConversation.sessionId,
    loadSessionWorkspaceFiles,
  ]);
  const visibleSessionWorkspaceFiles =
    sessionWorkspaceFilesState.sessionId === currentConversation.sessionId
      ? sessionWorkspaceFilesState.files
      : [];

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
    // Session data loads after the app has completed its silent cookie refresh.
    let disposed = false;
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

        void loadSessionWorkspaceFiles(initialSessionId);

        return conversationHistoryApi
          .getSessionDetail(initialSessionId, { limit: HISTORY_PAGE_SIZE })
          .then(async (detail) => {
            if (disposed || !detail || isHistoryDetailEmpty(detail)) {
              return;
            }
            const hydrated = await hydrateSessionWithRunningReplay(
              detail,
              (requestId) => loadRunReplay(initialSessionId, requestId),
              reportRunningReplayError
            );
            const restored = await restoreHitlForSession(hydrated);
            if (disposed) {
              return;
            }
            setCurrentConversation(restored);
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
    loadRunReplay,
    loadSessionWorkspaceFiles,
    reportRunningReplayError,
  ]);

  useEffect(() => {
    const matched = getProductByType(currentConversation.productType);
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

      const source = localRecentConversationsRef.current;
      const next = mergeLocalRecentConversations(source, conversation);
      localRecentConversationsRef.current = next;

      const nextSummaryKey = getRecentSessionSummaryKey(
        next.find((item) => item.sessionId === conversation.sessionId)
      );
      if (localRecentSummaryRef.current.get(conversation.sessionId) === nextSummaryKey) {
        // Keep the latest full snapshot in the ref for session switching, but
        // do not enqueue a React update for every streaming timestamp/token.
        return;
      }
      localRecentSummaryRef.current.set(conversation.sessionId, nextSummaryKey);
      setLocalRecentConversations(next);
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
      // inputInfo 会在同一轮立即清空；这里必须同步提交首个 chatList，避免 ChatView
      // 在过渡更新落地前被 Home 判断为空而卸载并 abort SSE。
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
      sessionSelectionVersionRef.current += 1;
      // 创建新会话同时清空输入、任务文件和视图壳状态；override 只用于恢复
      // 已存在的 session 元数据，默认路径始终生成新的 sessionId。
      const nextSessionId = override?.sessionId || createSessionId();
      const nextProductType = override?.productType || product.type;
      setActiveView("chat");
      const nextConversation = createConversation({
        sessionId: nextSessionId,
        productType: nextProductType,
        deepThink: nextProductType === "dataAgent" ? false : override?.deepThink ?? false,
        ...override,
      });
      setCurrentConversation(nextConversation);
      setWorkspaceTaskList([]);
      setSelectedTaskFileKey("");
      upsertLocalRecentSession(nextConversation);
      resetInput();
    },
    [product.type, resetInput, upsertLocalRecentSession]
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
      const selectionVersion = ++sessionSelectionVersionRef.current;
      setWorkspaceTaskList([]);
      setSelectedTaskFileKey("");
      setHistoryPageLoading(false);
      setActiveView("chat");

      const localSelection = resolveLocalSessionSelection(
        localRecentConversationsRef.current,
        session.sessionId
      );
      if (!localSelection.shouldLoadRemote && localSelection.localConversation) {
        // 新建但尚未发送过消息的会话只存在前端，不能向后端请求不存在的详情。
        setCurrentConversation(localSelection.localConversation);
        resetInput();
        return;
      }

      void loadSessionWorkspaceFiles(session.sessionId);
      // 切换已有会话始终从后端读取最新详情，避免用前端流式缓存覆盖已完成状态。
      conversationHistoryApi
        .getSessionDetail(session.sessionId, { limit: HISTORY_PAGE_SIZE })
        .then(async (detail) => {
          if (selectionVersion !== sessionSelectionVersionRef.current) {
            return;
          }
          if (!detail || isHistoryDetailEmpty(detail)) {
            if (localSelection.localConversation) {
              setCurrentConversation(localSelection.localConversation);
            }
            return;
          }
          const hydrated = await hydrateSessionWithRunningReplay(
            detail,
            (requestId) => loadRunReplay(session.sessionId, requestId),
            reportRunningReplayError
          );
          const restored = await restoreHitlForSession(hydrated);
          if (selectionVersion !== sessionSelectionVersionRef.current) {
            return;
          }
          setCurrentConversation(restored);
          resetInput();
        })
        .catch((error) => {
          if (selectionVersion === sessionSelectionVersionRef.current) {
            if (localSelection.localConversation) {
              setCurrentConversation(localSelection.localConversation);
              return;
            }
            console.error("加载历史会话详情失败", error);
          }
        });
    },
    [loadRunReplay, loadSessionWorkspaceFiles, reportRunningReplayError, resetInput]
  );

  const requestRunReplay = useCallback(
    (requestId: string) => {
      const sessionId = currentConversation.sessionId;
      return loadRunReplay(sessionId, requestId)
        .then((replay) => {
          setCurrentConversation((previous) =>
            previous.sessionId === sessionId
              ? mergeRunReplayIntoConversation(previous, replay)
              : previous
          );
        })
        .catch((error) => {
          console.error(
            `加载会话 run 回放失败 (requestId=${requestId})`,
            error
          );
        });
    },
    [currentConversation.sessionId, loadRunReplay]
  );

  const loadMoreHistory = useCallback(() => {
    const { sessionId, historyNextCursor, historyHasMore } = currentConversation;
    if (!sessionId || !historyHasMore || !historyNextCursor) {
      return Promise.resolve();
    }

    const key = `${sessionId}::${historyNextCursor}`;
    const inFlight = historyPageRequestsRef.current.get(key);
    if (inFlight) {
      return inFlight;
    }

    setHistoryPageLoading(true);
    const request = conversationHistoryApi
      .getSessionDetail(sessionId, {
        limit: HISTORY_PAGE_SIZE,
        after: historyNextCursor,
      })
      .then((page) => {
        setCurrentConversation((previous) =>
          previous.sessionId === sessionId
            ? mergeConversationHistoryPage(previous, page)
            : previous
        );
      })
      .catch((error) => {
        console.error("加载更早会话记录失败", error);
      })
      .finally(() => {
        historyPageRequestsRef.current.delete(key);
        setHistoryPageLoading(false);
      });

    historyPageRequestsRef.current.set(key, request);
    return request;
  }, [currentConversation]);

  useEffect(() => {
    if (
      conversationBootstrapLoading ||
      !conversationBootstrapResolvedRef.current
    ) {
      return;
    }
    setSessionId(currentConversation.sessionId);
  }, [conversationBootstrapLoading, currentConversation.sessionId]);

  const changeInputInfo = useCallback(
    (info: CHAT.TInputInfo) => {
      const nextMeta = deriveConversationMetaFromInput(info, { productType: product.type });

      updateCurrentConversationMeta(nextMeta);

      setInputInfo({
        ...info,
        outputStyle: info.outputStyle,
        deepThink: nextMeta.deepThink,
      });
    },
    [product.type, updateCurrentConversationMeta]
  );

  const handleInputSelectionChange = useCallback(
    ({
      product: nextProduct,
      deepThink: nextDeepThink,
    }: {
      product: CHAT.Product;
      deepThink: boolean;
    }) => {
      const resolved = nextProduct;
      setProduct(resolved);

      updateCurrentConversationMeta({
        productType: resolved.type,
        deepThink: resolved.type === "dataAgent" ? false : nextDeepThink,
      });
    },
    [updateCurrentConversationMeta]
  );

  const toSendMessage = useCallback(
    (query: SuggestedQuestion) => {
      changeInputInfo({
        message: query.label,
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

      const operator =
        auth.user?.nickname?.trim() ||
        auth.user?.account ||
        featuredAdminForm.operator;
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
    [
      auth.user?.account,
      auth.user?.nickname,
      featuredAdminForm.operator,
      syncFeaturedAdminRecord,
    ]
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

  const handleSidebarNewChat = useCallback(() => {
    setSidebarPanel("sessions");
    setSelectedTaskFileKey("");
    setWorkspaceImmersive(false);
    closeMobileSidebar();
    createNewChat();
  }, [closeMobileSidebar, createNewChat]);

  const handleSidebarSelectSession = useCallback(
    (session: ConversationSessionItem) => {
      setSidebarPanel("sessions");
      setSelectedTaskFileKey("");
      setWorkspaceImmersive(false);
      closeMobileSidebar();
      handleSelectRecentSession(session);
    },
    [closeMobileSidebar, handleSelectRecentSession]
  );

  const handleSidebarChangeView = useCallback(
    (view: SidebarView) => {
      if (view === "featured" && auth.status !== "authenticated") {
        navigate(ROUTES.FEATURED_CONVERSATIONS);
        return;
      }
      sessionSelectionVersionRef.current += 1;
      if (view === "featured") {
        setFeaturedEntryId("");
      }
      setSidebarPanel("sessions");
      setWorkspaceImmersive(false);
      closeMobileSidebar();
      setActiveView(view);
    },
    [auth.status, closeMobileSidebar, navigate]
  );

  const handleLogout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch (error) {
      console.error("退出登录失败", error);
    }
    navigate(ROUTES.LOGIN, { replace: true });
  }, [navigate]);

  const handleSidebarOpenTaskFiles = useCallback(() => {
    sessionSelectionVersionRef.current += 1;
    ensureCurrentSessionWorkspaceFiles();
    setActiveView("chat");
    setWorkspaceImmersive(false);
    setSidebarPanel("task-files");
  }, [ensureCurrentSessionWorkspaceFiles]);

  const handleSidebarCloseTaskFiles = useCallback(() => {
    setSidebarPanel("sessions");
  }, []);

  const handleSidebarSelectTaskFile = useCallback(
    (file: WorkspaceFileItem) => {
      setSelectedTaskFileKey(workspaceFileKey(file));
      chatViewApiRef.current?.openFile(file);
      closeMobileSidebar();
    },
    [closeMobileSidebar]
  );

  const handleSidebarRefreshTaskFiles = useCallback(() => {
    setWorkspaceTaskList((prev) => [...prev]);
  }, []);

  const sidebarSharedProps = useMemo(
    () => ({
      activeView,
      recentSessions: displayedRecentSessions,
      recentSessionsLoading,
      selectedSessionId: currentConversation.sessionId,
      user: auth.user,
      sidebarPanel,
      taskList: workspaceTaskList,
      selectedTaskFileKey,
      onNewChat: handleSidebarNewChat,
      onSelectSession: handleSidebarSelectSession,
      onChangeView: handleSidebarChangeView,
      onManageFeaturedConversation: handleOpenFeaturedAdmin,
      onOpenTaskFiles: handleSidebarOpenTaskFiles,
      onCloseTaskFiles: handleSidebarCloseTaskFiles,
      onSelectTaskFile: handleSidebarSelectTaskFile,
      onRefreshTaskFiles: handleSidebarRefreshTaskFiles,
      onLogout: handleLogout,
    }),
    [
      activeView,
      auth.user,
      currentConversation.sessionId,
      displayedRecentSessions,
      handleOpenFeaturedAdmin,
      handleSidebarChangeView,
      handleSidebarCloseTaskFiles,
      handleSidebarNewChat,
      handleSidebarOpenTaskFiles,
      handleSidebarRefreshTaskFiles,
      handleSidebarSelectSession,
      handleSidebarSelectTaskFile,
      handleLogout,
      recentSessionsLoading,
      selectedTaskFileKey,
      sidebarPanel,
      workspaceTaskList,
    ]
  );

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
              <WorkspaceContent>
                <LazyWorkspaceMRag embedded />
              </WorkspaceContent>
            ) : activeView === "image-generation" ? (
              <WorkspaceContent>
                <LazyWorkspaceImageGeneration embedded />
              </WorkspaceContent>
            ) : activeView === "sop" ? (
              <WorkspaceContent>
                <LazyWorkspaceSop embedded />
              </WorkspaceContent>
            ) : activeView === "sub-agents" ? (
              <WorkspaceContent>
                <LazySubAgentAdmin embedded />
              </WorkspaceContent>
            ) : activeView === "models" ? (
              <WorkspaceContent>
                <LazyModelAdmin embedded />
              </WorkspaceContent>
            ) : activeView === "capabilities" ? (
              <WorkspaceContent>
                <LazyCapabilityLibrary embedded />
              </WorkspaceContent>
            ) : activeView === "featured" ? (
              <WorkspaceContent>
                <LazyFeaturedConversations
                  embedded
                  initialFeaturedId={featuredEntryId}
                />
              </WorkspaceContent>
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
                      inputDraft={currentInputDraft}
                      onInputDraftChange={updateCurrentInputDraft}
                      product={product}
                      conversation={currentConversation}
                      onConversationChange={updateConversation}
                      onRequestRunReplay={requestRunReplay}
                      onLoadMoreHistory={loadMoreHistory}
                      historyLoading={historyPageLoading}
                      onInputConsumed={onInputConsumed}
                      onTaskListChange={setWorkspaceTaskList}
                      sessionWorkspaceFiles={visibleSessionWorkspaceFiles}
                      onEnsureSessionFiles={ensureCurrentSessionWorkspaceFiles}
                      onRegisterApi={(api) => {
                        chatViewApiRef.current = api;
                      }}
                      onOpenTaskFiles={() => {
                        ensureCurrentSessionWorkspaceFiles();
                        setWorkspaceImmersive(false);
                        setSidebarPanel("task-files");
                        setMobileSidebarOpen(true);
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
                      inputDraft={currentInputDraft}
                      product={product}
                      videoModalOpen={videoModalOpen}
                      onSelectionChange={handleInputSelectionChange}
                      onInputDraftChange={updateCurrentInputDraft}
                      onSend={changeInputInfo}
                      onSendQuestion={toSendMessage}
                      onOpenVideo={setVideoModalOpen}
                      onCloseVideo={() => setVideoModalOpen(undefined)}
                      featuredCards={featuredCards}
                      onOpenFeaturedConversations={() => {
                        if (auth.status !== "authenticated") {
                          navigate(ROUTES.FEATURED_CONVERSATIONS);
                          return;
                        }
                        setFeaturedEntryId("");
                        setActiveView("featured");
                      }}
                      onOpenFeaturedDetail={(featuredId) => {
                        if (auth.status !== "authenticated") {
                          navigate(buildFeaturedConversationDetailPath(featuredId));
                          return;
                        }
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
