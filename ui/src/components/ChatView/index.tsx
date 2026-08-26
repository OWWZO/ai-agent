import { useEffect, useMemo, useRef, useState } from "react";
import { ActionViewItemEnum } from "@/utils";
import querySSE from "@/utils/querySSE";
import { getStableTaskIdentity } from "@/utils/chat";
import Dialogue from "@/components/Dialogue";
import DataDialogue from "@/components/Dialogue/DataDialogue";
import GeneralInput from "@/components/GeneralInput";
import ActionView from "@/components/ActionView";
import { ThinkingPanel } from "@/components/Dialogue/ThinkingPanel";
import { ToolDiffPanel } from "@/components/Dialogue/ToolDiffPanel";
import { AgentDetailPanel } from "@/components/Dialogue/AgentDetailPanel";
import {
  buildEditDiffCode,
  extractEditPath,
  resolveTaskToolArg,
  resolveTaskToolName,
  resolveTaskToolOutput,
  resolveTaskToolStatus,
  toolLabel,
} from "@/components/Dialogue/tools";
import { MoonSpinner } from "@/components/ui/MoonSpinner";
import SessionTaskComposerBar from "./SessionTaskComposerBar";
import BackgroundTasksDock, {
  findAgentTaskByToolCallId,
  findLatestRunningAgentTask,
} from "./BackgroundTasksDock";
import PlanComposerBar from "./PlanComposerBar";
import AskUserQuestionCard from "@/components/Dialogue/AskUserQuestionCard";
import {
  findLatestPendingAskUser,
  resolveHitlDockSlot,
} from "./hitlDockModel";
import { hasPendingAskUserQuestion } from "./streamState";
import { getProductByType, toRequestOutputStyle } from "@/utils/constants";
import { useMemoizedFn } from "ahooks";
import classNames from "classnames";
import { Modal } from "antd";
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation";
import {
  FolderOpen,
  PanelLeftClose,
  PanelRightClose,
  PanelRightOpen,
} from "lucide-react";
import { parseDataChatEvent } from "@/utils/sseParsers";
import type { DataConversationRuntime } from "./chatView.types";
import {
  createConversationDraftController,
  createDraftConversation,
  useConversationStream,
} from "./useConversationStream";
import { canOpenTaskWorkspacePanel } from "./streamState";
import { useWorkspacePanels } from "./useWorkspacePanels";
import GenUiActionBridge from "@/components/genui/GenUiActionBridge";
import { resolveTaskToolCallId } from "@/utils/chat/toolCalls";
import { subAgentLiveRevision } from "@/utils/chat/subAgentChat";
import { collectSessionFileTasks } from "@/components/ActionView/workspaceFiles";
import { collectSessionArtifactFiles } from "@/utils/markdownArtifacts";

type ChatViewApi = {
  openFile: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
};

type Props = {
  inputInfo: CHAT.TInputInfo;
  product?: CHAT.Product;
  conversation: CHAT.ConversationHistory;
  chatRoles: CHAT.FixRole[];
  readOnly?: boolean;
  onConversationChange: (
    conversationId: string,
    nextConversation: CHAT.ConversationHistory
  ) => void;
  onRoleSelect: (role: CHAT.FixRole) => void;
  onInputConsumed?: () => void;
  onTaskListChange?: (taskList: CHAT.Task[]) => void;
  onRegisterApi?: (api: ChatViewApi | null) => void;
  onOpenTaskFiles?: () => void;
  /** 沉浸模式变化：Home 用来收起/展开左侧会话栏 */
  onFocusModeChange?: (immersive: boolean) => void;
};

const getTaskStableKey = (task?: CHAT.Task) => {
  return getStableTaskIdentity(task);
};

