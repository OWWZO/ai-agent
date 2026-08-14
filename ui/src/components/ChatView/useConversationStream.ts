import { useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { useMemoizedFn } from "ahooks";
import { message } from "antd";
import { isOutputProductType, toRequestOutputStyle } from "@/utils/constants";
import { getUniqId } from "@/utils";
import { buildAgentStreamRequest } from "@/utils/agentRequest";
import {
  clearActiveRun,
  readActiveRun,
  saveActiveRun,
  updateActiveRunSeq,
  updateActiveRunEvent,
} from "@/utils/activeRunStorage";
import {
  buildConversationTaskData,
  buildTaskFromEventData,
  combineData,
  handleTaskData,
  normalizeEventData,
} from "@/utils/chat";
import {
  hydrateConversationFromReplayFrames,
} from "@/utils/conversationHistory";
import querySSE from "@/utils/querySSE";
import { parseAgentAnswer } from "@/utils/sseParsers";
import { conversationHistoryApi } from "@/services/agentConversation";
import { AGENT_RUN_FOLLOW_SSE_URL } from "@/services/agentRun";
import type {
  ActiveRunState,
  ConversationDraftController,
  ConversationListKey,
  ThrottledStreamController,
} from "./chatView.types";
import {
  cloneWorkspaceTask,
  getLatestRenderableTask,
  resolveActionPanelVisibility,
  resolveLatestRunState,
  resolveRunPresence,
  resolveWorkspaceCaption,
  shouldRefreshWorkspaceTask,
} from "./streamState";

function isLlmRetryEvent(eventData?: MESSAGE.EventData | null): boolean {
  return (
    eventData?.messageType === "llm_retry" ||
    eventData?.resultMap?.messageType === "llm_retry"
  );
}

function isLlmRetryTip(tip?: string): boolean {
  return !!tip && tip.includes("正在重试");
}

type UseConversationStreamOptions = {
  conversation: CHAT.ConversationHistory;
  onConversationChange: (
    conversationId: string,
    nextConversation: CHAT.ConversationHistory
  ) => void;
  onPrepareStreamingWorkspace?: () => void;
  onTokenUseUp?: () => void;
};

type UseConversationStreamResult = {
  taskList: CHAT.Task[];
  workspaceStreamTask?: CHAT.Task;
  workspaceCaption?: string;
  activeRunState?: ActiveRunState;
  setActiveRunState: Dispatch<SetStateAction<ActiveRunState | undefined>>;
  plan?: CHAT.Plan;
  showAction: boolean;
  changeActionStatus: (status: boolean) => void;
  loading: boolean;
  streamingThoughtMap: Record<string, string>;
  sendMessage: (inputInfo: CHAT.TInputInfo) => void;
  stopActiveRun: () => Promise<void>;
  /** 向进行中的 run 注入用户指导（不开新 SSE） */
  injectActiveRun: (text: string) => Promise<boolean>;
  regenerateLastMessage: () => void;
};

const CONNECTION_LOST_HINT = "连接暂时断开，任务仍在后台执行";
const CONCURRENT_RUN_HINT =
  "已有任务在进行中，请等待完成或先停止后再试";
const FOLLOW_RECONNECT_BASE_DELAY = 800;

function isChatItemRunning(chat?: CHAT.ChatItem | null) {
  if (!chat) {
    return false;
  }
  if (chat.loading) {
    return true;
  }
  return String(chat.metrics?.status || "").toUpperCase() === "RUNNING";
}
const FOLLOW_RECONNECT_MAX_DELAY = 15_000;
/** RUNNING 期间允许持续退避；不再在 6 次后永久放弃 */
const FOLLOW_RECONNECT_ATTEMPT_CAP = 20;

/** 每个会话至多一条活 SSE；切会话不 abort，后台继续写回对应 conversation。 */
type LiveStreamEntry = {
  conversationId: string;
  requestId: string;
  controller: AbortController;
};

/** 断流后后台会话也能 follow：不依赖当前前台 conversationRef。 */
type FollowReconnectContext = {
  conversationId: string;
  sessionId: string;
  requestId: string;
  productType?: string;
  deepThink?: boolean;
  seedChat: CHAT.ChatItem;
};

function useRafThrottle<TValue>(
  initialValue: TValue,
  interval: number,
  onFlush: (value: TValue) => void
): ThrottledStreamController<TValue> {
  const frameRef = useRef<number | null>(null);
  const pendingRef = useRef(initialValue);
  const lastFlushAtRef = useRef(0);

  const cancel = useMemoizedFn(() => {
    if (frameRef.current !== null) {
      cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
    }
  });

  const flush = useMemoizedFn((force = false) => {
    const now = performance.now();
    if (!force && now - lastFlushAtRef.current < interval) {
      return;
    }
    lastFlushAtRef.current = now;
    const nextValue = pendingRef.current;
    pendingRef.current = initialValue;
    onFlush(nextValue);
  });

  const schedule = useMemoizedFn((
    updater: TValue | ((current: TValue) => TValue),
    force = false
  ) => {
    pendingRef.current =
      typeof updater === "function"
        ? (updater as (current: TValue) => TValue)(pendingRef.current)
        : updater;

    if (force) {
      cancel();
      flush(true);
      return;
    }

    if (frameRef.current !== null) {
      return;
    }

    const requestNextFrame = () => {
      frameRef.current = requestAnimationFrame(() => {
        frameRef.current = null;
        const now = performance.now();
        if (now - lastFlushAtRef.current < interval) {
          requestNextFrame();
          return;
        }
        flush(true);
      });
    };

    requestNextFrame();
  });

  const reset = useMemoizedFn((value: TValue) => {
    cancel();
    pendingRef.current = value;
    lastFlushAtRef.current = 0;
  });

  return useMemo(() => ({
    pendingRef,
    cancel,
    flush,
    schedule,
    reset,
  }), [cancel, flush, reset, schedule]);
}

function replaceConversationListLastItem<TItem>(
  conversation: CHAT.ConversationHistory,
  key: ConversationListKey,
  item: TItem
) {
  const nextList = [...(conversation[key] as TItem[])];
  nextList.splice(nextList.length - 1, 1, item);
  return {
    ...conversation,
    [key]: nextList,
  } as CHAT.ConversationHistory;
}

export function createConversationDraftController<TItem>(
  conversationId: string,
  initialConversation: CHAT.ConversationHistory,
  listKey: ConversationListKey,
  commit: (conversationId: string, nextConversation: CHAT.ConversationHistory) => void
): ConversationDraftController<TItem> {
  let snapshot = initialConversation;

  return {
    conversationId,
    getSnapshot: () => snapshot,
    replaceLastItem: (item) => {
      snapshot = replaceConversationListLastItem(snapshot, listKey, item);
      return snapshot;
    },
    commit: (nextConversation) => {
      snapshot = nextConversation;
      commit(conversationId, snapshot);
    },
  };
}

export function createDraftConversation(
  baseConversation: CHAT.ConversationHistory,
  overrides: Partial<CHAT.ConversationHistory>
) {
  return {
    ...baseConversation,
    chatTitle: baseConversation.chatTitle || overrides.chatTitle || "",
    title:
      baseConversation.title === "新对话" && overrides.chatTitle
        ? overrides.chatTitle.slice(0, 30)
        : baseConversation.title,
    ...overrides,
  };
}

function createRunningChat(
  inputInfo: CHAT.TInputInfo,
  sessionId: string,
  requestId: string,
  outputStyle?: string,
  deepThink?: boolean
): CHAT.ChatItem {
  return {
    query: inputInfo.message!,
    files: inputInfo.files!,
    responseType: "txt",
    sessionId,
    requestId,
    agentType: outputStyle === "chat" ? 0 : deepThink ? 1 : 2,
    loading: true,
    forceStop: false,
    tasks: [],
    thought: "",
    response: "",
    taskStatus: 0,
    tip: "",
    multiAgent: { tasks: [] },
    metrics: { status: "RUNNING" },
  };
}

/**
 * guard error 没有结构化 eventData 时，前端需要补一条失败总结，
 * 否则多智能体对话会停留在 loading 态，看不到明确的失败结论。
 */
export function applyGuardError(
  currentChat: CHAT.ChatItem,
  errorText: string
): CHAT.ChatItem {
  const nextErrorText = errorText || "当前请求处理失败，请稍后重试";

  return {
    ...currentChat,
    loading: false,
    tip: nextErrorText,
    metrics: {
      ...(currentChat.metrics || {}),
      status: "FAILED",
    },
    conclusion: {
      id: `${currentChat.requestId}-guard-error`,
      messageId: `${currentChat.requestId}-guard-error`,
      requestId: currentChat.requestId,
      messageTime: String(Date.now()),
      messageType: "task_summary",
      finish: true,
      isFinal: true,
      result: nextErrorText,
      resultMap: {
        taskSummary: nextErrorText,
        fileList: [],
        isFinal: true,
      },
    } as CHAT.Task,
  };
}

export function useConversationStream(
  options: UseConversationStreamOptions
): UseConversationStreamResult {
  const {
    conversation,
    onConversationChange,
    onPrepareStreamingWorkspace,
    onTokenUseUp,
  } = options;

  const [taskList, setTaskList] = useState<CHAT.Task[]>([]);
  const [workspaceStreamTask, setWorkspaceStreamTask] = useState<CHAT.Task>();
  const [activeRunState, setActiveRunState] = useState<ActiveRunState>();
  const [plan, setPlan] = useState<CHAT.Plan>();
  const [showAction, setShowAction] = useState(false);
  const [loading, setLoading] = useState(false);
  const [streamingThoughtMap, setStreamingThoughtMap] = useState<Record<string, string>>({});
  const conversationRef = useRef(conversation);
  const activeRequestIdRef = useRef<string | null>(null);
  const streamAbortControllerRef = useRef<AbortController | null>(null);
  const liveStreamsRef = useRef<Map<string, LiveStreamEntry>>(new Map());
  const followReconnectTimersRef = useRef<Map<string, number>>(new Map());
  const followReconnectAttemptsRef = useRef<Map<string, number>>(new Map());
  const followReconnectContextsRef = useRef<Map<string, FollowReconnectContext>>(
    new Map()
  );
  /** 各会话最新快照，供后台 follow 重连时 draft 使用（避免用前台 conversation 覆盖）。 */
  const conversationSnapshotsRef = useRef<Map<string, CHAT.ConversationHistory>>(
    new Map()
  );
  const lastEventSeqRef = useRef<Map<string, number>>(new Map());

  const clearFollowReconnectTimer = useMemoizedFn((requestId?: string) => {
    if (requestId) {
      const timer = followReconnectTimersRef.current.get(requestId);
      if (timer != null) {
        window.clearTimeout(timer);
        followReconnectTimersRef.current.delete(requestId);
      }
      return;
    }
    followReconnectTimersRef.current.forEach((timer) => window.clearTimeout(timer));
    followReconnectTimersRef.current.clear();
  });

  const bindForegroundStream = useMemoizedFn(
    (conversationId: string, requestId: string, controller: AbortController) => {
      liveStreamsRef.current.set(conversationId, {
        conversationId,
        requestId,
        controller,
      });
      if (conversationRef.current.id === conversationId) {
        streamAbortControllerRef.current = controller;
        activeRequestIdRef.current = requestId;
      }
    }
  );

  const unbindLiveStream = useMemoizedFn(
    (conversationId: string, controller?: AbortController) => {
      const entry = liveStreamsRef.current.get(conversationId);
      if (!entry) {
        return;
      }
      if (controller && entry.controller !== controller) {
        return;
      }
      liveStreamsRef.current.delete(conversationId);
      if (streamAbortControllerRef.current === entry.controller) {
        streamAbortControllerRef.current = null;
      }
      if (
        conversationRef.current.id === conversationId &&
        activeRequestIdRef.current === entry.requestId
      ) {
        activeRequestIdRef.current = null;
      }
    }
  );

  const hasLiveStream = useMemoizedFn(
    (conversationId: string, requestId: string) => {
      const entry = liveStreamsRef.current.get(conversationId);
      return (
        !!entry &&
        entry.requestId === requestId &&
        !entry.controller.signal.aborted
      );
    }
  );

  const workspaceTaskThrottle = useRafThrottle<CHAT.Task | undefined>(
    undefined,
    32,
    (task) => setWorkspaceStreamTask(task)
  );
  const thoughtThrottle = useRafThrottle<Record<string, string>>(
    {},
    48,
    (pendingThoughtMap) => {
      const pendingEntries = Object.entries(pendingThoughtMap);
      if (!pendingEntries.length) {
        return;
      }

      setStreamingThoughtMap((previous) => {
        let changed = false;
        const next = { ...previous };

        pendingEntries.forEach(([requestId, thought]) => {
          if (next[requestId] !== thought) {
            next[requestId] = thought;
            changed = true;
          }
        });

        return changed ? next : previous;
      });
    }
  );
  const resetWorkspaceTaskThrottle = workspaceTaskThrottle.reset;
  const resetThoughtThrottle = thoughtThrottle.reset;
  const cancelWorkspaceTaskThrottle = workspaceTaskThrottle.cancel;
  const cancelThoughtThrottle = thoughtThrottle.cancel;

  const commitConversation = useMemoizedFn(
    (conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      const stamped = {
        ...nextConversation,
        updatedAt: Date.now(),
      };
      conversationSnapshotsRef.current.set(conversationId, stamped);
      onConversationChange(conversationId, stamped);
    }
  );

  useEffect(() => {
    conversationSnapshotsRef.current.set(conversation.id, conversation);
  }, [conversation]);

  const scheduleStreamingThought = useMemoizedFn((requestId: string, thought: string, force = false) => {
    thoughtThrottle.schedule((current) => ({
      ...current,
      [requestId]: thought,
    }), force);
  });

  const scheduleWorkspaceStreamTask = useMemoizedFn((chat: CHAT.ChatItem, force = false) => {
    const latestTask = getLatestRenderableTask(chat);
    if (!latestTask) {
      return;
    }

    workspaceTaskThrottle.schedule(cloneWorkspaceTask(latestTask), force);
  });

  const changeActionStatus = useMemoizedFn((status: boolean) => {
    setShowAction(status);
  });

  useEffect(() => {
    conversationRef.current = conversation;
  }, [conversation]);

  useEffect(() => {
    // 切会话只切换前台 UI；后台会话的 SSE / follow 重连继续跑，禁止 abort、禁止清重连定时器。
    resetWorkspaceTaskThrottle(undefined);
    resetThoughtThrottle({});
    setTaskList([]);
    setWorkspaceStreamTask(undefined);
    setActiveRunState(undefined);
    setPlan(undefined);
    setShowAction(false);
    setStreamingThoughtMap({});

    const conversationId = conversation.id;
    const latestRunningChat = [...conversationRef.current.chatList]
      .reverse()
      .find((chat) => chat.loading && chat.requestId);
    const live = liveStreamsRef.current.get(conversationId);

    if (latestRunningChat?.requestId) {
      // 刷新后由 Home 从 Ledger 恢复的 RUNNING run 仍然可以被用户显式停止，
      // 但网络断开本身不会在这里被改写成 STOPPED。
      activeRequestIdRef.current = latestRunningChat.requestId;
      setLoading(true);
      if (
        live &&
        live.requestId === latestRunningChat.requestId &&
        !live.controller.signal.aborted
      ) {
        streamAbortControllerRef.current = live.controller;
      } else {
        streamAbortControllerRef.current = null;
      }
    } else {
      activeRequestIdRef.current = null;
      streamAbortControllerRef.current = null;
      setLoading(false);
    }
  }, [
    conversation.id,
    resetThoughtThrottle,
    resetWorkspaceTaskThrottle,
  ]);

  const runningFollowKey = useMemo(() => {
    const latestRunningChat = [...conversation.chatList]
      .reverse()
      .find((chat) => chat.loading && chat.requestId);
    if (!latestRunningChat?.requestId) {
      return null;
    }
    return `${conversation.id}::${latestRunningChat.requestId}`;
  }, [conversation.chatList, conversation.id]);

  useEffect(() => {
    if (!conversation.chatList.length || loading) {
      return;
    }

    const latestChatSnapshot = [...conversation.chatList]
      .reverse()
      .find(
        (chat) =>
          (chat.multiAgent?.tasks?.length || 0) > 0 ||
          !!chat.multiAgent?.plan ||
          !!chat.timeline?.length
      );

    if (!latestChatSnapshot) {
      return;
    }

    const conversationTaskData = buildConversationTaskData(
      latestChatSnapshot,
      conversation.deepThink
    );
    const latestTask = getLatestRenderableTask(conversationTaskData.currentChat);

    setTaskList(conversationTaskData.taskList);
    setPlan(conversationTaskData.plan);
    setWorkspaceStreamTask(latestTask ? cloneWorkspaceTask(latestTask) : undefined);
    setActiveRunState(resolveLatestRunState(latestChatSnapshot));
    setShowAction(resolveActionPanelVisibility({
      plan: conversationTaskData.plan,
      taskList: conversationTaskData.taskList,
    }));
  }, [conversation.chatList, conversation.deepThink, conversation.id, loading]);

  useEffect(() => {
    const referenceChat = conversation.chatList[conversation.chatList.length - 1];
    if (!referenceChat) {
      setActiveRunState(undefined);
      return;
    }

    setActiveRunState(resolveLatestRunState(referenceChat));
  }, [conversation.chatList]);

  useEffect(() => {
    return () => {
      clearFollowReconnectTimer();
      followReconnectContextsRef.current.clear();
      liveStreamsRef.current.forEach((entry) => {
        entry.controller.abort();
      });
      liveStreamsRef.current.clear();
      streamAbortControllerRef.current = null;
      activeRequestIdRef.current = null;
      cancelWorkspaceTaskThrottle();
      cancelThoughtThrottle();
    };
  }, [
    cancelThoughtThrottle,
    cancelWorkspaceTaskThrottle,
    clearFollowReconnectTimer,
  ]);

  /**
   * 历史 hydrate 出 RUNNING 后，续绑服务端仍在执行的 run（不重发 query）。
   * 与 sendMessage 共用同一套 combineData / handleTaskData 语义。
   * reconnectContext 用于后台会话断流后续绑（不依赖前台 conversationRef）。
   */
  const followActiveRun = useMemoizedFn((
    targetRequestId: string,
    reconnectContext?: FollowReconnectContext
  ) => {
    const cached = reconnectContext || followReconnectContextsRef.current.get(targetRequestId);
    const baseConversation = conversationRef.current;
    const conversationId = cached?.conversationId || baseConversation.id;
    const seedChat =
      cached?.seedChat ||
      [...baseConversation.chatList]
        .reverse()
        .find((chat) => chat.requestId === targetRequestId && chat.loading);
    if (!seedChat?.requestId) {
      return;
    }
    if (hasLiveStream(conversationId, targetRequestId)) {
      return;
    }
    clearFollowReconnectTimer(targetRequestId);

    const requestId = seedChat.requestId;
    const sessionId = cached?.sessionId || baseConversation.sessionId;
    const productType = cached?.productType ?? baseConversation.productType;
    const isChatMode = productType === "chat";
    const normalizedDeepThink = isChatMode
      ? false
      : Boolean(cached?.deepThink ?? baseConversation.deepThink);

    followReconnectContextsRef.current.set(requestId, {
      conversationId,
      sessionId,
      requestId,
      productType,
      deepThink: normalizedDeepThink,
      seedChat: {
        ...seedChat,
        loading: true,
        metrics: {
          ...(seedChat.metrics || {}),
          status: "RUNNING",
        },
      },
    });
    const previous = liveStreamsRef.current.get(conversationId);
    if (previous && previous.requestId !== requestId) {
      previous.controller.abort();
    }
    const abortController = new AbortController();
    bindForegroundStream(conversationId, requestId, abortController);
    const storedCheckpoint = readActiveRun();
    const initialEventSeq = storedCheckpoint?.requestId === requestId
      ? storedCheckpoint.lastEventSeq
      : (lastEventSeqRef.current.get(requestId) || 0);
    lastEventSeqRef.current.set(requestId, initialEventSeq);
    if (!storedCheckpoint || storedCheckpoint.requestId !== requestId) {
      saveActiveRun(sessionId, requestId);
    }
    if (conversationRef.current.id === conversationId) {
      setLoading(true);
      onPrepareStreamingWorkspace?.();
    }

    // 前台才刷本组件 UI；后台会话只通过 commitConversation 写回历史状态。
    const isActiveStream = () =>
      conversationRef.current.id === conversationId &&
      activeRequestIdRef.current === requestId;

    let currentChat: CHAT.ChatItem = {
      ...seedChat,
      loading: true,
      metrics: {
        ...(seedChat.metrics || {}),
        status: "RUNNING",
      },
    };

    const draftBase: CHAT.ConversationHistory =
      conversationSnapshotsRef.current.get(conversationId) ||
      (conversationRef.current.id === conversationId
        ? conversationRef.current
        : {
            ...conversationRef.current,
            id: conversationId,
            sessionId,
            productType: productType || conversationRef.current.productType,
            deepThink: normalizedDeepThink,
            chatList: [currentChat],
          });

    const draftController = createConversationDraftController<CHAT.ChatItem>(
      conversationId,
      draftBase,
      "chatList",
      commitConversation
    );

    const syncRunningConversation = () => {
      draftController.commit(draftController.replaceLastItem({ ...currentChat }));
    };

    const syncDerivedConversationSnapshot = (nextChat: CHAT.ChatItem) => {
      pendingConversation = draftController.replaceLastItem({ ...nextChat });
    };

    let pendingConversation: CHAT.ConversationHistory | null = null;
    let pendingTaskData: ReturnType<typeof handleTaskData> | null = null;
    let taskDataDirty = false;
    let pendingFlushFrame: number | null = null;
    let lastConversationFlushAt = 0;
    let lastTaskFlushAt = 0;
    const CONVERSATION_FLUSH_INTERVAL = 16;
    const TASK_FLUSH_INTERVAL = 96;

    const flushNonChatUpdates = (force = false) => {
      if (!pendingConversation && !pendingTaskData && !taskDataDirty) {
        return;
      }
      const now = performance.now();
      if (taskDataDirty) {
        const derived = handleTaskData(
          currentChat,
          normalizedDeepThink,
          currentChat.multiAgent
        );
        syncDerivedConversationSnapshot(derived.currentChat);
        taskDataDirty = false;
        if (force || now - lastTaskFlushAt >= TASK_FLUSH_INTERVAL) {
          pendingTaskData = derived;
        }
      }
      const shouldFlushConversation =
        !!pendingConversation &&
        (force || now - lastConversationFlushAt >= CONVERSATION_FLUSH_INTERVAL);
      const shouldFlushTask =
        !!pendingTaskData && (force || now - lastTaskFlushAt >= TASK_FLUSH_INTERVAL);
      const streamStillActive = isActiveStream();
      if (shouldFlushTask && pendingTaskData) {
        if (streamStillActive) {
          setTaskList(pendingTaskData.taskList);
          setPlan(pendingTaskData.plan);
          setShowAction(
            resolveActionPanelVisibility({
              plan: pendingTaskData.plan,
              taskList: pendingTaskData.taskList,
            })
          );
        }
        pendingTaskData = null;
        lastTaskFlushAt = now;
      }
      if (shouldFlushConversation && pendingConversation) {
        commitConversation(conversationId, pendingConversation);
        pendingConversation = null;
        lastConversationFlushAt = now;
      }
    };

    const scheduleNonChatFlush = (force = false) => {
      if (force) {
        if (pendingFlushFrame) {
          cancelAnimationFrame(pendingFlushFrame);
          pendingFlushFrame = null;
        }
        flushNonChatUpdates(true);
        return;
      }
      if (pendingFlushFrame) {
        return;
      }
      pendingFlushFrame = requestAnimationFrame(() => {
        pendingFlushFrame = null;
        flushNonChatUpdates(false);
        if (pendingConversation || pendingTaskData || taskDataDirty) {
          scheduleNonChatFlush(false);
        }
      });
    };

    const markFollowEnded = (nextStatus?: string) => {
      const streamStillActive = isActiveStream();
      clearFollowReconnectTimer(requestId);
      followReconnectAttemptsRef.current.delete(requestId);
      followReconnectContextsRef.current.delete(requestId);
      clearActiveRun(requestId);
      unbindLiveStream(conversationId, abortController);
      currentChat = {
        ...currentChat,
        loading: false,
        tip: "",
        metrics: {
          ...(currentChat.metrics || {}),
          status:
            nextStatus ||
            (currentChat.conclusion || currentChat.metrics?.status === "SUCCESS"
              ? "SUCCESS"
              : currentChat.metrics?.status === "STOPPED"
                ? "STOPPED"
                : "FAILED"),
        },
      };
      if (streamStillActive) {
        setLoading(false);
      }
      syncRunningConversation();
    };

    /**
     * follow_idle 表示进程内已无该 requestId。
     * 不能直接标 FAILED：任务可能已在后端跑完，只是观察流断了；刷新能好就是因为重新 hydrate ledger。
     * 这里主动拉 session detail 同步终态；若仍 RUNNING 则继续 follow。
     */
    const resyncAfterFollowIdle = async () => {
      const streamStillActive = isActiveStream();
      clearFollowReconnectTimer(requestId);
      unbindLiveStream(conversationId, abortController);
      currentChat = {
        ...currentChat,
        tip: "正在同步任务结果…",
      };
      pendingConversation = draftController.replaceLastItem({ ...currentChat });
      scheduleNonChatFlush(true);

      try {
        const detail = await conversationHistoryApi.getSessionDetail(sessionId);
        const history = hydrateConversationFromReplayFrames(detail);
        const hydrated = (history.chatList || []).find(
          (item) => item.requestId === requestId
        );
        if (!hydrated) {
          markFollowEnded(
            currentChat.conclusion || currentChat.multiAgent?.tasks?.length
              ? "SUCCESS"
              : "FAILED"
          );
          return;
        }

        const stillRunning =
          hydrated.loading ||
          String(hydrated.metrics?.status || "").toUpperCase() === "RUNNING";

        if (stillRunning) {
          currentChat = {
            ...hydrated,
            loading: true,
            tip: CONNECTION_LOST_HINT,
            metrics: {
              ...(hydrated.metrics || {}),
              status: "RUNNING",
            },
          };
          followReconnectContextsRef.current.set(requestId, {
            conversationId,
            sessionId,
            requestId,
            productType,
            deepThink: normalizedDeepThink,
            seedChat: { ...currentChat },
          });
          pendingConversation = draftController.replaceLastItem({ ...currentChat });
          scheduleNonChatFlush(true);
          if (streamStillActive) {
            setLoading(true);
          }
          scheduleFollowReconnect(conversationId, requestId, 800);
          return;
        }

        clearFollowReconnectTimer(requestId);
        followReconnectAttemptsRef.current.delete(requestId);
        followReconnectContextsRef.current.delete(requestId);
        clearActiveRun(requestId);
        currentChat = {
          ...hydrated,
          loading: false,
          tip: "",
        };
        const snapshot = conversationSnapshotsRef.current.get(conversationId);
        const baseList =
          snapshot?.chatList ||
          draftController.getSnapshot().chatList ||
          [];
        const nextList = baseList.map((item) =>
          item.requestId === requestId ? { ...currentChat } : item
        );
        const hasItem = nextList.some((item) => item.requestId === requestId);
        const mergedList = hasItem
          ? nextList
          : [...nextList, { ...currentChat }];
        commitConversation(conversationId, {
          ...(snapshot || draftController.getSnapshot()),
          ...history,
          id: conversationId,
          chatList: mergedList,
        });
        if (streamStillActive) {
          setLoading(false);
          const taskData = handleTaskData(
            currentChat,
            normalizedDeepThink,
            currentChat.multiAgent
          );
          setTaskList(taskData.taskList);
          setPlan(taskData.plan);
          setShowAction(
            resolveActionPanelVisibility({
              plan: taskData.plan,
              taskList: taskData.taskList,
            })
          );
        }
      } catch (error) {
        console.warn("resync after follow_idle failed", error);
        markFollowEnded(
          currentChat.conclusion || currentChat.multiAgent?.tasks?.length
            ? "SUCCESS"
            : undefined
        );
      }
    };

    const handleMessage = (data: MESSAGE.Answer) => {
      const eventSeq = Number(data.eventSeq || 0);
      const lastEventSeq = lastEventSeqRef.current.get(requestId) || 0;
      if (eventSeq > 0 && eventSeq <= lastEventSeq) {
        return;
      }
      if (eventSeq > 0) {
        lastEventSeqRef.current.set(requestId, eventSeq);
        updateActiveRunSeq(requestId, eventSeq);
      }
      // 收到任意有效帧说明观察流已经恢复，下一次断开从最短退避重新开始。
      followReconnectAttemptsRef.current.set(requestId, 0);
      const { finished, resultMap, packageType, status } = data;
      const streamStillActive = isActiveStream();

      if (packageType === "follow_idle") {
        void resyncAfterFollowIdle();
        return;
      }

      if (packageType === "follow_pending") {
        // registry 暂无但 ledger 仍 RUNNING：保持 loading，退避后续绑
        const live = liveStreamsRef.current.get(conversationId);
        if (live && live.controller !== abortController) {
          return;
        }
        unbindLiveStream(conversationId, abortController);
        currentChat = {
          ...currentChat,
          tip: CONNECTION_LOST_HINT,
        };
        pendingConversation = draftController.replaceLastItem({ ...currentChat });
        scheduleNonChatFlush(false);
        scheduleFollowReconnect(conversationId, requestId);
        return;
      }

      if (packageType === "heartbeat") {
        if (!isChatMode && streamStillActive && currentChat.loading) {
          const presence = resolveRunPresence({
            loading: true,
            chat: currentChat,
            deepThink: normalizedDeepThink,
            plan: currentChat.plan || currentChat.multiAgent?.plan,
          });
          // 重试等待期间心跳不要冲掉「正在重试」提示
          if (
            presence.hint &&
            currentChat.tip !== presence.hint &&
            !isLlmRetryTip(currentChat.tip)
          ) {
            currentChat = {
              ...currentChat,
              tip: presence.hint,
            };
            pendingConversation = draftController.replaceLastItem({ ...currentChat });
            scheduleNonChatFlush(false);
          }
        }
        return;
      }

      const isTerminalGuardError =
        Boolean(finished) &&
        packageType === "result" &&
        Boolean(data.errorMsg) &&
        !resultMap?.eventData;
      if (isTerminalGuardError) {
        const errorText = data.errorMsg || "当前请求处理失败，请稍后重试";
        if (isChatMode) {
          currentChat = {
            ...currentChat,
            loading: false,
            response: errorText,
            metrics: {
              ...(currentChat.metrics || {}),
              status: "FAILED",
            },
          };
          clearActiveRun(requestId);
          followReconnectContextsRef.current.delete(requestId);
          unbindLiveStream(conversationId, abortController);
          if (streamStillActive) {
            setLoading(false);
          }
          syncRunningConversation();
          return;
        }
        currentChat = applyGuardError(currentChat, errorText);
        clearActiveRun(requestId);
        followReconnectContextsRef.current.delete(requestId);
        unbindLiveStream(conversationId, abortController);
        if (streamStillActive) {
          setLoading(false);
        }
        draftController.commit(draftController.replaceLastItem({ ...currentChat }));
        return;
      }

      if (["roleUnavailable", "roleSwitchRejected", "noAvailableChatRole"].includes(status)) {
        currentChat = {
          ...currentChat,
          response: data.errorMsg || "当前角色暂不可用",
          loading: false,
          metrics: {
            ...(currentChat.metrics || {}),
            status: "FAILED",
          },
        };
        clearActiveRun(requestId);
        unbindLiveStream(conversationId, abortController);
        if (streamStillActive) {
          setLoading(false);
        }
        syncRunningConversation();
        return;
      }

      if (isChatMode) {
        const eventData = normalizeEventData(resultMap?.eventData);
        const inner = eventData?.resultMap;
        const innerType = inner?.messageType;
        if (innerType === "agent_stream") {
          const text = inner?.result || "";
          if (text) {
            currentChat.response = `${currentChat.response || ""}${text}`;
          }
        } else if (innerType === "result" && !currentChat.response) {
          currentChat.response = inner?.result || "";
        }
        if (innerType) {
          syncRunningConversation();
        }
        if (innerType && (inner?.finish || finished)) {
          markFollowEnded("SUCCESS");
        }
        return;
      }

      const eventData = normalizeEventData(resultMap?.eventData);
      if (!eventData) {
        return;
      }
      const isPlanThoughtEvent = eventData.messageType === "plan_thought";
      const isPlanThoughtFinal = Boolean(eventData.resultMap?.isFinal || finished);
      currentChat = combineData(eventData, currentChat);
      if (eventData.resultMap?.messageType === "result") {
        currentChat.conclusion = buildTaskFromEventData(eventData) as CHAT.Task;
      }
      if (streamStillActive && shouldRefreshWorkspaceTask(eventData)) {
        scheduleWorkspaceStreamTask(currentChat, finished);
      }
      if (streamStillActive && normalizedDeepThink && isPlanThoughtEvent) {
        const latestThought = currentChat.thought || currentChat.multiAgent.plan_thought || "";
        scheduleStreamingThought(currentChat.requestId, latestThought, isPlanThoughtFinal);
      }
      if (!isPlanThoughtEvent) {
        taskDataDirty = true;
      }
      if (finished) {
        clearActiveRun(requestId);
        followReconnectContextsRef.current.delete(requestId);
        followReconnectAttemptsRef.current.delete(requestId);
        clearFollowReconnectTimer(requestId);
        unbindLiveStream(conversationId, abortController);
        currentChat.loading = false;
        currentChat.tip = "";
        currentChat.metrics = {
          ...(currentChat.metrics || {}),
          status: "SUCCESS",
        };
        if (streamStillActive) {
          setLoading(false);
        }
        if (streamStillActive && normalizedDeepThink) {
          const finalThought = currentChat.thought || currentChat.multiAgent.plan_thought || "";
          scheduleStreamingThought(currentChat.requestId, finalThought, true);
        }
      } else if (isLlmRetryEvent(eventData)) {
        // combineData 已写入重试 tip，勿被 presence 覆盖
        currentChat = {
          ...currentChat,
          tip: currentChat.tip,
        };
      } else {
        const presence = resolveRunPresence({
          loading: true,
          chat: currentChat,
          deepThink: normalizedDeepThink,
          plan: currentChat.plan || currentChat.multiAgent?.plan,
        });
        currentChat = {
          ...currentChat,
          tip: presence.hint,
        };
      }
      draftController.replaceLastItem({ ...currentChat });
      if (!isPlanThoughtEvent || isPlanThoughtFinal) {
        pendingConversation = draftController.getSnapshot();
        const forceTimeline =
          finished ||
          isLlmRetryEvent(eventData) ||
          eventData.messageType === "tool_thought" ||
          eventData.messageType === "llm_reasoning" ||
          eventData.messageType === "tool_call" ||
          eventData.messageType === "tool_result" ||
          eventData.resultMap?.messageType === "tool_call" ||
          eventData.resultMap?.messageType === "tool_result" ||
          eventData.resultMap?.messageType === "ui_tree" ||
          eventData.resultMap?.messageType === "ui_patch";
        scheduleNonChatFlush(forceTimeline);
      }
    };

    querySSE(
      {
        body: {
          sessionId,
          requestId,
          lastEventSeq: initialEventSeq,
        },
        signal: abortController.signal,
        retryOnError: false,
        handleEventId: (eventId) => updateActiveRunEvent(requestId, eventId),
        parser: parseAgentAnswer,
        handleMessage,
        handleError: (error) => {
          console.error("follow SSE error", error);
          if (!currentChat.loading) {
            return;
          }
          const live = liveStreamsRef.current.get(conversationId);
          if (live && live.controller !== abortController) {
            return;
          }
          unbindLiveStream(conversationId, abortController);
          currentChat = {
            ...currentChat,
            tip: CONNECTION_LOST_HINT,
          };
          pendingConversation = draftController.replaceLastItem({ ...currentChat });
          scheduleNonChatFlush(false);
          scheduleFollowReconnect(conversationId, requestId);
        },
        handleClose: () => {
          scheduleNonChatFlush(true);
          if (!currentChat.loading) {
            return;
          }
          const live = liveStreamsRef.current.get(conversationId);
          if (live && live.controller !== abortController) {
            return;
          }
          // EOF 也可能来自代理/浏览器提前收流；只要没有收到 follow_idle，
          // 就继续续绑，避免把仍在后台执行的 run 错误收口为失败。
          unbindLiveStream(conversationId, abortController);
          currentChat = {
            ...currentChat,
            tip: CONNECTION_LOST_HINT,
          };
          pendingConversation = draftController.replaceLastItem({ ...currentChat });
          scheduleNonChatFlush(false);
          scheduleFollowReconnect(conversationId, requestId, 300);
        },
      },
      AGENT_RUN_FOLLOW_SSE_URL
    );
  });

  const scheduleFollowReconnect = useMemoizedFn((
    conversationId: string,
    requestId: string,
    delay?: number
  ) => {
    if (followReconnectTimersRef.current.has(requestId)) {
      return;
    }
    if (hasLiveStream(conversationId, requestId)) {
      return;
    }

    const ctx = followReconnectContextsRef.current.get(requestId);
    if (ctx) {
      followReconnectContextsRef.current.set(requestId, {
        ...ctx,
        conversationId,
        seedChat: {
          ...ctx.seedChat,
          loading: true,
          tip: CONNECTION_LOST_HINT,
        },
      });
    }

    const attempt = followReconnectAttemptsRef.current.get(requestId) ?? 0;
    const retryDelay =
      delay ??
      Math.min(
        FOLLOW_RECONNECT_BASE_DELAY * 2 ** Math.min(attempt, 6),
        FOLLOW_RECONNECT_MAX_DELAY
      );
    followReconnectAttemptsRef.current.set(
      requestId,
      Math.min(attempt + 1, FOLLOW_RECONNECT_ATTEMPT_CAP)
    );
    const timer = window.setTimeout(() => {
      followReconnectTimersRef.current.delete(requestId);
      if (hasLiveStream(conversationId, requestId)) {
        return;
      }
      // 前台/后台均可续绑：用 reconnect context，不依赖当前 conversationRef
      const reconnectCtx = followReconnectContextsRef.current.get(requestId);
      followActiveRun(requestId, reconnectCtx);
    }, retryDelay);
    followReconnectTimersRef.current.set(requestId, timer);
  });

  useEffect(() => {
    if (!runningFollowKey) {
      return;
    }
    const [conversationId, requestId] = runningFollowKey.split("::");
    if (!requestId || !conversationId) {
      return;
    }
    // 同会话已有活流时不要再 follow。
    if (hasLiveStream(conversationId, requestId)) {
      return;
    }
    followActiveRun(requestId);
  }, [followActiveRun, hasLiveStream, runningFollowKey]);

  // 页签恢复 / 网络恢复时，立即对所有仍 RUNNING 的断流 run 触发 follow。
  useEffect(() => {
    const kickReconnect = () => {
      followReconnectContextsRef.current.forEach((ctx, requestId) => {
        if (hasLiveStream(ctx.conversationId, requestId)) {
          return;
        }
        if (followReconnectTimersRef.current.has(requestId)) {
          clearFollowReconnectTimer(requestId);
        }
        scheduleFollowReconnect(ctx.conversationId, requestId, 0);
      });
    };
    const onVisibility = () => {
      if (document.visibilityState === "visible") {
        kickReconnect();
      }
    };
    window.addEventListener("online", kickReconnect);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      window.removeEventListener("online", kickReconnect);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [clearFollowReconnectTimer, hasLiveStream, scheduleFollowReconnect]);

  const sendMessage = useMemoizedFn((inputInfo: CHAT.TInputInfo) => {
    const baseConversation = conversationRef.current;
    const conversationId = baseConversation.id;
    const { message, deepThink, outputStyle } = inputInfo;
    const currentOutputStyle =
      outputStyle ||
      (baseConversation.productType === "chat" ||
      baseConversation.productType === "dataAgent" ||
      isOutputProductType(baseConversation.productType)
        ? baseConversation.productType
        : undefined);
    const isChatMode = currentOutputStyle === "chat";
    const normalizedDeepThink = isChatMode ? false : Boolean(deepThink);

    // 开 SSE 之前拦截：任意其它会话仍在跑（活流 / follow 上下文 / 快照 loading）则直接拒绝。
    // 本会话内允许覆盖重发；跨会话禁止，避免双 SSE 互挤。
    let blockedByOtherRun = false;
    liveStreamsRef.current.forEach((entry, otherId) => {
      if (otherId !== conversationId && !entry.controller.signal.aborted) {
        blockedByOtherRun = true;
      }
    });
    if (!blockedByOtherRun) {
      followReconnectContextsRef.current.forEach((ctx) => {
        if (
          ctx.conversationId !== conversationId &&
          isChatItemRunning(ctx.seedChat)
        ) {
          blockedByOtherRun = true;
        }
      });
    }
    if (!blockedByOtherRun) {
      conversationSnapshotsRef.current.forEach((snapshot, otherId) => {
        if (otherId === conversationId) {
          return;
        }
        if ((snapshot.chatList || []).some((chat) => isChatItemRunning(chat))) {
          blockedByOtherRun = true;
        }
      });
    }
    if (blockedByOtherRun) {
      message.warning(CONCURRENT_RUN_HINT);
      return;
    }

    const requestId = getUniqId();
    clearFollowReconnectTimer(requestId);
    followReconnectAttemptsRef.current.delete(requestId);
    followReconnectContextsRef.current.delete(requestId);
    // 只替换本会话旧流，不影响其他会话后台 SSE。
    const previous = liveStreamsRef.current.get(conversationId);
    previous?.controller.abort();
    const abortController = new AbortController();
    bindForegroundStream(conversationId, requestId, abortController);
    saveActiveRun(baseConversation.sessionId, requestId);
    lastEventSeqRef.current.set(requestId, 0);
    const isActiveStream = () =>
      conversationRef.current.id === conversationId &&
      activeRequestIdRef.current === requestId;
    let currentChat = createRunningChat(
      inputInfo,
      baseConversation.sessionId,
      requestId,
      currentOutputStyle,
      normalizedDeepThink
    );

    if (!isChatMode) {
      currentChat = {
        ...currentChat,
        tip: resolveRunPresence({
          loading: true,
          chat: currentChat,
          deepThink: normalizedDeepThink,
        }).hint,
      };
    }

    followReconnectContextsRef.current.set(requestId, {
      conversationId,
      sessionId: baseConversation.sessionId,
      requestId,
      productType: currentOutputStyle || baseConversation.productType,
      deepThink: normalizedDeepThink,
      seedChat: { ...currentChat },
    });

    if (!isChatMode && normalizedDeepThink) {
      setStreamingThoughtMap((previous) => ({
        ...previous,
        [requestId]: "",
      }));
    }

    const initialConversation = createDraftConversation(baseConversation, {
      chatTitle: message || "",
      productType: currentOutputStyle || baseConversation.productType,
      deepThink: normalizedDeepThink,
      chatList: [...baseConversation.chatList, { ...currentChat }],
    });
    const draftController = createConversationDraftController<CHAT.ChatItem>(
      conversationId,
      initialConversation,
      "chatList",
      commitConversation
    );

    draftController.commit(initialConversation);
    setLoading(true);
    onPrepareStreamingWorkspace?.();

    const syncRunningConversation = () => {
      draftController.commit(
        draftController.replaceLastItem({ ...currentChat })
      );
    };

    /**
     * 流式任务会先把原始事件累积在 multiAgent.tasks，再由 handleTaskData 派生出左侧时间线需要的 chat.tasks。
     * 这里在节流刷新任务视图时，把派生后的 chat 一并回写到会话快照，避免左侧对话区一直停留在旧数据。
     */
    const syncDerivedConversationSnapshot = (nextChat: CHAT.ChatItem) => {
      pendingConversation = draftController.replaceLastItem({ ...nextChat });
    };

    const params = buildAgentStreamRequest({
      sessionId: baseConversation.sessionId,
      requestId,
      message,
      deepThink: normalizedDeepThink,
      outputStyle: currentOutputStyle,
      files: inputInfo.files,
      aiAgentId: inputInfo.aiAgentId,
      fallbackRoleAgentId: baseConversation.role?.agentId,
    });
    let pendingConversation: CHAT.ConversationHistory | null = null;
    let pendingTaskData: ReturnType<typeof handleTaskData> | null = null;
    let taskDataDirty = false;
    // 原始事件先写入 currentChat.multiAgent，再由 handleTaskData 派生工作区/时间线；两类状态不能混为一谈。
    let pendingFlushFrame: number | null = null;
    let lastConversationFlushAt = 0;
    let lastTaskFlushAt = 0;
    const CONVERSATION_FLUSH_INTERVAL = 16;
    /** 工作区 taskList 可略节流；时间线 chat.tasks 必须随事件即时派生 */
    const TASK_FLUSH_INTERVAL = 96;

    const flushNonChatUpdates = (force = false) => {
      if (!pendingConversation && !pendingTaskData && !taskDataDirty) {
        return;
      }

      const now = performance.now();
      // 会话快照与任务面板使用不同刷新频率：前者保持响应，后者避免每个 SSE chunk 都触发重渲染。
      // 只要有新事件就重建 chat.tasks，避免 multiAgent 已更新但时间线仍停在旧快照
      if (taskDataDirty) {
        const derived = handleTaskData(
          currentChat,
          normalizedDeepThink,
          currentChat.multiAgent
        );
        syncDerivedConversationSnapshot(derived.currentChat);
        taskDataDirty = false;
        if (force || now - lastTaskFlushAt >= TASK_FLUSH_INTERVAL) {
          pendingTaskData = derived;
        }
      }

      const shouldFlushConversation =
        !!pendingConversation &&
        (force || now - lastConversationFlushAt >= CONVERSATION_FLUSH_INTERVAL);
      const shouldFlushTask =
        !!pendingTaskData && (force || now - lastTaskFlushAt >= TASK_FLUSH_INTERVAL);
      const streamStillActive = isActiveStream();

      if (shouldFlushTask && pendingTaskData) {
        if (streamStillActive) {
          setTaskList(pendingTaskData.taskList);
          setPlan(pendingTaskData.plan);
          setShowAction(resolveActionPanelVisibility({
            plan: pendingTaskData.plan,
            taskList: pendingTaskData.taskList,
          }));
        }
        pendingTaskData = null;
        lastTaskFlushAt = now;
      }

      if (shouldFlushConversation && pendingConversation) {
        commitConversation(conversationId, pendingConversation);
        pendingConversation = null;
        lastConversationFlushAt = now;
      }
    };

    const scheduleNonChatFlush = (force = false) => {
      if (force) {
        if (pendingFlushFrame) {
          cancelAnimationFrame(pendingFlushFrame);
          pendingFlushFrame = null;
        }
        flushNonChatUpdates(true);
        return;
      }

      if (pendingFlushFrame) {
        return;
      }

      pendingFlushFrame = requestAnimationFrame(() => {
        pendingFlushFrame = null;
        flushNonChatUpdates(false);
        if (pendingConversation || pendingTaskData || taskDataDirty) {
          scheduleNonChatFlush(false);
        }
      });
    };

    const handleMessage = (data: MESSAGE.Answer) => {
      const eventSeq = Number(data.eventSeq || 0);
      const lastEventSeq = lastEventSeqRef.current.get(requestId) || 0;
      if (eventSeq > 0 && eventSeq <= lastEventSeq) {
        return;
      }
      if (eventSeq > 0) {
        lastEventSeqRef.current.set(requestId, eventSeq);
        updateActiveRunSeq(requestId, eventSeq);
      }
      // 收到任意有效帧说明主观察流正常，后续断开不应沿用旧的退避次数。
      followReconnectAttemptsRef.current.set(requestId, 0);
      const { finished, resultMap, packageType, status } = data;
      const streamStillActive = isActiveStream();
      const isTerminalGuardError =
        Boolean(finished) &&
        packageType === "result" &&
        Boolean(data.errorMsg) &&
        !resultMap?.eventData;

      // 结束错误、角色不可用、token 耗尽和普通事件是互斥处理分支；终态分支必须先停止 loading 再落快照。
      if (isTerminalGuardError) {
        const errorText = data.errorMsg || "当前请求处理失败，请稍后重试";
        clearActiveRun(requestId);
        unbindLiveStream(conversationId, abortController);
        if (streamStillActive) {
          setLoading(false);
        }

        if (isChatMode) {
          currentChat = {
            ...currentChat,
            loading: false,
            response: errorText,
            metrics: {
              ...(currentChat.metrics || {}),
              status: "FAILED",
            },
          };
          syncRunningConversation();
          return;
        }

        currentChat = applyGuardError(currentChat, errorText);
        const taskData = handleTaskData(
          currentChat,
          normalizedDeepThink,
          currentChat.multiAgent
        );
        if (streamStillActive) {
          setTaskList(taskData.taskList);
        }
        draftController.commit(
          draftController.replaceLastItem({ ...currentChat })
        );
        return;
      }

      if (["roleUnavailable", "roleSwitchRejected", "noAvailableChatRole"].includes(status)) {
        clearActiveRun(requestId);
        unbindLiveStream(conversationId, abortController);
        currentChat = {
          ...currentChat,
          response: data.errorMsg || "当前角色暂不可用",
          loading: false,
          metrics: {
            ...(currentChat.metrics || {}),
            status: "FAILED",
          },
        };
        if (streamStillActive) {
          setLoading(false);
        }
        syncRunningConversation();
        return;
      }

      if (status === "tokenUseUp") {
        clearActiveRun(requestId);
        unbindLiveStream(conversationId, abortController);
        if (streamStillActive) {
          onTokenUseUp?.();
        }
        const taskData = handleTaskData(
          currentChat,
          normalizedDeepThink,
          currentChat.multiAgent
        );
        currentChat = {
          ...currentChat,
          loading: false,
          metrics: {
            ...(currentChat.metrics || {}),
            status: "FAILED",
          },
        };
        if (streamStillActive) {
          setLoading(false);
          setTaskList(taskData.taskList);
        }
        draftController.commit(
          draftController.replaceLastItem({ ...currentChat })
        );
        return;
      }

      if (packageType === "heartbeat") {
        if (!isChatMode && streamStillActive && currentChat.loading) {
          const presence = resolveRunPresence({
            loading: true,
            chat: currentChat,
            deepThink: normalizedDeepThink,
            plan: currentChat.plan || currentChat.multiAgent?.plan,
          });
          // 重试等待期间心跳不要冲掉「正在重试」提示
          if (
            presence.hint &&
            currentChat.tip !== presence.hint &&
            !isLlmRetryTip(currentChat.tip)
          ) {
            currentChat = {
              ...currentChat,
              tip: presence.hint,
            };
            pendingConversation = draftController.replaceLastItem({ ...currentChat });
            scheduleNonChatFlush(false);
          }
        }
        return;
      }

      if (isChatMode) {
        const eventData = normalizeEventData(resultMap?.eventData);
        const inner = eventData?.resultMap;
        const innerType = inner?.messageType;
        if (innerType === "agent_stream") {
          const text = inner?.result || "";
          if (text) {
            currentChat.response = `${currentChat.response || ""}${text}`;
          }
        } else if (innerType === "result" && !currentChat.response) {
          currentChat.response = inner?.result || "";
        }

        if (innerType) {
          syncRunningConversation();
        }

        if (innerType && (inner?.finish || finished)) {
          clearActiveRun(requestId);
          unbindLiveStream(conversationId, abortController);
          currentChat.loading = false;
          currentChat.metrics = {
            ...(currentChat.metrics || {}),
            status: "SUCCESS",
          };
          if (streamStillActive) {
            setLoading(false);
          }
          syncRunningConversation();
        }
        return;
      }

      const eventData = normalizeEventData(resultMap?.eventData);
      if (!eventData) {
        return;
      }

      const isPlanThoughtEvent = eventData.messageType === "plan_thought";
      const isPlanThoughtFinal = Boolean(eventData.resultMap?.isFinal || finished);
      // combineData 只负责把事件合并进原始会话，后续再根据事件类型触发工作区同步和最终结论覆盖。
      currentChat = combineData(eventData, currentChat);
      // 实时收到最终 result 时，优先用结构化结果覆盖掉临时 agent_stream 结论，
      // 避免界面在当前会话里一直停留在“答案$$$文件名”的原始协议文本。
      if (eventData.resultMap?.messageType === "result") {
        currentChat.conclusion = buildTaskFromEventData(eventData) as CHAT.Task;
      }
      if (streamStillActive && shouldRefreshWorkspaceTask(eventData)) {
        scheduleWorkspaceStreamTask(currentChat, finished);
      }
      if (streamStillActive && normalizedDeepThink && isPlanThoughtEvent) {
        const latestThought = currentChat.thought || currentChat.multiAgent.plan_thought || "";
        scheduleStreamingThought(currentChat.requestId, latestThought, isPlanThoughtFinal);
      }
      if (!isPlanThoughtEvent) {
        taskDataDirty = true;
      }
      if (finished) {
        clearActiveRun(requestId);
        followReconnectContextsRef.current.delete(requestId);
        followReconnectAttemptsRef.current.delete(requestId);
        clearFollowReconnectTimer(requestId);
        unbindLiveStream(conversationId, abortController);
        currentChat.loading = false;
        currentChat.tip = "";
        currentChat.metrics = {
          ...(currentChat.metrics || {}),
          status: "SUCCESS",
        };
        if (streamStillActive) {
          setLoading(false);
        }
        if (streamStillActive && normalizedDeepThink) {
          const finalThought = currentChat.thought || currentChat.multiAgent.plan_thought || "";
          scheduleStreamingThought(currentChat.requestId, finalThought, true);
        }
      } else if (isLlmRetryEvent(eventData)) {
        currentChat = {
          ...currentChat,
          tip: currentChat.tip,
        };
        followReconnectContextsRef.current.set(requestId, {
          conversationId,
          sessionId: baseConversation.sessionId,
          requestId,
          productType: currentOutputStyle || baseConversation.productType,
          deepThink: normalizedDeepThink,
          seedChat: { ...currentChat },
        });
      } else {
        const presence = resolveRunPresence({
          loading: true,
          chat: currentChat,
          deepThink: normalizedDeepThink,
          plan: currentChat.plan || currentChat.multiAgent?.plan,
        });
        currentChat = {
          ...currentChat,
          tip: presence.hint,
        };
        followReconnectContextsRef.current.set(requestId, {
          conversationId,
          sessionId: baseConversation.sessionId,
          requestId,
          productType: currentOutputStyle || baseConversation.productType,
          deepThink: normalizedDeepThink,
          seedChat: { ...currentChat },
        });
      }

      draftController.replaceLastItem({ ...currentChat });
      if (!isPlanThoughtEvent || isPlanThoughtFinal) {
        pendingConversation = draftController.getSnapshot();
        // 过程文 / 工具占位 / 工具结果：强制刷新，避免等工具跑完才整块冒泡
        const forceTimeline =
          finished ||
          isLlmRetryEvent(eventData) ||
          eventData.messageType === "tool_thought" ||
          eventData.messageType === "llm_reasoning" ||
          eventData.messageType === "tool_call" ||
          eventData.messageType === "tool_result" ||
          eventData.resultMap?.messageType === "tool_call" ||
          eventData.resultMap?.messageType === "tool_result" ||
          eventData.resultMap?.messageType === "ui_tree" ||
          eventData.resultMap?.messageType === "ui_patch";
        scheduleNonChatFlush(forceTimeline);
      }
    };

    const handleError = (error: unknown) => {
      console.error("SSE stream error", error);
      if (!currentChat.loading) {
        return;
      }
      const live = liveStreamsRef.current.get(conversationId);
      if (live && live.controller !== abortController) {
        return;
      }
      // 原始 POST 断开后不能重发 query，否则会创建第二个 Agent；先释放旧观察流，
      // 再通过 follow 接口续绑同一个 requestId。
      unbindLiveStream(conversationId, abortController);
      currentChat = {
        ...currentChat,
        tip: CONNECTION_LOST_HINT,
      };
      followReconnectContextsRef.current.set(requestId, {
        conversationId,
        sessionId: baseConversation.sessionId,
        requestId,
        productType: currentOutputStyle || baseConversation.productType,
        deepThink: normalizedDeepThink,
        seedChat: { ...currentChat },
      });
      pendingConversation = draftController.replaceLastItem({ ...currentChat });
      scheduleNonChatFlush(false);
      scheduleFollowReconnect(conversationId, requestId);
    };

    const handleClose = () => {
      scheduleNonChatFlush(true);
      if (!currentChat.loading) {
        return;
      }
      const live = liveStreamsRef.current.get(conversationId);
      if (live && live.controller !== abortController) {
        return;
      }
      // 服务端/代理可能以 EOF 结束响应但没有触发 onerror，同样切到 follow。
      unbindLiveStream(conversationId, abortController);
      currentChat = {
        ...currentChat,
        tip: CONNECTION_LOST_HINT,
      };
      followReconnectContextsRef.current.set(requestId, {
        conversationId,
        sessionId: baseConversation.sessionId,
        requestId,
        productType: currentOutputStyle || baseConversation.productType,
        deepThink: normalizedDeepThink,
        seedChat: { ...currentChat },
      });
      pendingConversation = draftController.replaceLastItem({ ...currentChat });
      scheduleNonChatFlush(false);
      scheduleFollowReconnect(conversationId, requestId, 300);
    };

    querySSE({
      body: params,
      signal: abortController.signal,
      retryOnError: false,
      handleEventId: (eventId) => updateActiveRunEvent(requestId, eventId),
      parser: parseAgentAnswer,
      handleMessage,
      handleError,
      handleClose,
    });
  });

  const regenerateLastMessage = useMemoizedFn(() => {
    const last = conversation.chatList[conversation.chatList.length - 1];
    if (!last || loading) {
      return;
    }

    sendMessage({
      message: last.query,
      outputStyle: toRequestOutputStyle(conversation.productType),
      deepThink: conversation.deepThink,
      aiAgentId: conversation.role?.agentId,
    });
  });

  const stopActiveRun = useMemoizedFn(async () => {
    const requestId = activeRequestIdRef.current;
    const activeConversation = conversationRef.current;
    if (!requestId || !loading) {
      return;
    }
    clearFollowReconnectTimer(requestId);
    followReconnectAttemptsRef.current.delete(requestId);
    try {
      const { agentRunApi } = await import("@/services/agentRun");
      await agentRunApi.stop({
        sessionId: activeConversation.sessionId,
        requestId,
      });
    } catch (error) {
      console.warn("stop run failed", error);
    } finally {
      clearActiveRun(requestId);
      const live = liveStreamsRef.current.get(activeConversation.id);
      if (live && live.requestId === requestId) {
        live.controller.abort();
        unbindLiveStream(activeConversation.id, live.controller);
      }
      if (conversationRef.current.id === activeConversation.id) {
        setLoading(false);
      }
      const chatList = activeConversation.chatList || [];
      const last = chatList[chatList.length - 1];
      if (last && last.requestId === requestId) {
        last.loading = false;
        last.forceStop = true;
        last.tip = "";
        last.metrics = {
          ...(last.metrics || {}),
          status: "STOPPED",
        };
        onConversationChange(activeConversation.id, {
          ...activeConversation,
          chatList: [...chatList],
          updatedAt: Date.now(),
        });
      }
    }
  });

  const injectActiveRun = useMemoizedFn(async (text: string) => {
    const requestId = activeRequestIdRef.current;
    const activeConversation = conversationRef.current;
    const trimmed = (text || "").trim();
    if (!requestId || !loading || !trimmed) {
      return false;
    }
    try {
      const { agentRunApi } = await import("@/services/agentRun");
      const data = await agentRunApi.inject({
        sessionId: activeConversation.sessionId,
        requestId,
        text: trimmed,
      });
      // request 拦截器已解包为 data 字段
      if (!data || data.accepted !== true) {
        console.warn("inject run rejected", data);
        return false;
      }
      return true;
    } catch (error) {
      console.warn("inject run failed", error);
      return false;
    }
  });

  const workspaceCaption = useMemo(() => {
    return resolveWorkspaceCaption(workspaceStreamTask, loading);
  }, [loading, workspaceStreamTask]);

  return {
    taskList,
    workspaceStreamTask,
    workspaceCaption,
    activeRunState,
    setActiveRunState,
    plan,
    showAction,
    changeActionStatus,
    loading,
    streamingThoughtMap,
    sendMessage,
    stopActiveRun,
    injectActiveRun,
    regenerateLastMessage,
  };
}
