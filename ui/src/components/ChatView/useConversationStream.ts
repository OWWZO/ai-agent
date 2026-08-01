import { useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { useMemoizedFn } from "ahooks";
import { isOutputProductType, toRequestOutputStyle } from "@/utils/constants";
import { getUniqId } from "@/utils";
import { buildAgentStreamRequest } from "@/utils/agentRequest";
import {
  buildConversationTaskData,
  buildTaskFromEventData,
  combineData,
  handleTaskData,
  normalizeEventData,
} from "@/utils/chat";
import querySSE from "@/utils/querySSE";
import { parseAgentAnswer } from "@/utils/sseParsers";
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
  regenerateLastMessage: () => void;
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

  const commitConversation = useMemoizedFn(
    (conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      onConversationChange(conversationId, {
        ...nextConversation,
        updatedAt: Date.now(),
      });
    }
  );

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
    workspaceTaskThrottle.reset(undefined);
    thoughtThrottle.reset({});
    setTaskList([]);
    setWorkspaceStreamTask(undefined);
    setActiveRunState(undefined);
    setPlan(undefined);
    setShowAction(false);
    setLoading(false);
    setStreamingThoughtMap({});
  }, [conversation.id, thoughtThrottle.reset, workspaceTaskThrottle.reset]);

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
      workspaceTaskThrottle.cancel();
      thoughtThrottle.cancel();
    };
  }, [thoughtThrottle.cancel, workspaceTaskThrottle.cancel]);

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
    const requestId = getUniqId();
    activeRequestIdRef.current = requestId;
    const isActiveStream = () => conversationRef.current.id === conversationId;
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
      const { finished, resultMap, packageType, status } = data;
      const streamStillActive = isActiveStream();
      const isTerminalGuardError =
        Boolean(finished) &&
        packageType === "result" &&
        Boolean(data.errorMsg) &&
        !resultMap?.eventData;

      if (isTerminalGuardError) {
        const errorText = data.errorMsg || "当前请求处理失败，请稍后重试";
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
          if (presence.hint && currentChat.tip !== presence.hint) {
            currentChat = { ...currentChat, tip: presence.hint };
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
      } else {
        const presence = resolveRunPresence({
          loading: true,
          chat: currentChat,
          deepThink: normalizedDeepThink,
          plan: currentChat.plan || currentChat.multiAgent?.plan,
        });
        currentChat = { ...currentChat, tip: presence.hint };
      }

      draftController.replaceLastItem({ ...currentChat });
      if (!isPlanThoughtEvent || isPlanThoughtFinal) {
        pendingConversation = draftController.getSnapshot();
        // 过程文 / 工具占位 / 工具结果：强制刷新，避免等工具跑完才整块冒泡
        const forceTimeline =
          finished ||
          eventData.messageType === "tool_thought" ||
          eventData.messageType === "llm_reasoning" ||
          eventData.messageType === "tool_call" ||
          eventData.messageType === "tool_result" ||
          eventData.resultMap?.messageType === "tool_call" ||
          eventData.resultMap?.messageType === "tool_result";
        scheduleNonChatFlush(forceTimeline);
      }
    };

    const handleError = (error: unknown) => {
      console.error("SSE stream error", error);
    };

    const handleClose = () => {
      scheduleNonChatFlush(true);
    };

    querySSE({
      body: params,
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
    try {
      const { agentRunApi } = await import("@/services/agentRun");
      await agentRunApi.stop({
        sessionId: activeConversation.sessionId,
        requestId,
      });
    } catch (error) {
      console.warn("stop run failed", error);
    } finally {
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
    regenerateLastMessage,
  };
}