const ChatView: ReactorType.FC<Props> = (props) => {
  const {
    inputInfo: inputInfoProp,
    product,
    conversation,
    chatRoles,
    readOnly = false,
    onConversationChange,
    onRoleSelect,
    onInputConsumed,
    onTaskListChange,
    onRegisterApi,
    onOpenTaskFiles,
    onFocusModeChange,
  } = props;

  const [activeTask, setActiveTask] = useState<CHAT.Task>();
  const [workspaceOpenRequested, setWorkspaceOpenRequested] = useState(false);
  /** 打开工作区前缓存要点的文件，避免 ActionView 未挂载时 setFilePreview 丢失 */
  const [pendingPreviewFile, setPendingPreviewFile] = useState<CHAT.TFile>();
  /** Kimi 右侧互斥 detail：thinking | toolDiff | agent | ActionView */
  const [thinkingDetail, setThinkingDetail] = useState<string | null>(null);
  const [toolDiffTask, setToolDiffTask] = useState<CHAT.Task | null>(null);
  const [agentDetail, setAgentDetail] = useState<{
    tool: CHAT.Task;
    chat: CHAT.ChatItem;
  } | null>(null);
  const agentPanelClosedRef = useRef(false);
  const [composerDraft, setComposerDraft] = useState<string | null>(null);
  const {
    leftPanelWidth,
    isDragging,
    isLeftCollapsed,
    isRightCollapsed,
    isFocusMode,
    containerRef,
    handleDragStart,
    handleDragMove,
    handleDragEnd,
    setIsRightCollapsed,
    toggleLeftPanel,
    toggleRightPanel: toggleWorkspaceRightPanel,
    toggleFocusMode,
    exitFocusMode,
  } = useWorkspacePanels();

  // 工作区的打开、任务选择和文件预览与对话流是两套生命周期。这里保留
  // conversation 的最新快照，同时允许 ActionView 尚未挂载时暂存一次文件预览。
  useEffect(() => {
    onFocusModeChange?.(isFocusMode);
  }, [isFocusMode, onFocusModeChange]);
  const actionViewRef = ActionView.useActionView();
  const [modal, contextHolder] = Modal.useModal();
  const conversationRef = useRef(conversation);
  const [dataLoading, setDataLoading] = useState(false);
  const {
    taskList,
    workspaceStreamTask,
    workspaceCaption,
    activeRunState,
    setActiveRunState,
    plan,
    showAction,
    changeActionStatus,
    loading: streamLoading,
    stopActiveRun,
    injectActiveRun,
    streamingThoughtMap,
    sendMessage,
    undoLastUserTurn,
  } = useConversationStream({
    conversation,
    onConversationChange,
    onPrepareStreamingWorkspace: () => {
      // 新一轮请求开始后，工作区恢复自动跟随，避免仍停留在上一轮手动点开的旧任务上。
      setActiveTask(undefined);
      setPendingPreviewFile(undefined);
      setThinkingDetail(null);
      setToolDiffTask(null);
      agentPanelClosedRef.current = false;
      setAgentDetail(null);
      actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
    },
    onTokenUseUp: () => {
      modal.info({
        title: "您的试用次数已用尽",
        content: "如需额外申请，请联系 liyang.1236@jd.com",
      });
    },
  });

  useEffect(() => {
    conversationRef.current = conversation;
  }, [conversation]);

  useEffect(() => {
    // 会话 ID 变化意味着旧的流式草稿、数据加载状态和工作区展开状态都不再
    // 属于当前会话；这里只重置本组件持有的瞬时状态，历史内容由父状态提供。
    setDataLoading(false);
    setWorkspaceOpenRequested(false);
    setThinkingDetail(null);
    setToolDiffTask(null);
    agentPanelClosedRef.current = false;
    setAgentDetail(null);
  }, [conversation.id]);

  useEffect(() => {
    // 任务列表会在流式过程中不断补全字段。保留稳定 key 后用最新任务替换
    // 当前选中对象，既能更新状态，又不会因为对象引用变化丢失用户选择。
    const refreshTask = (prev?: CHAT.Task | null) => {
      if (!prev) return prev ?? null;
      const key = getTaskStableKey(prev);
      if (!key) return prev;
      const matched = taskList.find((task) => getTaskStableKey(task) === key);
      if (matched) return matched;
      if (getTaskStableKey(workspaceStreamTask) === key && workspaceStreamTask) {
        return workspaceStreamTask;
      }
      return prev;
    };

    setActiveTask((prev) => refreshTask(prev) || undefined);
    setToolDiffTask((prev) => refreshTask(prev));
    // Agent 详情优先按 toolCallId 从最新 chat 回找：后台 Dock 打开的卡常不在顶层
    // taskList 身份空间里，仅靠 getTaskStableKey 会一直卡在打开瞬间的快照。
    setAgentDetail((prev) => {
      if (!prev) return prev;
      const latestChat =
        conversation.chatList?.[conversation.chatList.length - 1] || prev.chat;
      const toolCallId =
        resolveTaskToolCallId(prev.tool) ||
        prev.tool.messageId ||
        prev.tool.id ||
        "";
      const fromChat =
        latestChat && toolCallId
          ? findAgentTaskByToolCallId(latestChat, toolCallId)
          : null;
      const nextTool = fromChat || refreshTask(prev.tool);
      if (!nextTool) return prev;
      if (nextTool === prev.tool && latestChat === prev.chat) return prev;
      return { tool: nextTool, chat: latestChat || prev.chat };
    });
  }, [taskList, workspaceStreamTask, conversation.chatList]);

  const commitConversation = useMemoizedFn(
    (conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      // 草稿控制器只提交属于创建它的 conversationId 的结果；updatedAt 在这里
      // 统一刷新，保证普通对话和 DataAgent 对话使用同一持久化入口。
      onConversationChange(conversationId, {
        ...nextConversation,
        updatedAt: Date.now(),
      });
    }
  );

  const updateDataChatFromEvent = useMemoizedFn((
    runtime: DataConversationRuntime,
    event: CHAT.DataChatEvent
  ) => {
    // SSE 事件只增量修改当前数据项，再由 draftController 原子替换列表末项。
    // READY/ERROR 同时收口 loading；只在会话仍匹配时清除全局 dataLoading。
    switch (event.eventType) {
      case "THINK":
        runtime.currentChat.think = event.data;
        break;
      case "CHART_DATA":
        runtime.currentChat.chartData = event.data;
        break;
      case "ERROR":
        runtime.currentChat.error = event.data;
        runtime.currentChat.loading = false;
        if (conversationRef.current.id === runtime.draftController.conversationId) {
          setDataLoading(false);
        }
        break;
      case "READY":
        runtime.currentChat.loading = false;
        if (conversationRef.current.id === runtime.draftController.conversationId) {
          setDataLoading(false);
        }
        break;
      default:
        break;
    }

    const nextConversation = runtime.draftController.replaceLastItem({ ...runtime.currentChat });
    runtime.draftController.commit(nextConversation);
  });

  const openRightWorkspace = useMemoizedFn(() => {
    setWorkspaceOpenRequested(true);
    setIsRightCollapsed(false);
    changeActionStatus(true);
  });

  const openThinkingPanel = useMemoizedFn((text: string) => {
    setThinkingDetail(text);
    setToolDiffTask(null);
    setAgentDetail(null);
    setPendingPreviewFile(undefined);
    setActiveTask(undefined);
    openRightWorkspace();
  });

  const syncThinkingPanel = useMemoizedFn((text: string) => {
    setThinkingDetail((prev) => (prev == null ? prev : text));
  });

  const closeThinkingPanel = useMemoizedFn(() => {
    setThinkingDetail(null);
  });

  const openToolDiffPanel = useMemoizedFn(
    (task: CHAT.Task, chat?: CHAT.ChatItem) => {
      setToolDiffTask(task);
      setThinkingDetail(null);
      setAgentDetail(null);
      setPendingPreviewFile(undefined);
      setActiveTask(undefined);
      openRightWorkspace();
      if (chat) {
        setActiveRunState({
          status: chat.metrics?.status,
          finishedAt: chat.finishedAt,
        });
      }
    }
  );

  const closeToolDiffPanel = useMemoizedFn(() => {
    setToolDiffTask(null);
  });

  const openAgentPanel = useMemoizedFn(
    (task: CHAT.Task, chat: CHAT.ChatItem) => {
      setAgentDetail({ tool: task, chat });
      setThinkingDetail(null);
      setToolDiffTask(null);
      setPendingPreviewFile(undefined);
      setActiveTask(undefined);
      openRightWorkspace();
      setActiveRunState({
        status: chat.metrics?.status,
        finishedAt: chat.finishedAt,
      });
    }
  );

  const closeAgentPanel = useMemoizedFn(() => {
    agentPanelClosedRef.current = true;
    setAgentDetail(null);
  });

  const closeExclusiveDetail = useMemoizedFn(() => {
    if (thinkingDetail != null) {
      closeThinkingPanel();
      return true;
    }
    if (toolDiffTask) {
      closeToolDiffPanel();
      return true;
    }
    if (agentDetail) {
      closeAgentPanel();
      return true;
    }
    return false;
  });

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      if (closeExclusiveDetail()) {
        event.preventDefault();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [closeExclusiveDetail]);

  const changeTask = (task: CHAT.Task, chat?: CHAT.ChatItem) => {
    // Structured data / 子智能体 JSON 观察值当前不渲染，禁止打开空白右侧面板。
    if (!canOpenTaskWorkspacePanel(task)) {
      return;
    }
    // 任务点击同时建立工作区可见性和当前运行快照；先恢复动态跟随，避免
    // 上一轮打开的文件预览遮住用户刚选择的任务。
    setThinkingDetail(null);
    setToolDiffTask(null);
    setAgentDetail(null);
    openRightWorkspace();
    // 工具点击回到「动态」预览，避免被已打开的文件 tab 挡住
    setPendingPreviewFile(undefined);
    actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
    setActiveTask(task);
    setActiveRunState({
      status: chat?.metrics?.status,
      finishedAt: chat?.finishedAt,
    });
  };

  const changeFile = useMemoizedFn((file: CHAT.TFile, chat?: CHAT.ChatItem) => {
    // 只打开右侧预览 tab；左侧文件管理仅由「查看当前会话的文件」入口进入
    setThinkingDetail(null);
    setToolDiffTask(null);
    setAgentDetail(null);
    openRightWorkspace();
    setActiveRunState({
      status: chat?.metrics?.status,
      finishedAt: chat?.finishedAt,
    });
    // 先写入父状态：工作区未挂载时 ref 调用会丢；挂载后由 ActionView 消费。
    // 两条路径同时保留是为了覆盖“首次打开工作区”和“工作区已存在”两种时序。
    setPendingPreviewFile(file);
    actionViewRef.current?.setFilePreview(file);
  });

  const clearPendingPreviewFile = useMemoizedFn(() => {
    setPendingPreviewFile(undefined);
  });

  useEffect(() => {
    onRegisterApi?.({ openFile: changeFile });
    return () => onRegisterApi?.(null);
  }, [changeFile, onRegisterApi]);

  const changePlan = () => {
    setThinkingDetail(null);
    setToolDiffTask(null);
    setAgentDetail(null);
    openRightWorkspace();
    actionViewRef.current?.openPlanView();
  };

  const toolDiffView = useMemo(() => {
    if (!toolDiffTask) return null;
    const name = resolveTaskToolName(toolDiffTask);
    const arg = resolveTaskToolArg(toolDiffTask);
    return {
      title: toolLabel(name),
      path: extractEditPath(arg),
      diffCode: buildEditDiffCode({ name, arg }),
      output: resolveTaskToolOutput(toolDiffTask),
      status: resolveTaskToolStatus(toolDiffTask),
    };
  }, [toolDiffTask]);

  const toggleRightPanel = useMemoizedFn(() => {
    const nextOpen = isRightCollapsed;
    setWorkspaceOpenRequested(nextOpen);
    changeActionStatus(isRightCollapsed);
    toggleWorkspaceRightPanel();
  });

  const closeMobileWorkspace = useMemoizedFn(() => {
    setThinkingDetail(null);
    setToolDiffTask(null);
    setAgentDetail(null);
    agentPanelClosedRef.current = true;
    setWorkspaceOpenRequested(false);
    changeActionStatus(false);
    setIsRightCollapsed(true);
    if (isFocusMode) {
      exitFocusMode();
    }
  });

  const sendDataMessage = useMemoizedFn((inputInfo: CHAT.TInputInfo) => {
    // DataAgent 不复用普通 Agent 的运行账本：先基于当前会话创建带 loading
    // 占位的草稿控制器，再把 SSE THINK/CHART_DATA/READY/ERROR 事件逐个合并回末项。
    const baseConversation = conversationRef.current;
    const conversationId = baseConversation.id;
    const params = {content: inputInfo.message,};
    const currentChat: CHAT.DataChatItem = {
      query: inputInfo.message,
      loading: true,
      think: "",
      chartData: undefined,
      error: "",
    };
    const initialConversation = createDraftConversation(baseConversation, {
      chatTitle: inputInfo.message || "",
      productType: "dataAgent",
      deepThink: false,
      dataChatList: [...baseConversation.dataChatList, { ...currentChat }],
    });
    const draftController = createConversationDraftController<CHAT.DataChatItem>(
      conversationId,
      initialConversation,
      "dataChatList",
      commitConversation
    );

    // 先提交 optimistic 草稿，用户可以立即看到本次查询；后续事件只更新这个
    // controller，不直接依赖闭包里的 conversation，避免流期间父状态更新导致覆盖。
    draftController.commit(initialConversation);
    setDataLoading(true);

    const runtime: DataConversationRuntime = {
      draftController,
      currentChat,
    };

    const handleMessage = (data: CHAT.DataChatEvent) => {
      updateDataChatFromEvent(runtime, data);
    };
    const handleError = (error: unknown) => {
      console.error("DataAgent SSE stream error", error);
      if (conversationRef.current.id !== conversationId) {
        return;
      }
      runtime.currentChat = {
        ...runtime.currentChat,
        loading: false,
        error: "连接暂时断开，请重试",
      };
      setDataLoading(false);
      runtime.draftController.commit(
        runtime.draftController.replaceLastItem({ ...runtime.currentChat })
      );
    };

    const handleClose = () => {
      console.log("close");
    };
    querySSE(
      {
        body: params,
        parser: parseDataChatEvent,
        handleMessage,
        handleError,
        handleClose,
      },
      `${SERVICE_BASE_URL}/data/chatQuery`
    );
  });

  useEffect(() => {
    if (readOnly) {
      return;
    }
    if (inputInfoProp.message?.length !== 0) {
      // 输入路由以 outputStyle 和 deepThink 决定协议：普通/深度请求交给
      // useConversationStream，只有非深度 dataAgent 才走独立的 DataAgent SSE。
      const targetOutput =
        inputInfoProp.outputStyle || conversationRef.current.productType;
      if (targetOutput === "dataAgent" && !inputInfoProp.deepThink) {
        sendDataMessage(inputInfoProp);
      } else {
        sendMessage(inputInfoProp);
      }
      onInputConsumed?.();
    }
  }, [inputInfoProp, onInputConsumed, readOnly, sendDataMessage, sendMessage]);

  const handleUndoLastTurn = useMemoizedFn(() => {
    if (loading) {
      return;
    }
    modal.confirm({
      title: "撤销上一轮对话？",
      content: "将删除最后一轮问答，并把你的问题填回输入框以便修改后重发。",
      okText: "撤销",
      cancelText: "取消",
      onOk: () => {
        const query = undoLastUserTurn();
        if (query) {
          setComposerDraft(query);
        }
      },
    });
  });

  const clearComposerDraft = useMemoizedFn(() => {
    setComposerDraft(null);
  });

  const loading = streamLoading || dataLoading;
  const optimisticDataChat = useMemo(() => {
    const targetOutput = inputInfoProp.outputStyle || conversation.productType;
    const lastDataChat = conversation.dataChatList[conversation.dataChatList.length - 1];
    const latestChatAlreadyHydrated =
      lastDataChat?.loading &&
      lastDataChat.query === inputInfoProp.message &&
      !lastDataChat.chartData &&
      !lastDataChat.error;
    // 如果真正的草稿尚未回写父状态，先渲染一个临时末项；一旦检测到同查询的
    // loading 项已存在，就停止追加，避免同一条用户输入显示两次。
    const shouldRenderOptimisticPlaceholder =
      targetOutput === "dataAgent" &&
      !inputInfoProp.deepThink &&
      inputInfoProp.message?.length > 0 &&
      !latestChatAlreadyHydrated;

    if (!shouldRenderOptimisticPlaceholder) {
      return undefined;
    }

    return {
      query: inputInfoProp.message,
      loading: true,
      think: "",
      chartData: undefined,
      error: "",
    } satisfies CHAT.DataChatItem;
  }, [
    conversation.dataChatList,
    conversation.productType,
    inputInfoProp.deepThink,
    inputInfoProp.message,
    inputInfoProp.outputStyle,
  ]);

  const currentProduct = useMemo(() => {
    return getProductByType(conversation.productType || product?.type);
  }, [conversation.productType, product?.type]);

  // 会话级文件任务：跨多轮累计，避免新请求清空侧栏文件列表
  const sessionFileTasks = useMemo(
    () => collectSessionFileTasks(conversation.chatList, taskList),
    [conversation.chatList, taskList]
  );

  useEffect(() => {
    // 侧栏「会话文件」消费会话级任务，而非仅当前轮 taskList
    onTaskListChange?.(sessionFileTasks || []);
  }, [sessionFileTasks, onTaskListChange]);

  // 会话级产物表：终答 Markdown 相对引用（report.md）跨轮可解析
  const sessionArtifactFiles = useMemo(
    () => collectSessionArtifactFiles(conversation.chatList),
    [conversation.chatList]
  );

  const hasWorkspaceContent = Boolean(
    showAction ||
      workspaceOpenRequested ||
      thinkingDetail ||
      toolDiffTask ||
      agentDetail ||
      activeTask ||
      workspaceStreamTask ||
      taskList.length ||
      sessionFileTasks.length
  );

  const renderMobileWorkspaceTrigger = () =>
    hasWorkspaceContent ? (
      <button
        type="button"
        onClick={toggleRightPanel}
        className="reactor-mobile-workspace-trigger flex h-8 w-8 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)] lg:hidden"
        title="打开工作区"
        aria-label="打开工作区"
      >
        <PanelRightOpen className="h-4 w-4" />
      </button>
    ) : null;

  const activeChat = conversation.chatList?.[conversation.chatList.length - 1];
  const liveAgentDetail = useMemo(() => {
    if (!agentDetail) {
      return null;
    }
    const liveChat =
      conversation.chatList.find(
        (item) => item.requestId === agentDetail.chat.requestId
      ) || agentDetail.chat;
    const toolId =
      resolveTaskToolCallId(agentDetail.tool) ||
      agentDetail.tool.messageId ||
      agentDetail.tool.id ||
      "";
    const liveTool =
      findAgentTaskByToolCallId(liveChat, toolId) || agentDetail.tool;
    return { tool: liveTool, chat: liveChat };
  }, [agentDetail, conversation.chatList]);
  // children 在 handleTaskData 里原地 push，chatList/tool 引用经常不变；
  // 每轮渲染读当前 children，才能把子 Agent 轨迹推进右侧面板。
  const agentLiveRevision = liveAgentDetail
    ? subAgentLiveRevision(liveAgentDetail.tool)
    : "";

  useEffect(() => {
    if (agentPanelClosedRef.current || agentDetail) {
      return;
    }
    if (thinkingDetail != null || toolDiffTask) {
      return;
    }
    if (!(streamLoading || activeChat?.loading)) {
      return;
    }
    const tool = findLatestRunningAgentTask(activeChat);
    if (!tool) {
      return;
    }
    openAgentPanel(tool, activeChat);
  }, [
    activeChat,
    agentDetail,
    openAgentPanel,
    streamLoading,
    thinkingDetail,
    toolDiffTask,
  ]);
  const waitingUserInput =
    String(activeChat?.metrics?.status || "").toUpperCase() === "WAITING_INPUT" ||
    activeChat?.tip === "需要你的帮助" ||
    hasPendingAskUserQuestion(activeChat);
  const hitlDockSlot = useMemo(
    () => resolveHitlDockSlot(activeChat, taskList),
    [activeChat, taskList]
  );
  const pendingAskTool = useMemo(
    () =>
      hitlDockSlot === "ask"
        ? findLatestPendingAskUser(activeChat, taskList)
        : undefined,
    [activeChat, taskList, hitlDockSlot]
  );

  // 顶栏优先展示运行状态；空闲时回到会话标题，让工作区始终有明确上下文。
  const headerStatus = useMemo(() => {
    if (waitingUserInput) {
      return activeChat?.tip?.trim() || "需要你的帮助";
    }
    if (!(loading || activeChat?.loading)) {
      return "";
    }
    const tip = activeChat?.tip?.trim();
    return tip || "正在推进任务…";
  }, [activeChat?.loading, activeChat?.tip, loading, waitingUserInput]);

  const headerTitle = useMemo(() => {
    const title = conversation.chatTitle?.trim() || conversation.title?.trim();
    if (title && title !== "新对话") {
      return title;
    }
    return currentProduct?.name || "新任务";
  }, [conversation.chatTitle, conversation.title, currentProduct?.name]);

  /** 底部 Dock：companions 常驻；pending HITL 替换 Composer */
  const renderComposerStack = (inputKey: string) => (
    <>
      <BackgroundTasksDock chat={activeChat} onOpenAgent={openAgentPanel} />
      <SessionTaskComposerBar chat={activeChat} taskList={taskList} />
      {hitlDockSlot === "ask" && pendingAskTool ? (
        <div className="mb-1" data-testid="hitl-dock-ask">
          <AskUserQuestionCard tool={pendingAskTool} />
        </div>
      ) : hitlDockSlot === "approval" ? (
        <div className="mb-1" data-testid="hitl-dock-approval">
          <PlanComposerBar
            chat={activeChat}
            taskList={taskList}
            structuredPlan={activeChat?.plan}
            loading={loading}
          />
        </div>
      ) : (
        <>
          <PlanComposerBar
            chat={activeChat}
            taskList={taskList}
            structuredPlan={activeChat?.plan}
            loading={loading}
          />
          <GeneralInput
            key={inputKey}
            sessionId={conversation.sessionId}
            contextUsage={activeChat?.contextUsage ?? null}
            placeholder={
              conversation.role?.available === false
                ? "当前角色已失效，请新建对话后重新选择角色"
                : loading
                  ? "任务进行中，可发送指导…"
                  : "希望 Reactor 为你做哪些任务呢？"
            }
            showBtn={false}
            size="medium"
            busy={loading}
            disabled={!loading && conversation.role?.available === false}
            onStop={loading ? () => void stopActiveRun() : undefined}
            onInject={loading ? (text) => void injectActiveRun(text) : undefined}
            draftMessage={composerDraft}
            onDraftConsumed={clearComposerDraft}
            product={currentProduct}
            deepThink={conversation.deepThink}
            displayOutput={currentProduct}
            chatRole={conversation.role}
            chatRoles={chatRoles}
            showRoleSelector={false}
            onRoleSelect={onRoleSelect}
            send={(info) =>
              sendMessage({
                ...info,
                outputStyle: toRequestOutputStyle(conversation.productType),
                deepThink: conversation.deepThink,
                aiAgentId: conversation.role?.agentId,
              })
            }
          />
        </>
      )}
    </>
  );

  const renderHeaderStatus = (opts?: { showDeepThink?: boolean; badge?: string }) => (
    <div className="flex min-w-0 items-center gap-2.5">
      <span
        className={classNames(
          "reactor-status-dot shrink-0",
          headerStatus && "is-running"
        )}
        aria-hidden="true"
      />
      {headerStatus ? (
        <div
          className="thinking-shimmer truncate text-[14px] font-semibold tracking-tight text-[var(--chat-text-soft)]"
          role="status"
          aria-live="polite"
        >
          {headerStatus}
        </div>
      ) : (
        <div className="min-w-0 truncate text-[14px] font-semibold tracking-[-0.01em] text-[var(--chat-text)]">
          {headerTitle}
        </div>
      )}
      {opts?.showDeepThink && conversation.deepThink ? (
        <div className="flex shrink-0 items-center gap-1.5 rounded-[6px] bg-[var(--chat-surface-muted)] px-2.5 py-1 text-[11px] font-medium text-[var(--chat-text-soft)]">
          <i className="font_family icon-shendusikao text-[11px]"></i>
          <span>深度研究</span>
        </div>
      ) : null}
      {opts?.badge ? (
        <div className="flex shrink-0 items-center gap-1.5 rounded-[6px] bg-[var(--chat-surface-muted)] px-2.5 py-1 text-[11px] font-medium text-[var(--chat-text-soft)]">
          <i className="font_family icon-shendusikao text-[11px]"></i>
          <span>{opts.badge}</span>
        </div>
      ) : null}
    </div>
  );

  const renderChatDialogues = () => {
    const lastRequestId =
      conversation.chatList[conversation.chatList.length - 1]?.requestId;
    return (
    <>
      {conversation.chatList.map((chat) => (
        <Dialogue
          key={chat.requestId}
          chat={chat}
          streamingThought={streamingThoughtMap[chat.requestId]}
          deepThink={conversation.deepThink}
          sessionArtifactFiles={sessionArtifactFiles}
          changeTask={changeTask}
          changeFile={changeFile}
          changePlan={changePlan}
          onUndo={
            !readOnly &&
            !loading &&
            chat.requestId === lastRequestId
              ? handleUndoLastTurn
              : undefined
          }
          thinkingPanelOpen={thinkingDetail != null}
          onOpenThinking={openThinkingPanel}
          onSyncThinking={syncThinkingPanel}
          onOpenToolDiff={openToolDiffPanel}
          onOpenAgent={openAgentPanel}
        />
      ))}
      {streamLoading ? (
        <div
          className="mt-3 flex items-center gap-2 pl-1 text-[var(--color-text-muted)]"
          role="status"
          aria-live="polite"
        >
          <MoonSpinner label="正在等待回复…" />
        </div>
      ) : null}
    </>
    );
  };

  const renderDataDialogues = () => {
    const visibleDataChats = optimisticDataChat
      ? [...conversation.dataChatList, optimisticDataChat]
      : conversation.dataChatList;

    return (
      <>
        {visibleDataChats.map((chat, idx) => (
          <DataDialogue key={`${conversation.id}-${idx}`} chat={chat} />
        ))}
      </>
    );
  };

  const renderMultAgent = () => {
    // 如果没有工作空间内容，显示单面板
    if (!showAction && !workspaceOpenRequested && thinkingDetail == null) {
      return (
        <div className="reactor-single-chat-shell flex h-full w-full justify-center overflow-hidden bg-[var(--color-bg)] px-4 pt-4 md:px-6">
          <div
            className="flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden"
            id="chat-view"
          >
            <div className="reactor-chat-header mb-3 flex min-h-[36px] items-center justify-between px-1">
              {renderHeaderStatus({ showDeepThink: true })}
              <div className="flex shrink-0 items-center gap-1">
                {renderMobileWorkspaceTrigger()}
                <button
                  type="button"
                  onClick={() => onOpenTaskFiles?.()}
                  className="flex h-8 w-8 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                  title="查看当前会话的文件"
                  aria-label="查看当前会话的文件"
                >
                  <FolderOpen className="h-4 w-4" />
                </button>
              </div>
            </div>

            <Conversation
              key={conversation.id}
              className="chat-fade-bottom min-h-0 flex-1 overflow-hidden"
            >
              <ConversationContent className="mx-auto w-full max-w-[860px] px-1 pb-6">
                {renderChatDialogues()}
              </ConversationContent>
              <ConversationScrollButton />
            </Conversation>

            {!readOnly ? (
              <div
                 className="shrink-0 bg-[var(--color-bg)] pb-5 pt-4"
                data-composer-dock="true"
              >
                <div className="mx-auto w-full max-w-[860px]">
                  {renderComposerStack(`input-${conversation.sessionId}-single`)}
                </div>
              </div>
            ) : null}
          </div>
        </div>
      );
    }

    // 双面板布局；沉浸模式：对话区窄列保留，工作区主导（侧栏由 Home 收起）
    return (
      <div
        ref={containerRef}
        className={classNames(
          "reactor-chat-workspace-layout flex h-full w-full gap-1.5 bg-[var(--color-bg)] p-1.5 md:p-2",
          isDragging && "cursor-col-resize select-none"
        )}
        data-workspace-open={isRightCollapsed ? "false" : "true"}
        data-chat-read-only={readOnly ? "true" : "false"}
      >
        {/* Left Panel - Chat Area */}
        <div
          className={classNames(
            "reactor-chat-panel-left flex min-h-0 flex-col overflow-hidden bg-[var(--color-bg)]",
            isDragging
              ? "transition-none"
              : "transition-[width,min-width,max-width,opacity] duration-[280ms] ease-[cubic-bezier(0.77,0,0.175,1)]",
            isLeftCollapsed && !isFocusMode && "w-14 min-w-14",
            (!isLeftCollapsed || isFocusMode) && !isRightCollapsed && "shrink-0",
            isRightCollapsed && !isLeftCollapsed && !isFocusMode && "flex-1"
          )}
          style={{
            ...((!isLeftCollapsed || isFocusMode)
              ? {
                width: isRightCollapsed && !isFocusMode
                  ? undefined
                  : `${leftPanelWidth}%`,
                minWidth: isFocusMode ? 280 : undefined,
                maxWidth: isFocusMode ? "28%" : undefined,
              }
              : {}),
          }}
        >
          {isLeftCollapsed && !isFocusMode ? (
            // 折叠状态
            <div className="flex h-full flex-col items-center py-4">
              <button
                onClick={toggleLeftPanel}
                className="flex h-10 w-10 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                title="展开聊天区"
              >
                <PanelRightClose className="h-5 w-5" />
              </button>
            </div>
          ) : (
            // 展开状态（含沉浸窄列）
            <>
              {/* Header：运行时显示状态，空闲时显示会话标题 */}
              <div
                className={classNames(
                  "reactor-chat-header flex items-center justify-between border-b border-[var(--color-line)] py-3.5",
                  isFocusMode ? "px-3" : "px-5"
                )}
              >
                {renderHeaderStatus({ showDeepThink: !isFocusMode })}
                {!isFocusMode ? (
                  <div className="flex shrink-0 items-center gap-1">
                    {renderMobileWorkspaceTrigger()}
                    <button
                      type="button"
                      onClick={() => onOpenTaskFiles?.()}
                      className="flex h-8 w-8 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                      title="查看当前会话的文件"
                      aria-label="查看当前会话的文件"
                    >
                      <FolderOpen className="h-4 w-4" />
                    </button>
                  </div>
                ) : null}
              </div>

              {/* Messages */}
              <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
                <Conversation
                  key={conversation.id}
                  className={classNames(
                    "chat-fade-bottom min-h-0 flex-1 overflow-hidden pt-5",
                    isFocusMode ? "px-3" : "px-5"
                  )}
                >
                  <ConversationContent>
                    {renderChatDialogues()}
                  </ConversationContent>
                  <ConversationScrollButton />
                </Conversation>

                {!readOnly ? (
                  <div
                    className={classNames(
                       "shrink-0 bg-[var(--color-bg)] pb-4 pt-3",
                      isFocusMode ? "px-2" : "px-4"
                    )}
                    data-composer-dock="true"
                  >
                    {renderComposerStack(`input-${conversation.sessionId}-left`)}
                  </div>
                ) : null}
              </div>
            </>
          )}
        </div>

        {/* Drag Handle — 沉浸模式锁定比例，不可拖 */}
        {!isFocusMode && !isLeftCollapsed && !isRightCollapsed && (
          <div
            aria-label="调整对话区和工作区宽度"
            role="separator"
            aria-orientation="vertical"
            onPointerDown={handleDragStart}
            onPointerMove={handleDragMove}
            onPointerUp={handleDragEnd}
            onPointerCancel={handleDragEnd}
            className={classNames(
              "reactor-workspace-resizer group relative flex w-4 shrink-0 touch-none cursor-col-resize items-center justify-center rounded-full transition-colors",
              "hover:bg-[var(--chat-accent)]/10",
              isDragging && "bg-[var(--chat-accent)]/15"
            )}
            title="拖拽调整左右区域宽度"
          >
            {/* 宽命中区保障可拖动，内部细线保持界面克制。 */}
            <div className="absolute inset-y-3 left-1/2 w-px -translate-x-1/2 bg-[var(--color-line)] transition-colors group-hover:bg-[var(--color-line-strong)]" />
            <div
              className={classNames(
                "relative h-14 w-1 rounded-full transition-colors duration-150",
                isDragging
                  ? "bg-[var(--chat-accent)]"
                  : "bg-[var(--color-line-strong)] group-hover:bg-[var(--color-text-faint)]"
              )}
            />
          </div>
        )}

        {!isRightCollapsed ? (
          <button
            type="button"
            className="reactor-workspace-scrim lg:hidden"
            aria-label="关闭工作区"
            onClick={closeMobileWorkspace}
          />
        ) : null}

        {/* Right Panel - Action/Workspace Area */}
        <div
          className={classNames(
            "reactor-workspace-panel flex min-h-0 flex-col overflow-hidden bg-[var(--color-bg)]",
            isDragging
              ? "transition-none"
              : "transition-[width,min-width,max-width,opacity] duration-[280ms] ease-[cubic-bezier(0.77,0,0.175,1)]",
            isRightCollapsed && "w-14 min-w-14",
            !isRightCollapsed && "flex-1"
          )}
          data-workspace-collapsed={isRightCollapsed ? "true" : "false"}
        >
          {isRightCollapsed ? (
            // 折叠状态
            <div className="flex h-full flex-col items-center py-4">
              <button
                onClick={toggleRightPanel}
                className="flex h-10 w-10 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                title="展开智能体工作区"
              >
                <PanelLeftClose className="h-5 w-5" />
              </button>
            </div>
          ) : thinkingDetail != null ? (
            <ThinkingPanel
              text={thinkingDetail}
              onClose={() => {
                closeThinkingPanel();
                if (!showAction && !workspaceOpenRequested) {
                  setIsRightCollapsed(true);
                }
              }}
            />
          ) : toolDiffView ? (
            <ToolDiffPanel
              title={toolDiffView.title}
              path={toolDiffView.path}
              diffCode={toolDiffView.diffCode}
              output={toolDiffView.output}
              status={toolDiffView.status}
              onClose={() => {
                closeToolDiffPanel();
                if (!showAction && !workspaceOpenRequested) {
                  setIsRightCollapsed(true);
                }
              }}
            />
          ) : liveAgentDetail ? (
            <AgentDetailPanel
              tool={liveAgentDetail.tool}
              chat={liveAgentDetail.chat}
              liveRevision={agentLiveRevision}
              changeActiveChat={changeTask}
              changePlan={changePlan}
              onOpenToolDiff={openToolDiffPanel}
              onOpenAgent={openAgentPanel}
              onClose={() => {
                closeAgentPanel();
                if (!showAction && !workspaceOpenRequested) {
                  setIsRightCollapsed(true);
                }
              }}
            />
          ) : (
            <ActionView
              activeTask={activeTask}
              streamTask={workspaceStreamTask}
              pendingPreviewFile={pendingPreviewFile}
              onPendingPreviewFileConsumed={clearPendingPreviewFile}
              workspaceCaption={workspaceCaption}
              taskList={sessionFileTasks}
              plan={plan}
              runState={activeRunState}
              isFocusMode={isFocusMode}
              onToggleFocusMode={toggleFocusMode}
              ref={actionViewRef}
              onClose={() => {
                if (isFocusMode) {
                  exitFocusMode();
                } else {
                  setWorkspaceOpenRequested(false);
                  changeActionStatus(false);
                  setIsRightCollapsed(true);
                }
              }}
            />
          )}
        </div>

        {contextHolder}
      </div>
    );
  };

  const renderDataAgent = () => {
    return (
      <div className="reactor-data-chat-shell flex h-full w-full justify-center overflow-hidden bg-[var(--color-surface-sunken)] px-4 pt-4 md:px-6">
        <div
          className="flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden"
          id="chat-view"
        >
          <div className="reactor-chat-header mb-3 flex min-h-[36px] items-center justify-between px-1">
            {renderHeaderStatus({ badge: "数据分析" })}
          </div>

          <Conversation
            key={conversation.id}
            className="chat-fade-bottom min-h-0 flex-1 overflow-hidden"
          >
            <ConversationContent className="mx-auto w-full max-w-[860px] px-1 pb-6">
              {renderDataDialogues()}
            </ConversationContent>
            <ConversationScrollButton />
          </Conversation>

          {!readOnly ? (
            <div
               className="shrink-0 bg-[var(--color-bg)] pb-5 pt-4"
              data-composer-dock="true"
            >
              <div className="mx-auto w-full max-w-[860px]">
                <GeneralInput
                  key={`input-${conversation.sessionId}-data`}
                  sessionId={conversation.sessionId}
                  contextUsage={
                    conversation.chatList?.[conversation.chatList.length - 1]
                      ?.contextUsage ?? null
                  }
                  placeholder={loading ? "任务进行中..." : "希望 Reactor 为你做哪些任务呢？"}
                  showBtn={false}
                  size="medium"
                  busy={loading}
                  disabled={loading}
                  product={currentProduct}
                  deepThink={false}
                  displayOutput={currentProduct}
                  send={(info) =>
                    sendDataMessage({
                      ...info,
                      outputStyle: "dataAgent",
                      deepThink: false,
                    })
                  }
                />
              </div>
            </div>
          ) : null}
        </div>
      </div>
    );
  };

  const isDataConversation =
    conversation.productType === "dataAgent" && !conversation.deepThink;

  const sendGenUiMessage = useMemoizedFn((message: string) => {
    sendMessage({
      message,
      deepThink: conversation.deepThink,
      outputStyle: toRequestOutputStyle(conversation.productType),
      aiAgentId: conversation.role?.agentId,
    });
  });

  return (
    <div className="flex h-full w-full justify-center">
      {!readOnly ? (
        <GenUiActionBridge sendMessage={sendGenUiMessage} busy={loading} />
      ) : null}
      {isDataConversation ? renderDataAgent() : renderMultAgent()}
    </div>
  );
};

export default ChatView;
