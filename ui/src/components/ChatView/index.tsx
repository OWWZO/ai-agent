import {
  useCallback,
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { ActionViewItemEnum } from "@/utils";
import querySSE from "@/utils/querySSE";
import { buildConversationTaskData, getStableTaskIdentity } from "@/utils/chat";
import {
  collectPendingReplayIds,
  groupChatsIntoTurns,
  mergeTurnChats,
} from "@/lib/turnGroups";
import Dialogue from "@/components/Dialogue";
import DataDialogue from "@/components/Dialogue/DataDialogue";
import GeneralInput from "@/components/GeneralInput";
import ActionView from "@/components/ActionView";
import { ThinkingPanel } from "@/components/Dialogue/ThinkingPanel";
import { ToolPreviewPanel } from "@/components/Dialogue/ToolPreviewPanel";
import { AgentDetailPanel } from "@/components/Dialogue/AgentDetailPanel";
import {
  buildToolPreviewSource,
  type ToolPreviewSource,
} from "@/components/Dialogue/tools/toolPreviewSource";
import { MoonSpinner } from "@/components/ui/MoonSpinner";
import SessionTaskComposerBar from "./SessionTaskComposerBar";
import BackgroundAgentsPanel from "./BackgroundAgentsPanel";
import type { AgentsPanelVariant } from "./backgroundAgentsModel";
import { projectDockTasks } from "@/utils/chat/agentRuntimeProjector";
import { findAgentTaskByToolCallId, findLatestRunningAgentTask } from "@/utils/chat/taskIdentity";
import PlanComposerBar from "./PlanComposerBar";
import AskUserQuestionCard from "@/components/Dialogue/AskUserQuestionCard";
import DesktopControlCard from "@/components/Dialogue/DesktopControlCard";
import DesktopPreviewPanel from "@/components/ActionView/DesktopPreviewPanel";
import { readDesktopStreamUrl } from "@/components/Dialogue/DesktopControlCard";
import { DESKTOP_CONTROL_PREVIEW_EVENT } from "@/services/desktopControl";
import {
  findLatestPendingAskUser,
  findLatestPendingDesktopControl,
  resolveHitlDockSlot,
} from "./hitlDockModel";
import { hasPendingAskUserQuestion, isParentLoopLive } from "./streamState";
import { getProductByType } from "@/utils/constants";
import { useMemoizedFn } from "ahooks";
import classNames from "classnames";
import { Modal } from "antd";
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation";
import ConversationTurnNavigator from "@/components/Dialogue/ConversationTurnNavigator";
import type { StickToBottomContext } from "use-stick-to-bottom";
import {
  ChevronUp,
  FolderOpen,
  LoaderCircle,
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
import {
  canOpenTaskWorkspacePanel,
  isCanvasPublishPreviewTask,
} from "./streamState";
import { useWorkspacePanels } from "./useWorkspacePanels";
import GenUiActionBridge from "@/components/genui/GenUiActionBridge";
import { identityKeys, readTaskIdentity } from "@/utils/chat/taskIdentity";
import {
  collectChatFileTasks,
  collectSessionFileTasks,
  collectWorkspaceFiles,
  buildSessionFileManifestTask,
  flattenTasksWithFiles,
} from "@/components/ActionView/workspaceFiles";
import { collectSessionArtifactFiles } from "@/utils/markdownArtifacts";
import { getTaskFiles } from "@/utils/taskArtifacts";
import type { PanelItemType } from "@/components/ActionPanel";

type ChatViewApi = {
  openFile: (file: CHAT.TFile, chat?: CHAT.ChatItem) => void;
};

/**
 * 聊天内容的水平内边距。必须放在滚动容器**内层**（ConversationContent），
 * 而不是外层 Conversation —— use-stick-to-bottom 会在两者之间注入无类名的
 * 滚动 div，滚动条画在它身上；外层一旦有 padding，滚动条就距面板边缘 20px。
 *
 * `lg:` 以下清零沿用原移动端媒体查询规则；显式写 `px-0 md:px-0` 是为了让
 * tailwind-merge 清掉 Content 基类里的 `px-1` / `md:px-2`。
 */
function chatContentClassName(
  hasWorkspaceLayout: boolean,
  isFocusMode: boolean
): string {
  if (!hasWorkspaceLayout) {
    return "mx-auto w-full max-w-[860px] px-1 pb-6";
  }
  return isFocusMode ? "px-0 md:px-0 lg:px-3" : "px-0 md:px-0 lg:px-5";
}

type Props = {
  inputInfo: CHAT.TInputInfo;
  inputDraft?: string;
  onInputDraftChange?: (draft: string) => void;
  product?: CHAT.Product;
  conversation: CHAT.ConversationHistory;
  readOnly?: boolean;
  onConversationChange: (
    conversationId: string,
    nextConversation: CHAT.ConversationHistory
  ) => void;
  onInputConsumed?: () => void;
  onTaskListChange?: (
    taskList: ReturnType<typeof collectSessionFileTasks>
  ) => void;
  sessionWorkspaceFiles?: CHAT.TFile[];
  onEnsureSessionFiles?: () => void;
  onRequestRunReplay?: (requestId: string) => Promise<unknown> | void;
  onLoadMoreHistory?: () => Promise<void> | void;
  historyLoading?: boolean;
  onRegisterApi?: (api: ChatViewApi | null) => void;
  onOpenTaskFiles?: () => void;
  /** 沉浸模式变化：Home 用来收起/展开左侧会话栏 */
  onFocusModeChange?: (immersive: boolean) => void;
};

const getTaskStableKey = (task?: CHAT.Task) => {
  return getStableTaskIdentity(task);
};

const getRenderedChat = (chat: CHAT.ChatItem, deepThink: boolean) => {
  if (!(chat.multiAgent?.tasks || []).some((group) => group?.length)) {
    return chat;
  }
  return buildConversationTaskData(chat, deepThink).currentChat;
};

const getFileSnapshot = (file: CHAT.TFile) =>
  [
    file.name,
    file.url,
    file.previewUrl,
    file.downloadUrl,
    file.type,
    file.size,
    file.missing,
    file.missingReason,
    file.resourceKey,
    file.mimeType,
    file.relativePath,
    file.originFileName,
  ]
    .map((value) => String(value ?? ""))
    .join("\u001f");

type SessionFileTaskSnapshotSource = {
  id?: string;
  messageId?: string;
  requestId?: string;
  messageType?: string;
  messageTime?: string;
};

const getSessionFileTaskSnapshot = (task: SessionFileTaskSnapshotSource) =>
  [
    task.id,
    task.messageId,
    task.requestId,
    task.messageType,
    task.messageTime,
    getTaskFiles(task).map(getFileSnapshot).join("\u001e"),
  ]
    .map((value) => String(value ?? ""))
    .join("\u001f");

const sameStringList = (previous: string[], next: string[]) =>
  previous.length === next.length &&
  previous.every((value, index) => value === next[index]);

type SessionFileTasks = ReturnType<typeof collectSessionFileTasks>;

const ChatView: ReactorType.FC<Props> = (props) => {
  const {
    inputInfo: inputInfoProp,
    inputDraft = "",
    onInputDraftChange,
    product,
    conversation,
    readOnly = false,
    onConversationChange,
    onInputConsumed,
    onTaskListChange,
    sessionWorkspaceFiles = [],
    onEnsureSessionFiles,
    onRequestRunReplay,
    onLoadMoreHistory,
    historyLoading = false,
    onRegisterApi,
    onOpenTaskFiles,
    onFocusModeChange,
  } = props;

  const [activeTask, setActiveTask] = useState<CHAT.Task>();
  const [workspaceOpenRequested, setWorkspaceOpenRequested] = useState(false);
  /** 打开工作区前缓存要点的文件，避免 ActionView 未挂载时 setFilePreview 丢失 */
  const [pendingPreviewFile, setPendingPreviewFile] = useState<CHAT.TFile>();
  /**
   * 工具卡推来的 `CodeViewerSource` tab（Stage 4）。
   *
   * 与 `pendingPreviewFile` 同一个理由：ActionView 可能还没挂载，先把要开的 source
   * 暂存在父级，挂载后消费一次再清空。
   */
  const [pendingSourceTab, setPendingSourceTab] = useState<ToolPreviewSource | null>(
    null
  );
  /** Kimi 右侧互斥 detail：thinking | toolDiff | agent | ActionView */
  const [thinkingDetail, setThinkingDetail] = useState<string | null>(null);
  const [toolDiffTask, setToolDiffTask] = useState<CHAT.Task | null>(null);
  /** 多文件 edit：右侧要展示第几个文件的 patch（undefined = 单文件 / 全部） */
  const [toolDiffFileIndex, setToolDiffFileIndex] = useState<number | undefined>(
    undefined
  );
  const [agentDetail, setAgentDetail] = useState<{
    tool: CHAT.Task;
    chat: CHAT.ChatItem;
  } | null>(null);
  /** 右上角「智能体」面板形态：默认 auto（宽屏展开、窄屏胶囊），输入框徽标可强制展开 */
  const [agentsPanelVariant, setAgentsPanelVariant] =
    useState<AgentsPanelVariant>("auto");
  const [workspaceBackTarget, setWorkspaceBackTarget] =
    useState<CHAT.AgentDetailTarget | null>(null);
  const agentPanelClosedRef = useRef(false);
  const lastAutoOpenedDesktopControlRef = useRef("");
  const [composerDraft, setComposerDraft] = useState<string | null>(null);
  const {
    leftPanelWidth,
    isLeftCollapsed,
    isRightCollapsed,
    isFocusMode,
    containerRef,
    leftPanelRef,
    handleDragStart,
    handleDragMove,
    handleDragEnd,
    setIsRightCollapsed,
    toggleLeftPanel,
    toggleRightPanel: toggleWorkspaceRightPanel,
    toggleFocusMode,
    exitFocusMode,
  } = useWorkspacePanels();
  // Streaming snapshots arrive more often than a user can read. Keep the live
  // header/workspace state responsive while allowing the transcript to render
  // at a lower priority during heavy updates and panel resizing.
  const conversationListSnapshot = useMemo(
    () => ({
      id: conversation.id,
      chatList: conversation.chatList
    }),
    [conversation.chatList, conversation.id]
  );
  const deferredConversationList = useDeferredValue(conversationListSnapshot);
  const deferredChatList =
    deferredConversationList.id === conversation.id
      ? deferredConversationList.chatList
      : conversation.chatList;
  /**
   * 同一提问下的续跑 run（AskUserQuestion / ExitPlanMode 之后的 continuation run）
   * 归成一个 turn，只渲染一条「已工作」状态条 —— 对齐 ZCode 的 turn 语义。
   * 合并结果必须 memo：`Dialogue` 是 memo 组件，靠 `chat` 引用相等判断是否重渲染。
   */
  const chatTurns = useMemo(
    () =>
      groupChatsIntoTurns(deferredChatList).map((turn) => ({
        key: turn.key,
        chat: mergeTurnChats(turn),
        /**
         * 整个 turn 的 requestId。历史回放是**按 run** 懒加载的，而合并后的
         * `Dialogue` 只认最后一个 run 的 requestId —— 只补它会让 turn 内其它 run
         * （尤其是 AskUserQuestion 之前那个）永远拿不到过程数据，工具卡整段消失。
         */
        requestIds: turn.chats
          .map((item) => String(item.requestId || ""))
          .filter(Boolean),
        /** 仍需补回放的 run；补齐后随 `deferredChatList` 更新自动缩短。 */
        pendingReplayIds: collectPendingReplayIds(turn.chats),
      })),
    [deferredChatList]
  );
  /**
   * 轮次导航按 turn 出刻度：它的活跃项靠 DOM 锚点（`data-request-id`）测量，
   * 而合并后的 `Dialogue` 只有一个锚点，传原始 chatList 会让刻度与 DOM 错位。
   */
  const turnChatList = useMemo(
    () => chatTurns.map((turn) => turn.chat),
    [chatTurns]
  );

  // 工作区的打开、任务选择和文件预览与对话流是两套生命周期。这里保留
  // conversation 的最新快照，同时允许 ActionView 尚未挂载时暂存一次文件预览。
  useEffect(() => {
    onFocusModeChange?.(isFocusMode);
  }, [isFocusMode, onFocusModeChange]);
  const actionViewRef = ActionView.useActionView();
  const [modal, contextHolder] = Modal.useModal();
  const conversationRef = useRef(conversation);
  const lastAutoOpenedCanvasPreviewKeyRef = useRef("");
  const [dataLoading, setDataLoading] = useState(false);
  const [replayLoadingIds, setReplayLoadingIds] = useState<Set<string>>(
    () => new Set()
  );
  const {
    taskList,
    workspaceStreamTask,
    workspaceCaption,
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
    readOnly,
    onConversationChange,
    onPrepareStreamingWorkspace: () => {
      // 新一轮请求开始后，工作区恢复自动跟随，避免仍停留在上一轮手动点开的旧任务上。
      setActiveTask(undefined);
      setPendingPreviewFile(undefined);
      setThinkingDetail(null);
      setToolDiffTask(null);
      setWorkspaceBackTarget(null);
      agentPanelClosedRef.current = false;
      setAgentDetail(null);
      lastAutoOpenedCanvasPreviewKeyRef.current = "";
      actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
    },
    onTokenUseUp: () => {
      modal.info({
        title: "您的试用次数已用尽",
        content: "如需额外申请，请联系 liyang.1236@jd.com",
      });
    },
  });

  const requestReplay = useCallback(
    async (requestId: string) => {
      if (!onRequestRunReplay) {
        return;
      }
      // 状态条属于整个 turn：把该 turn 里**所有**还没补过回放的 run 依次补上。
      // 只补传入的 requestId（= 最后一个 run）会让提问前的 run 永远没有过程数据。
      const turn = chatTurns.find((item) => item.requestIds.includes(requestId));
      const targets = turn ? turn.pendingReplayIds : [requestId];
      for (const target of targets) {
        if (!target || replayLoadingIds.has(target)) {
          continue;
        }
        setReplayLoadingIds((previous) => {
          const next = new Set(previous);
          next.add(target);
          return next;
        });
        try {
          await onRequestRunReplay(target);
        } finally {
          setReplayLoadingIds((previous) => {
            const next = new Set(previous);
            next.delete(target);
            return next;
          });
        }
      }
    },
    [chatTurns, onRequestRunReplay, replayLoadingIds]
  );

  useEffect(() => {
    conversationRef.current = conversation;
  }, [conversation]);

  useEffect(() => {
    // 会话 ID 变化意味着旧的流式草稿、数据加载状态和工作区展开状态都不再
    // 属于当前会话；这里只重置本组件持有的瞬时状态，历史内容由父状态提供。
    setDataLoading(false);
    setWorkspaceOpenRequested(false);
    setIsRightCollapsed(false);
    setThinkingDetail(null);
    setToolDiffTask(null);
    setWorkspaceBackTarget(null);
    agentPanelClosedRef.current = false;
    setAgentDetail(null);
    lastAutoOpenedCanvasPreviewKeyRef.current = "";
    lastAutoOpenedDesktopControlRef.current = "";
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
      const latestChatSource =
        conversation.chatList?.[conversation.chatList.length - 1] || prev.chat;
      const latestChat = getRenderedChat(latestChatSource, conversation.deepThink);
      const toolCallId = identityKeys(readTaskIdentity(prev.tool))[0] || "";
      const fromChat =
        latestChat && toolCallId
          ? findAgentTaskByToolCallId(latestChat, toolCallId)
          : null;
      const nextTool = fromChat || refreshTask(prev.tool);
      if (!nextTool) return prev;
      if (nextTool === prev.tool && latestChat === prev.chat) return prev;
      return {
        tool: nextTool,
        chat: latestChat || prev.chat
      };
    });
  }, [taskList, workspaceStreamTask, conversation.chatList, conversation.deepThink]);

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
    onEnsureSessionFiles?.();
    setWorkspaceOpenRequested(true);
    setIsRightCollapsed(false);
    changeActionStatus(true);
  });

  const openThinkingPanel = useMemoizedFn((text: string) => {
    setThinkingDetail(text);
    setToolDiffTask(null);
    setAgentDetail(null);
    setPendingPreviewFile(undefined);
    setPendingSourceTab(null);
    setActiveTask(undefined);
    setWorkspaceBackTarget(null);
    openRightWorkspace();
  });

  const syncThinkingPanel = useMemoizedFn((text: string) => {
    setThinkingDetail((prev) => (prev == null ? prev : text));
  });

  const closeThinkingPanel = useMemoizedFn(() => {
    setThinkingDetail(null);
  });

  /**
   * 点工具卡文件名 chip 的统一入口（Stage 4）。
   *
   * 会在侧栏 ActionView 里**新增 / 切换一个 tab**（patch 与文件 tab 共用一条 tab 栏，
   * 对齐 ZCode 行为），而不是只替换右侧单一面板 —— 这样连开多个 diff 不会互相顶掉。
   *
   * `backTarget` 存在时（从子 Agent 详情进来的）仍走独立 `ToolPreviewPanel`，
   * 因为那条路径需要保留「返回子 Agent」按钮。
   */
  const openToolDiffPanel = useMemoizedFn(
    (
      task: CHAT.Task,
      chat?: CHAT.ChatItem,
      backTarget?: CHAT.AgentDetailTarget,
      fileIndex?: number
    ) => {
      const request = buildToolPreviewSource(task, fileIndex);
      if (!request) return;

      setThinkingDetail(null);
      setPendingPreviewFile(undefined);
      setActiveTask(undefined);
      setActiveRunState({
        status: chat?.metrics?.status,
        finishedAt: chat?.finishedAt,
      });

      if (backTarget) {
        // 详情页返回路径：独立面板 + 返回按钮
        setToolDiffTask(task);
        setToolDiffFileIndex(fileIndex);
        setWorkspaceBackTarget(backTarget);
        openRightWorkspace();
        return;
      }

      // 常规路径：只往侧栏塞一个 tab。`source` 是 file 且能映射到会话产物时，
      // ActionView 会把它并进同 key 的文件 tab；否则作为独立 source tab 打开。
      setToolDiffTask(null);
      setToolDiffFileIndex(undefined);
      setAgentDetail(null);
      setWorkspaceBackTarget(null);
      openRightWorkspace();
      setPendingPreviewFile(undefined);
      setPendingSourceTab(request);
    }
  );

  const closeToolDiffPanel = useMemoizedFn(() => {
    setToolDiffTask(null);
    setToolDiffFileIndex(undefined);
    setWorkspaceBackTarget(null);
  });

  const openAgentPanel = useMemoizedFn(
    (
      task: CHAT.Task,
      chat: CHAT.ChatItem,
      backTarget?: CHAT.AgentDetailTarget
    ) => {
      setAgentDetail({
        tool: task,
        chat
      });
      setThinkingDetail(null);
      setToolDiffTask(null);
      setPendingPreviewFile(undefined);
      setPendingSourceTab(null);
      setActiveTask(undefined);
      setWorkspaceBackTarget(backTarget || null);
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
    setWorkspaceBackTarget(null);
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

  const changeTask = useMemoizedFn(
    (
      task: CHAT.Task,
      chat?: CHAT.ChatItem,
      backTarget?: CHAT.AgentDetailTarget
    ) => {
      // Structured data / 子智能体 JSON 观察值当前不渲染，禁止打开空白右侧面板。
      if (!canOpenTaskWorkspacePanel(task)) {
        return;
      }
      // 任务点击同时建立工作区可见性和当前运行快照；先恢复动态跟随，避免
      // 上一轮打开的文件预览遮住用户刚选择的任务。
      setThinkingDetail(null);
      setToolDiffTask(null);
      setAgentDetail(null);
      setWorkspaceBackTarget(backTarget || null);
      openRightWorkspace();
      // 工具点击回到「动态」预览，避免被已打开的文件 tab 挡住
      setPendingPreviewFile(undefined);
      actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
      setActiveTask(task);
      setActiveRunState({
        status: chat?.metrics?.status,
        finishedAt: chat?.finishedAt,
      });
    }
  );

  const changeFile = useMemoizedFn(
    (
      file: CHAT.TFile,
      chat?: CHAT.ChatItem,
      backTarget?: CHAT.AgentDetailTarget
    ) => {
      // 只打开右侧预览 tab；左侧文件管理仅由「查看当前会话的文件」入口进入
      setThinkingDetail(null);
      setToolDiffTask(null);
      setAgentDetail(null);
      setWorkspaceBackTarget(backTarget || null);
      openRightWorkspace();
      setActiveRunState({
        status: chat?.metrics?.status,
        finishedAt: chat?.finishedAt,
      });
      // 先写入父状态：工作区未挂载时 ref 调用会丢；挂载后由 ActionView 消费。
      // 两条路径同时保留是为了覆盖“首次打开工作区”和“工作区已存在”两种时序。
      setPendingPreviewFile(file);
      actionViewRef.current?.setFilePreview(file);
    }
  );

  const clearPendingPreviewFile = useMemoizedFn(() => {
    setPendingPreviewFile(undefined);
  });

  useEffect(() => {
    if (!streamLoading || !workspaceStreamTask) {
      return;
    }
    if (!isCanvasPublishPreviewTask(workspaceStreamTask)) {
      return;
    }
    const files = getTaskFiles(workspaceStreamTask);
    const file = files[0];
    if (!file) {
      return;
    }
    const key =
      file.relativePath ||
      file.url ||
      file.downloadUrl ||
      getStableTaskIdentity(workspaceStreamTask);
    if (!key || lastAutoOpenedCanvasPreviewKeyRef.current === key) {
      return;
    }
    lastAutoOpenedCanvasPreviewKeyRef.current = key;
    const liveChat =
      conversation.chatList[conversation.chatList.length - 1];
    changeFile(file, liveChat);
  }, [changeFile, conversation.chatList, streamLoading, workspaceStreamTask]);

  useEffect(() => {
    onRegisterApi?.({ openFile: changeFile });
    return () => onRegisterApi?.(null);
  }, [changeFile, onRegisterApi]);

  const changePlan = useMemoizedFn(() => {
    setThinkingDetail(null);
    setToolDiffTask(null);
    setAgentDetail(null);
    setWorkspaceBackTarget(null);
    openRightWorkspace();
    actionViewRef.current?.openPlanView();
  });

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
    setWorkspaceBackTarget(null);
    agentPanelClosedRef.current = true;
    setWorkspaceOpenRequested(false);
    changeActionStatus(false);
    setIsRightCollapsed(true);
    if (isFocusMode) {
      exitFocusMode();
    }
  });

  const closeActionView = useMemoizedFn(() => {
    setWorkspaceBackTarget(null);
    if (isFocusMode) {
      exitFocusMode();
      return;
    }
    setWorkspaceOpenRequested(false);
    changeActionStatus(false);
    setIsRightCollapsed(true);
  });

  const returnToAgentDetail = useMemoizedFn(() => {
    const target = workspaceBackTarget;
    if (!target) {
      return;
    }
    openAgentPanel(target.tool, target.chat);
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

  const sessionFileManifestTask = useMemo(
    () => buildSessionFileManifestTask(sessionWorkspaceFiles),
    [sessionWorkspaceFiles]
  );

  // 会话级文件任务：跨多轮累计，避免新请求清空侧栏文件列表
  const sessionFileTasksCacheRef = useRef<{
    conversationId: string;
    tasks: SessionFileTasks;
    snapshots: string[];
    chatTasks: WeakMap<object, PanelItemType[]>;
    liveTaskList?: PanelItemType[];
    liveTasks: PanelItemType[];
  }>({
    conversationId: "",
    tasks: [],
    snapshots: [],
    chatTasks: new WeakMap(),
    liveTasks: [],
  });
  const sessionFileTasks = useMemo(() => {
    const cache = sessionFileTasksCacheRef.current;
    if (cache.conversationId !== conversation.id) {
      cache.conversationId = conversation.id;
      cache.chatTasks = new WeakMap();
      cache.liveTaskList = undefined;
      cache.liveTasks = [];
      cache.tasks = [];
      cache.snapshots = [];
    }

    const next: SessionFileTasks = [];
    if (sessionFileManifestTask) {
      next.push(sessionFileManifestTask);
    }
    for (const chat of conversation.chatList) {
      let chatTasks = cache.chatTasks.get(chat);
      if (!chatTasks) {
        chatTasks = collectChatFileTasks(chat);
        cache.chatTasks.set(chat, chatTasks);
      }
      next.push(...chatTasks);
    }
    if (cache.liveTaskList !== taskList) {
      cache.liveTaskList = taskList;
      cache.liveTasks = flattenTasksWithFiles(taskList);
    }
    next.push(...cache.liveTasks);

    const nextSnapshots = next.map(getSessionFileTaskSnapshot);
    if (
      cache.conversationId === conversation.id &&
      sameStringList(cache.snapshots, nextSnapshots)
    ) {
      return cache.tasks;
    }
    cache.tasks = next;
    cache.snapshots = nextSnapshots;
    return next;
  }, [
    conversation.chatList,
    conversation.id,
    sessionFileManifestTask,
    taskList,
  ]);

  useEffect(() => {
    // 侧栏「会话文件」消费会话级任务，而非仅当前轮 taskList
    onTaskListChange?.(sessionFileTasks || []);
  }, [sessionFileTasks, onTaskListChange]);

  // 会话级产物表：终答 Markdown 相对引用（report.md）跨轮可解析
  const sessionArtifactFilesCacheRef = useRef<{
    conversationId: string;
    files: CHAT.TFile[];
    snapshots: string[];
  }>({
    conversationId: "",
    files: [],
    snapshots: [],
  });
  /**
   * StickToBottom 的 context：消息导航跳转前要 `stopScroll()` 解锁吸底，
   * 否则跳转后会被吸底逻辑拉回底部（`plans/030` 风险 R1）。
   */
  const conversationContextRef = useRef<StickToBottomContext | null>(null);

  const sessionArtifactFiles = useMemo(() => {
    const merged: CHAT.TFile[] = [];
    const seen = new Set<string>();
    for (const file of [
      ...collectWorkspaceFiles(sessionFileTasks),
      ...collectSessionArtifactFiles(deferredChatList),
    ]) {
      const key =
        file.resourceKey ||
        file.relativePath ||
        file.url ||
        file.downloadUrl ||
        file.name;
      if (!key || seen.has(key)) {
        continue;
      }
      seen.add(key);
      merged.push(file);
    }
    const next = merged;
    const nextSnapshots = next.map(getFileSnapshot);
    const cache = sessionArtifactFilesCacheRef.current;
    if (
      cache.conversationId === conversation.id &&
      sameStringList(cache.snapshots, nextSnapshots)
    ) {
      return cache.files;
    }
    sessionArtifactFilesCacheRef.current = {
      conversationId: conversation.id,
      files: next,
      snapshots: nextSnapshots,
    };
    return next;
  }, [conversation.id, deferredChatList, sessionFileTasks]);

  /**
   * 右侧「工具预览」的统一模型（只有从子 Agent 详情返回时才用）。
   *
   * 判定逻辑与 `openToolDiffPanel` 共用 `buildToolPreviewSource`，两者不会漂移。
   */
  const toolDiffView = useMemo(() => {
    if (!toolDiffTask) return null;
    return buildToolPreviewSource(
      toolDiffTask,
      toolDiffFileIndex,
      sessionArtifactFiles
    );
  }, [toolDiffTask, toolDiffFileIndex, sessionArtifactFiles]);

  const activeChat = conversation.chatList?.[conversation.chatList.length - 1];
  const sessionBusy = loading || isParentLoopLive(activeChat);
  /** 后台子智能体条目（右上角面板 + 输入框徽标同源） */
  const backgroundAgentItems = useMemo(() => {
    const sourceChats = conversation.chatList?.length
      ? conversation.chatList
      : activeChat
        ? [activeChat]
        : [];
    const seen = new Set<string>();
    const items: Array<{ id: string; state: string }> = [];
    for (const chat of sourceChats) {
      for (const task of projectDockTasks(chat)) {
        if (!task.id || seen.has(task.id)) {
          continue;
        }
        seen.add(task.id);
        items.push(task);
      }
    }
    return items;
  }, [conversation.chatList, activeChat]);
  const backgroundAgentCount = backgroundAgentItems.length;
  const backgroundAgentRunningCount = backgroundAgentItems.filter(
    (item) => item.state === "run"
  ).length;
  const liveAgentDetail = useMemo(() => {
    if (!agentDetail) {
      return null;
    }
    const liveChatSource =
      conversation.chatList.find(
        (item) => item.requestId === agentDetail.chat.requestId
      ) || agentDetail.chat;
    const liveChat = getRenderedChat(liveChatSource, conversation.deepThink);
    const toolId = identityKeys(readTaskIdentity(agentDetail.tool))[0] || "";
    const liveTool =
      findAgentTaskByToolCallId(liveChat, toolId) || agentDetail.tool;
    return {
      tool: liveTool,
      chat: liveChat
    };
  }, [agentDetail, conversation.chatList, conversation.deepThink]);
  const agentDetailBackTarget = liveAgentDetail
    ? {
      tool: liveAgentDetail.tool,
      chat: liveAgentDetail.chat,
    }
    : undefined;

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
  const pendingDesktopTool = useMemo(
    () =>
      hitlDockSlot === "desktop"
        ? findLatestPendingDesktopControl(activeChat, taskList)
        : undefined,
    [activeChat, taskList, hitlDockSlot]
  );
  const pendingDesktopStreamUrl = readDesktopStreamUrl(pendingDesktopTool);
  const pendingDesktopPreviewKey = pendingDesktopStreamUrl
    ? getStableTaskIdentity(pendingDesktopTool) || pendingDesktopStreamUrl
    : "";

  const openDesktopPreview = useMemoizedFn(() => {
    if (!pendingDesktopStreamUrl) return;
    setThinkingDetail(null);
    setToolDiffTask(null);
    setAgentDetail(null);
    setPendingPreviewFile(undefined);
    setActiveTask(undefined);
    setWorkspaceBackTarget(null);
    openRightWorkspace();
  });

  useEffect(() => {
    if (!pendingDesktopPreviewKey) {
      lastAutoOpenedDesktopControlRef.current = "";
      return;
    }
    if (lastAutoOpenedDesktopControlRef.current === pendingDesktopPreviewKey) {
      return;
    }
    lastAutoOpenedDesktopControlRef.current = pendingDesktopPreviewKey;
    openDesktopPreview();
  }, [openDesktopPreview, pendingDesktopPreviewKey]);

  useEffect(() => {
    if (!pendingDesktopStreamUrl) return;
    window.addEventListener(DESKTOP_CONTROL_PREVIEW_EVENT, openDesktopPreview);
    return () =>
      window.removeEventListener(
        DESKTOP_CONTROL_PREVIEW_EVENT,
        openDesktopPreview
      );
  }, [openDesktopPreview, pendingDesktopStreamUrl]);

  // 活动桌面控制优先显示在右侧；普通工具不自动拉开空「动态」工作区。
  const hasWorkspaceContent = Boolean(
    showAction ||
      workspaceOpenRequested ||
      thinkingDetail ||
      toolDiffTask ||
      agentDetail ||
      pendingSourceTab ||
      pendingDesktopStreamUrl
  );

  const renderWorkspaceReopenTrigger = () =>
    hasWorkspaceContent && isRightCollapsed ? (
      <button
        type="button"
        onClick={toggleRightPanel}
        className="reactor-mobile-workspace-trigger flex h-8 w-8 items-center justify-center rounded-full text-foreground-subtle transition-colors hover:bg-hover hover:text-foreground"
        title="打开工作区"
        aria-label="打开工作区"
      >
        <PanelRightOpen className="h-4 w-4" />
      </button>
    ) : null;

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

  /** Composer 堆栈：任务条常驻；pending HITL 替换 Composer（后台子 Agent 入口在右上角面板 + 输入框徽标） */
  const renderComposerStack = (inputKey: string) => (
    <>
      <SessionTaskComposerBar chat={activeChat} taskList={taskList} />
      {hitlDockSlot === "desktop" && pendingDesktopTool ? (
        <div className="mb-1" data-testid="hitl-dock-desktop">
          <DesktopControlCard tool={pendingDesktopTool} />
        </div>
      ) : hitlDockSlot === "ask" && pendingAskTool ? (
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
            placeholder={sessionBusy ? "任务进行中，可发送指导…" : "希望 Reactor 为你做哪些任务呢？"}
            showBtn={false}
            size="medium"
            busy={sessionBusy}
            disabled={false}
            onStop={sessionBusy ? () => void stopActiveRun() : undefined}
            onInject={sessionBusy ? (text) => void injectActiveRun(text) : undefined}
            draftMessage={composerDraft}
            onDraftConsumed={clearComposerDraft}
            draftValue={inputDraft}
            onDraftChange={onInputDraftChange}
            product={currentProduct}
            deepThink={conversation.deepThink}
            backgroundAgentCount={backgroundAgentCount}
            backgroundAgentRunningCount={backgroundAgentRunningCount}
            onOpenBackgroundAgents={() => setAgentsPanelVariant("panel")}
            send={(info) =>
              sendMessage({
                ...info,
                deepThink: conversation.deepThink,
                outputStyle: conversation.productType === "dataAgent" ? "dataAgent" : undefined,
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
          className="thinking-shimmer truncate text-ui-base font-semibold tracking-tight text-foreground-subtle"
          role="status"
          aria-live="polite"
        >
          {headerStatus}
        </div>
      ) : (
        <div className="min-w-0 truncate text-ui-base font-semibold text-foreground">
          {headerTitle}
        </div>
      )}
      {opts?.showDeepThink && conversation.deepThink ? (
        <div className="flex shrink-0 items-center gap-1.5 rounded-xs border border-border px-2 py-1 text-ui-xs leading-none text-foreground-subtlest">
          <i className="font_family icon-shendusikao text-ui-xs"></i>
          <span>深度研究</span>
        </div>
      ) : null}
      {opts?.badge ? (
        <div className="flex shrink-0 items-center gap-1.5 rounded-xs border border-border px-2 py-1 text-ui-xs leading-none text-foreground-subtlest">
          <i className="font_family icon-shendusikao text-ui-xs"></i>
          <span>{opts.badge}</span>
        </div>
      ) : null}
    </div>
  );

  const renderChatDialogues = () => {
    const lastRequestId =
      deferredChatList[deferredChatList.length - 1]?.requestId;
    return (
      <>
        {onLoadMoreHistory && conversation.historyHasMore ? (
          <div className="flex justify-center py-2">
            <button
              type="button"
              onClick={() => void onLoadMoreHistory()}
              disabled={historyLoading}
              className="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-ui-sm text-foreground-subtlest transition-colors hover:bg-hover hover:text-foreground disabled:cursor-wait disabled:opacity-60"
              data-testid="load-more-history"
            >
              {historyLoading ? (
                <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <ChevronUp className="h-3.5 w-3.5" />
              )}
              <span>{historyLoading ? "加载中..." : "加载更早记录"}</span>
            </button>
          </div>
        ) : null}
        {chatTurns.map(({ key, chat, requestIds }) => (
          <Dialogue
            key={key}
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
            onOpenToolDiff={(task, chat, fileIndex) =>
              openToolDiffPanel(task, chat, undefined, fileIndex)
            }
            onOpenAgent={openAgentPanel}
            onOpenWorkspaceFiles={onOpenTaskFiles}
            onRequestReplay={onRequestRunReplay ? requestReplay : undefined}
            replayLoading={requestIds.some((id) => replayLoadingIds.has(id))}
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
    // 右侧关闭后回到居中单栏；仅展开时才占用双栏布局，避免留下窄条把主会话拉长。
    const hasWorkspaceLayout = hasWorkspaceContent && !isRightCollapsed;

    return (
      <div
        ref={containerRef}
        className={classNames(
          "reactor-chat-workspace-layout flex h-full w-full",
          // 双栏：外壳铺一层浅底（与左侧会话栏同色），两个面板保持纯白圆角、**不描边**。
          // 分栏靠这层底色差表达，而不是靠 border —— 这样「面板边框」这条线就不存在了。
          // 间隙尺寸对齐 ZCode：`ResizablePanelGroup` 本身没有 gap，两栏之间**只留分隔条自身的宽度**。
          // ZCode `WorkspaceShellLayout.tsx:1626` 的分隔条就是 `w-1`（4px）—— 所以这里 gap 取 0。
          // 单栏时不留底，让会话直接铺在页面上。
          hasWorkspaceLayout
            ? "gap-0 bg-[var(--chat-nav)] p-1"
            : "reactor-single-chat-shell justify-center overflow-hidden bg-background px-4 pt-4 md:px-6"
        )}
        data-workspace-open={hasWorkspaceLayout ? "true" : "false"}
        data-workspace-empty={hasWorkspaceContent ? "false" : "true"}
        data-chat-read-only={readOnly ? "true" : "false"}
      >
        {/* Left Panel - Chat Area */}
        <div
          ref={leftPanelRef}
            className={classNames(
            "reactor-chat-panel-left flex min-h-0 flex-col overflow-hidden bg-background",
            // 双栏时只保留圆角，不加描边 —— 与预览面板的分界由外壳底色差表达。
            hasWorkspaceLayout && "rounded-[var(--workspace-panel-radius)]",
            !hasWorkspaceLayout && "mx-auto h-full w-full max-w-[980px]",
            isLeftCollapsed && !isFocusMode && "w-14 min-w-14",
            (!isLeftCollapsed || isFocusMode) && hasWorkspaceLayout && "shrink-0"
          )}
          style={
            hasWorkspaceLayout
              ? {
                ...((!isLeftCollapsed || isFocusMode)
                  ? {
                    width: `var(--workspace-left-width, ${leftPanelWidth}%)`,
                    minWidth: isFocusMode ? 280 : undefined,
                    maxWidth: isFocusMode ? "28%" : undefined,
                  }
                  : {})
              }
              : undefined
          }
        >
          <div
            className={
              hasWorkspaceLayout
                ? "flex h-full min-h-0 w-full flex-col overflow-hidden"
                : "flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden"
            }
            id="chat-view"
          >
            {isLeftCollapsed && !isFocusMode ? (
              // 折叠状态
              <div className="flex h-full flex-col items-center py-4">
                <button
                  onClick={toggleLeftPanel}
                  className="flex h-10 w-10 items-center justify-center rounded-full text-foreground-subtle transition-colors hover:bg-hover hover:text-foreground"
                  title="展开聊天区"
                >
                  <PanelRightClose className="h-5 w-5" />
                </button>
              </div>
            ) : (
              <>
                <div
                  className={classNames(
                    "reactor-chat-header flex items-center justify-between",
                    // 头部不再画横线：面板本身已经是带框的卡片，内部再画一条会把
                    // 标题框成「卡里套卡」。层级交给间距表达。
                    hasWorkspaceLayout
                      ? "py-3.5"
                      : "mb-3 min-h-[36px]",
                    hasWorkspaceLayout
                      ? isFocusMode
                        ? "px-3"
                        : "px-5"
                      : "px-1"
                  )}
                >
                  {renderHeaderStatus({showDeepThink: hasWorkspaceLayout ? !isFocusMode : true,})}
                  {(!hasWorkspaceLayout || !isFocusMode) ? (
                    <div className="flex shrink-0 items-center gap-1">
                      {renderWorkspaceReopenTrigger()}
                      <button
                        type="button"
                        onClick={() => onOpenTaskFiles?.()}
                        className="flex h-8 w-8 items-center justify-center rounded-full text-foreground-subtle transition-colors hover:bg-hover hover:text-foreground"
                        title="查看当前会话的文件"
                        aria-label="查看当前会话的文件"
                      >
                        <FolderOpen className="h-4 w-4" />
                      </button>
                    </div>
                  ) : null}
                </div>

                <div className="relative flex min-h-0 flex-1 flex-col overflow-hidden">
                  <Conversation
                    key={conversation.id}
                    contextRef={conversationContextRef}
                    className={classNames(
                      "chat-fade-bottom min-h-0 flex-1 overflow-hidden",
                      hasWorkspaceLayout && "pt-5"
                      // ⚠️ 水平内边距不能加在这里：use-stick-to-bottom 会在
                      // Conversation 与 Content 之间注入无类名的滚动 div，
                      // 滚动条画在它身上 —— 外层一旦有 padding，滚动条就
                      // 距面板边缘 20px。内边距一律放到 Content 上（见下）。
                    )}
                  >
                    <ConversationContent
                      // scrollClassName 直接命中真正的滚动容器（库的 scrollRef div），
                      // 4px 极细滚动条 + 紧贴面板右缘。
                      scrollClassName="scrollbar-thin"
                      className={chatContentClassName(
                        hasWorkspaceLayout,
                        isFocusMode
                      )}
                    >
                      {renderChatDialogues()}
                    </ConversationContent>
                    <ConversationScrollButton />
                  </Conversation>

                  {/* 右上角「智能体」浮动面板：后台 run_in_background 子 Agent 的
                      运行态 / 终态入口（移植 ZCode ConversationStatusPanel） */}
                  <BackgroundAgentsPanel
                    chat={activeChat}
                    chats={conversation.chatList}
                    variant={agentsPanelVariant}
                    onVariantChange={setAgentsPanelVariant}
                    onOpenAgent={openAgentPanel}
                  />

                  <ConversationTurnNavigator
                    chats={turnChatList}
                    onBeforeJump={() =>
                      conversationContextRef.current?.stopScroll()
                    }
                  />

                  {!readOnly ? (
                    <div
                      className={classNames(
                        "shrink-0 bg-[var(--color-bg)]",
                        hasWorkspaceLayout
                          ? "pb-4 pt-3"
                          : "pb-5 pt-4",
                        hasWorkspaceLayout
                          ? isFocusMode
                            ? "px-2"
                            : "px-4"
                          : ""
                      )}
                      data-composer-dock="true"
                    >
                      <div
                        className={classNames(
                          "mx-auto w-full",
                          !hasWorkspaceLayout && "max-w-[860px]"
                        )}
                      >
                        {renderComposerStack(`input-${conversation.sessionId}-main`)}
                      </div>
                    </div>
                  ) : null}
                </div>
              </>
            )}
          </div>
        </div>

        {hasWorkspaceLayout && !isFocusMode && !isLeftCollapsed ? (
          <div
            aria-label="调整对话区和工作区宽度"
            role="separator"
            aria-orientation="vertical"
            onPointerDown={handleDragStart}
            onPointerMove={handleDragMove}
            onPointerUp={handleDragEnd}
            onPointerCancel={handleDragEnd}
            className={classNames(
              // ZCode WorkspaceShellLayout.tsx:1626 —— 分隔条本体是一条透明命中条，
              // 平时完全不可见（无底色、无描边、无手柄），只有 hover / 拖拽 / 焦点时才浮出指示线。
              // 宽度 `w-1`（4px）与 ZCode 的分隔条一致；容器 `gap-0`，所以两栏之间**就只有这 4px**，
              // 外壳浅底从这条缝里透出来当分界（改前是 gap-1 + w-1 + gap-1 = 12px，太宽）。
              "reactor-workspace-resizer group/handle relative z-10 flex h-full w-1 shrink-0 touch-none cursor-ew-resize items-center justify-center bg-transparent outline-none",
              "focus:outline-none focus-visible:ring-0",
              // 透明命中扩展：把 4px 视觉条的可拖拽区域撑到 12px，兼顾 ZCode 的极简外观与可用性。
              "before:absolute before:-inset-x-1 before:inset-y-0 before:content-['']",
              // 指示线：2px 圆角、三级前景半透明；上下按面板圆角内缩，默认完全透明。
              "after:pointer-events-none after:absolute after:left-1/2 after:-translate-x-1/2 after:w-0.5 after:rounded-full after:bg-foreground-subtlest/50 after:opacity-0 after:transition-opacity after:content-['']",
              "after:inset-y-[var(--workspace-panel-radius)]",
              // hover / 键盘焦点 / 拖拽中点亮（拖拽态由容器 data-workspace-resizing 驱动，与 ZCode 一致）
              "hover:after:opacity-100 focus-visible:after:opacity-100 [[data-workspace-resizing=true]_&]:after:opacity-100"
            )}
            title="拖拽调整左右区域宽度"
          />
        ) : null}

        {hasWorkspaceLayout ? (
          <button
            type="button"
            className="reactor-workspace-scrim lg:hidden"
            aria-label="关闭工作区"
            onClick={closeMobileWorkspace}
          />
        ) : null}

        {hasWorkspaceLayout ? (
          <div
            className="reactor-workspace-panel flex min-h-0 flex-1 flex-col overflow-hidden rounded-[var(--workspace-panel-radius)] bg-background"
            data-workspace-collapsed="false"
          >
            {pendingDesktopStreamUrl ? (
              <DesktopPreviewPanel
                streamUrl={pendingDesktopStreamUrl}
                onClose={closeActionView}
              />
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
              <ToolPreviewPanel
                source={toolDiffView.source}
                fileUrl={toolDiffView.fileUrl}
                unavailableReason={toolDiffView.unavailableReason}
                output={toolDiffView.output}
                status={toolDiffView.status}
                onBack={workspaceBackTarget ? returnToAgentDetail : undefined}
                onClose={() => {
                  closeToolDiffPanel();
                  if (!showAction && !workspaceOpenRequested) {
                    setIsRightCollapsed(true);
                  }
                }}
              />
            ) : liveAgentDetail ? (
              <AgentDetailPanel
                key={
                  identityKeys(readTaskIdentity(liveAgentDetail.tool))[0] ||
                  liveAgentDetail.tool.id
                }
                tool={liveAgentDetail.tool}
                chat={liveAgentDetail.chat}
                sessionArtifactFiles={sessionArtifactFiles}
                changeActiveChat={(task, chat) =>
                  changeTask(task, chat, agentDetailBackTarget)
                }
                changePlan={changePlan}
                changeFile={(file, chat) =>
                  changeFile(file, chat, agentDetailBackTarget)
                }
                onOpenThinking={openThinkingPanel}
                onOpenToolDiff={(task, chat, fileIndex) =>
                  openToolDiffPanel(task, chat, agentDetailBackTarget, fileIndex)
                }
                onOpenAgent={(task, chat) =>
                  openAgentPanel(task, chat, agentDetailBackTarget)
                }
                onOpenWorkspaceFiles={onOpenTaskFiles}
                onClose={() => {
                  closeAgentPanel();
                  if (!showAction && !workspaceOpenRequested) {
                    setIsRightCollapsed(true);
                  }
                }}
              />
            ) : (
              <ActionView
                key={conversation.id}
                activeTask={activeTask}
                streamTask={workspaceStreamTask}
                pendingPreviewFile={pendingPreviewFile}
                onPendingPreviewFileConsumed={clearPendingPreviewFile}
                pendingSourceTab={pendingSourceTab}
                onPendingSourceTabConsumed={() => setPendingSourceTab(null)}
                workspaceCaption={workspaceCaption}
                taskList={sessionFileTasks}
                plan={plan}
                isFocusMode={isFocusMode}
                onToggleFocusMode={toggleFocusMode}
                onBack={workspaceBackTarget ? returnToAgentDetail : undefined}
                ref={actionViewRef}
                onClose={closeActionView}
              />
            )}
          </div>
        ) : null}

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
            <ConversationContent
              scrollClassName="scrollbar-thin"
              className="mx-auto w-full max-w-[860px] px-1 pb-6"
            >
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
                  draftValue={inputDraft}
                  onDraftChange={onInputDraftChange}
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
    });
  });

  return (
    <div className="flex h-full w-full justify-center">
      {!readOnly ? (
        <GenUiActionBridge sendMessage={sendGenUiMessage} busy={sessionBusy} />
      ) : null}
      {isDataConversation ? renderDataAgent() : renderMultAgent()}
    </div>
  );
};

export default ChatView;
