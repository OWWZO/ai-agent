import {
  useCallback,
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
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
import ActionView, {
  type OpenAgentTabPayload,
  type OpenTaskTabPayload,
} from "@/components/ActionView";
import { ThinkingPanel } from "@/components/Dialogue/ThinkingPanel";
import { AgentDetailPanel } from "@/components/Dialogue/AgentDetailPanel";
import {
  buildToolPreviewSource,
  type ToolPreviewSource,
} from "@/components/Dialogue/tools/toolPreviewSource";
import { MoonSpinner } from "@/components/ui/MoonSpinner";
import SessionTaskComposerBar from "./SessionTaskComposerBar";
import SubAgentsPanel from "./SubAgentsPanel";
import type { AgentsPanelVariant } from "./subAgentsModel";
import { collectSubAgentRoster } from "@/utils/chat/subAgentRoster";
import { resolveSubAgentDisplay } from "@/utils/chat/subagent";
import FilePreview from "@/components/ActionView/FilePreview";
import { resolvePreviewTitle } from "@/components/ActionView/filePreviewModel";
import type {
  WorkspaceAgentTab,
  WorkspaceTaskTab,
} from "@/components/ActionView/workspaceFiles";
import {
  findAgentTaskByToolCallId,
  findLatestRunningAgentTask,
} from "@/utils/chat/taskIdentity";
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
import { cn } from "@/lib/utils";
import { Group, Panel, Separator } from "react-resizable-panels";
import { confirm } from "@/components/ui/confirm-dialog";
import {
  IMMERSIVE_LEFT_WIDTH,
  MAX_LEFT_WIDTH,
  MIN_LEFT_WIDTH,
} from "./useWorkspacePanels";
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
import {
  decideWorkspaceTabCountChange,
  resolveAutoFollowTabKind,
  shouldAutoFollowWorkspaceTask,
} from "./workspaceTabSentinel";
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
import { getPrimaryTaskFile, getTaskFiles } from "@/utils/taskArtifacts";
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
  onRefreshSessionFiles?: () => void;
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

/**
 * 按稳定身份回找**最新的**任务 —— 任务预览 tab 的渲染数据源。
 *
 * tab 只存 `taskKey` + 打开瞬间的兜底快照：流式过程中任务的 `resultMap` /
 * `artifactRefs` / `children` 一直在补全，用快照渲染会让预览停在打开那一刻
 * （旧的「动态」跟随没有这个问题，因为它每次都用宿主的最新任务重渲染）。
 *
 * 遍历顺序：`chatList` **倒序**（同一个 key 若跨轮重复出现，当前/最新那一轮优先）
 * → 再退到流式 `taskList`。两处都没有时才由调用方回退 `tab.fallbackTask`。
 */
const findTaskByStableKey = (
  chatList: CHAT.ChatItem[] | null | undefined,
  liveTaskList: PanelItemType[] | null | undefined,
  taskKey: string
): CHAT.Task | undefined => {
  if (!taskKey) {
    return undefined;
  }
  let hit: CHAT.Task | undefined;
  const walk = (task?: CHAT.Task | PanelItemType | null) => {
    if (!task || hit) {
      return;
    }
    if (getTaskStableKey(task as CHAT.Task) === taskKey) {
      hit = task as CHAT.Task;
      return;
    }
    const children = (task as CHAT.Task).children;
    if (Array.isArray(children)) {
      for (const child of children) {
        walk(child);
      }
    }
  };

  const chats = chatList || [];
  for (let index = chats.length - 1; index >= 0; index -= 1) {
    const chat = chats[index];
    for (const group of chat.tasks || []) {
      for (const task of group || []) {
        walk(task);
      }
    }
    for (const group of chat.multiAgent?.tasks || []) {
      for (const task of group || []) {
        walk(task);
      }
    }
    if (chat.conclusion) {
      walk(chat.conclusion);
    }
    if (hit) {
      return hit;
    }
  }

  const live = liveTaskList || [];
  for (let index = live.length - 1; index >= 0; index -= 1) {
    walk(live[index]);
    if (hit) {
      return hit;
    }
  }
  return hit;
};

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
    onRefreshSessionFiles,
    onRequestRunReplay,
    onLoadMoreHistory,
    historyLoading = false,
    onRegisterApi,
    onOpenTaskFiles,
    onFocusModeChange,
  } = props;

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
  /**
   * 待挂载的子智能体 tab（时间线工具卡 / 右上角「智能体」面板 / 目录推来）。
   *
   * 与 `pendingPreviewFile` / `pendingSourceTab` 同一套挂载机制：ActionView 可能
   * 还没挂载（工作区收起、正显示 thinking / diff 独占面板），先把要开的 tab
   * 暂存在父级，ActionView 挂载后消费一次再清空。
   */
  const [pendingAgentTab, setPendingAgentTab] =
    useState<OpenAgentTabPayload | null>(null);
  /** 待挂载的子智能体目录 tab（单例，与 pendingAgentTab 同机制） */
  const [pendingAgentDirectory, setPendingAgentDirectory] = useState(false);
  /** 待挂载的任务预览 tab（点工具卡推来，代替旧的「动态」跟随） */
  const [pendingTaskTab, setPendingTaskTab] = useState<OpenTaskTabPayload | null>(
    null
  );
  /**
   * 工作区「最后一个真实 tab 被关掉」的哨兵。
   *
   * ActionView 挂载后会立刻上报一次 tab 数，首次必然是 0 —— 因为 pending tab 的
   * 消费 effect 与上报 effect 在同一次提交里，上报读到的还是旧 state。所以
   * **首次 0 不能当关闭命令**，只有真的经历过 `>0` 之后再回到 0 才算「关光了」。
   */
  const hadWorkspaceTabsRef = useRef(false);
  /**
   * 工作区当前是否有真实 tab。
   *
   * 用 state 而不是 ref：它是 `hasWorkspaceContent` 的输入 —— 只要还有 tab，
   * 工作区就必须留着（哪怕流式的 `showAction` 已经翻回 false）。
   */
  const [workspaceHasTabs, setWorkspaceHasTabs] = useState(false);
  /** 流式「自动跟随」已经为哪个任务自动开过 tab：同一个任务只自动开一次 */
  const lastAutoFollowTaskKeyRef = useRef("");
  /**
   * 本轮里用户是否手动关过工作区。
   *
   * 关过之后流式自动跟随不再把工作区弹回来（否则用户刚关掉，下一个工具事件就
   * 又拉开一个面板）；新一轮开始时复位。
   */
  const userClosedWorkspaceRef = useRef(false);
  /**
   * 右侧**覆盖层** detail：thinking | desktop-preview。
   *
   * 只剩这两种：工具 diff / 子智能体详情都已改走 ActionView 的 tab（与文件预览同一套
   * 挂载机制），不再是独占面板 —— 所以它们不再需要「返回上一屏」的回退路径。
   */
  const [thinkingDetail, setThinkingDetail] = useState<string | null>(null);
  /** 右上角「智能体」面板形态：默认 auto（宽屏展开、窄屏胶囊），输入框徽标可强制展开 */
  const [agentsPanelVariant, setAgentsPanelVariant] =
    useState<AgentsPanelVariant>("auto");
  /** 已自动开过详情 tab 的子 Agent toolCallId：同一条只自动开一次，不覆盖用户手动关闭 */
  const autoOpenedAgentRef = useRef("");
  const lastAutoOpenedDesktopControlRef = useRef("");
  const [composerDraft, setComposerDraft] = useState<string | null>(null);
  const {
    leftPanelWidth,
    isLeftCollapsed,
    isRightCollapsed,
    isFocusMode,
    setIsRightCollapsed,
    toggleLeftPanel,
    toggleRightPanel: toggleWorkspaceRightPanel,
    toggleFocusMode,
    exitFocusMode,
    handleLayoutChange,
    workspaceRef,
    groupRef,
    notifyResizeEnd,
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
      // 新一轮开始：清掉上一轮遗留的「待打开」意图，但**不**动已经开着的 tab
      // （用户的 tab 选择是显式操作，不该被新一轮流式冲掉）。
      // 旧的「工作区恢复自动跟随」已随「动态」伪模式删除 —— 现在只有用户点工具卡
      // 才会开任务预览 tab，新一轮不会再自动抢工作区焦点。
      setPendingPreviewFile(undefined);
      setThinkingDetail(null);
      setPendingAgentTab(null);
      setPendingAgentDirectory(false);
      setPendingTaskTab(null);
      // 新一轮开始，「用户已关闭工作区」的记忆与自动跟随的去重键都归零 ——
      // 自动跟随按轮生效，上一轮的手动关闭不该管住下一轮。
      userClosedWorkspaceRef.current = false;
      lastAutoFollowTaskKeyRef.current = "";
      lastAutoOpenedCanvasPreviewKeyRef.current = "";
    },
    onTokenUseUp: () => {
      confirm({
        title: "您的试用次数已用尽",
        content: "如需额外申请，请联系 liyang.1236@jd.com",
        okText: "我知道了",
        alertOnly: true,
      });
    },
    onTurnSettled: () => onRefreshSessionFiles?.(),
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
    setPendingAgentTab(null);
    setPendingAgentDirectory(false);
    setPendingTaskTab(null);
    // 「待打开」意图都是**瞬时**的，不跨会话 —— 流式自动跟随会写 pendingPreviewFile，
    // 不清掉的话切会话时可能把上一个会话的文件带过来。
    setPendingPreviewFile(undefined);
    // ActionView 的 key 是 conversation.id，换会话后 tab 全没了 —— 哨兵必须归零，
    // 否则新会话首次上报 0 会被误判成「最后一个 tab 被关掉」。
    hadWorkspaceTabsRef.current = false;
    setWorkspaceHasTabs(false);
    userClosedWorkspaceRef.current = false;
    lastAutoFollowTaskKeyRef.current = "";
    lastAutoOpenedCanvasPreviewKeyRef.current = "";
    lastAutoOpenedDesktopControlRef.current = "";
  }, [conversation.id]);

  // 任务预览 tab 不需要在这里「刷新快照」：tab 只存 `taskKey`，实时任务由
  // `renderTaskTab` 在渲染时从最新 chatList / taskList 回找（旧实现靠一段
  // refreshTask 把 activeTask 快照 patch 回来，随「动态」伪模式一起删掉了）。

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
    // 用户显式打开 → 解除「本轮已手动关闭」的记忆，自动跟随可以重新生效。
    userClosedWorkspaceRef.current = false;
    setWorkspaceOpenRequested(true);
    setIsRightCollapsed(false);
    changeActionStatus(true);
  });

  /**
   * 收起整个工作区（最后一个真实 tab 被关掉 / 用户点关闭）。
   *
   * 必须同时复位 `hadWorkspaceTabsRef`：ActionView 会随工作区卸载，下次打开时
   * 是全新的空 tab 列表，首次上报的 0 不能被当成「又关光了」。
   *
   * 并记下「用户手动关过」：本轮内流式自动跟随不再把工作区弹回来。
   */
  const collapseWorkspace = useMemoizedFn(() => {
    hadWorkspaceTabsRef.current = false;
    userClosedWorkspaceRef.current = true;
    setWorkspaceOpenRequested(false);
    changeActionStatus(false);
    setIsRightCollapsed(true);
  });

  const openThinkingPanel = useMemoizedFn((text: string) => {
    setThinkingDetail(text);
    setPendingAgentTab(null);
    setPendingAgentDirectory(false);
    setPendingPreviewFile(undefined);
    setPendingSourceTab(null);
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
   * 一律在侧栏 ActionView 里**新增 / 切换一个 tab**（patch、文件、子智能体详情
   * 共用同一条 tab 栏，对齐 ZCode），而不是替换右侧单一面板 —— 这样从子智能体
   * 详情里点文件不会再顶掉详情，也不需要「返回子 Agent」这条回退路径。
   */
  const openToolDiffPanel = useMemoizedFn(
    (task: CHAT.Task, chat?: CHAT.ChatItem, fileIndex?: number) => {
      const request = buildToolPreviewSource(task, fileIndex);
      if (!request) return;

      setThinkingDetail(null);
      setPendingPreviewFile(undefined);
      setActiveRunState({
        status: chat?.metrics?.status,
        finishedAt: chat?.finishedAt,
      });

      // `source` 是 file 且能映射到会话产物时，ActionView 会把它并进同 key 的文件
      // tab；否则作为独立 source tab 打开。
      openRightWorkspace();
      setPendingPreviewFile(undefined);
      setPendingSourceTab(request);
    }
  );

  /**
   * 打开一个子智能体的详情 tab（时间线工具卡 / 右上角「智能体」面板 / 目录共用）。
   *
   * 子智能体详情从「独占面板」改为「工作区 tab」：与文件预览同一套挂载机制，
   * 多个子智能体可以并存、可来回切，不会再互相顶掉。
   */
  const openAgentPanel = useMemoizedFn(
    (task: CHAT.Task, chat: CHAT.ChatItem) => {
      const toolCallId =
        identityKeys(readTaskIdentity(task))[0] || task.id || task.messageId || "";
      if (!toolCallId) {
        return;
      }
      const display = resolveSubAgentDisplay(task);
      setPendingAgentTab({
        toolCallId,
        title: display.description || display.subagentType || "子智能体",
        subagentType: display.subagentType,
        runInBackground: display.runInBackground,
        tool: task,
        chat,
      });
      setThinkingDetail(null);
      setPendingPreviewFile(undefined);
      setPendingSourceTab(null);
      openRightWorkspace();
      setActiveRunState({
        status: chat.metrics?.status,
        finishedAt: chat.finishedAt,
      });
    }
  );

  /** 打开子智能体目录 tab（右上角「智能体」面板页脚 / 徽标入口） */
  const openSubAgentDirectory = useMemoizedFn(() => {
    setThinkingDetail(null);
    setPendingAgentTab(null);
    setPendingSourceTab(null);
    openRightWorkspace();
    setPendingAgentDirectory(true);
  });

  const closeExclusiveDetail = useMemoizedFn(() => {
    if (thinkingDetail != null) {
      closeThinkingPanel();
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

  /**
   * 点工具卡 → 开一个「任务预览 tab」（原「动态」跟随）。
   *
   * 与文件 / 子智能体详情同一套挂载机制：同一个任务重复点开只切前台不新增 tab，
   * 且不再有一个叫「动态」的空面板常驻右侧。
   */
  const changeTask = useMemoizedFn((task: CHAT.Task, chat?: CHAT.ChatItem) => {
    // Structured data / 子智能体 JSON 观察值当前不渲染，禁止打开空白右侧面板。
    if (!canOpenTaskWorkspacePanel(task)) {
      return;
    }
    const taskKey = getTaskStableKey(task);
    if (!taskKey) {
      return;
    }
    setThinkingDetail(null);
    setPendingTaskTab({
      taskKey,
      // 文件类任务的标题取自 primary file（`resolvePreviewTitle` 的 file 分支
      // 需要它），工具类任务取工具名 —— 不要直接把 messageType 暴露给用户。
      title:
        resolvePreviewTitle(task, getPrimaryTaskFile(task)) ||
        task.messageType ||
        "任务",
      task,
    });
    openRightWorkspace();
    setActiveRunState({
      status: chat?.metrics?.status,
      finishedAt: chat?.finishedAt,
    });
  });

  const changeFile = useMemoizedFn((file: CHAT.TFile, chat?: CHAT.ChatItem) => {
    // 只打开右侧预览 tab；左侧文件管理仅由「查看当前会话的文件」入口进入
    setThinkingDetail(null);
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
    openRightWorkspace();
    actionViewRef.current?.openPlanView();
  });

  const toggleRightPanel = useMemoizedFn(() => {
    // 目前只有「工作区收起时头部那个展开按钮」会走到这里（展开方向）。
    // 关闭方向统一走 `collapseWorkspace()`，保证 tab 哨兵一起复位 —— 否则下次展开
    // 时 ActionView 首次上报的 0 会被误判成「刚把最后一个 tab 关掉」。
    if (!isRightCollapsed) {
      collapseWorkspace();
      return;
    }
    setWorkspaceOpenRequested(true);
    changeActionStatus(true);
    toggleWorkspaceRightPanel();
  });

  const closeMobileWorkspace = useMemoizedFn(() => {
    setThinkingDetail(null);
    setPendingAgentTab(null);
    setPendingAgentDirectory(false);
    // 手动关闭必须清掉 pending：否则下一次 ActionView 挂载会把这条旧意图
    // 再消费一遍，工作区「自己」又弹回来。
    setPendingTaskTab(null);
    setPendingPreviewFile(undefined);
    collapseWorkspace();
    if (isFocusMode) {
      exitFocusMode();
    }
  });

  const closeActionView = useMemoizedFn(() => {
    if (isFocusMode) {
      exitFocusMode();
      return;
    }
    setPendingTaskTab(null);
    setPendingPreviewFile(undefined);
    collapseWorkspace();
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
    confirm({
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

  const activeChat = conversation.chatList?.[conversation.chatList.length - 1];
  const sessionBusy = loading || isParentLoopLive(activeChat);
  /**
   * 子智能体名册（同步 + 后台）。
   *
   * 右上角「智能体」面板、右侧工作区「子智能体目录」tab、输入框徽标三处同源，
   * 保证「面板里有、目录里没有」这类割裂不会出现。
   */
  const subAgentRoster = useMemo(
    () => collectSubAgentRoster(conversation.chatList, activeChat),
    [conversation.chatList, activeChat]
  );
  const subAgentCount = subAgentRoster.length;
  const subAgentRunningCount = subAgentRoster.filter(
    (item) => item.state === "run"
  ).length;

  /**
   * 子智能体详情 tab 的内容（render prop 给 ActionView）。
   *
   * ActionView 只存身份（toolCallId），**实时父卡在这里解析** —— 流式过程中
   * 父卡每秒在变，把快照存进 tab 会让详情面板停在打开那一刻。
   * 父卡找不到（历史裁剪 / 后台子 Agent 老会话）时回退 tab 里的兜底快照。
   */
  const renderAgentTab = useCallback(
    (tab: WorkspaceAgentTab) => {
      let source: { tool: CHAT.Task; chat: CHAT.ChatItem } | null = null;
      for (const chat of conversation.chatList || []) {
        const liveChat = getRenderedChat(chat, conversation.deepThink);
        const liveTool = findAgentTaskByToolCallId(liveChat, tab.toolCallId);
        if (liveTool) {
          source = {
            tool: liveTool,
            chat: liveChat,
          };
          break;
        }
      }
      const resolved = source || {
        tool: tab.fallbackTool,
        chat: tab.fallbackChat,
      };
      const handleChangeFile = (file: CHAT.TFile) => {
        changeFile(file, resolved.chat);
      };
      return (
        <AgentDetailPanel
          key={`agent-tab-${tab.toolCallId}`}
          tool={resolved.tool}
          chat={resolved.chat}
          embedded
          sessionArtifactFiles={sessionArtifactFiles}
          changeActiveChat={(task, chat) => changeTask(task, chat)}
          changePlan={changePlan}
          changeFile={handleChangeFile}
          onOpenThinking={openThinkingPanel}
          onOpenToolDiff={(task, _chat, fileIndex) =>
            openToolDiffPanel(task, resolved.chat, fileIndex)
          }
          onOpenAgent={(task, chat) => openAgentPanel(task, chat)}
          onOpenWorkspaceFiles={onOpenTaskFiles}
        />
      );
    },
    [
      changeFile,
      changePlan,
      changeTask,
      conversation.chatList,
      conversation.deepThink,
      onOpenTaskFiles,
      openAgentPanel,
      openThinkingPanel,
      openToolDiffPanel,
      sessionArtifactFiles,
    ]
  );

  /**
   * 任务预览 tab 的内容（render prop 给 ActionView）。
   *
   * 与 `renderAgentTab` 同一个理由：tab 只存 `taskKey`，**实时任务在这里解析** ——
   * 流式过程中任务的 resultMap / artifactRefs 每秒都在补全，用打开瞬间的快照渲染
   * 会让预览停住。回找顺序：最新 chatList → 流式 taskList / workspaceStreamTask
   * → tab 自带的兜底快照（历史裁剪后仍不至于白屏）。
   *
   * 渲染复用既有 `FilePreview`（carousel + ActionPanel 路由），**不要**另写一套
   * 工具预览逻辑。
   */
  const renderTaskTab = useCallback(
    (tab: WorkspaceTaskTab) => {
      const liveTask =
        findTaskByStableKey(conversation.chatList, taskList, tab.taskKey) ||
        (workspaceStreamTask &&
        getTaskStableKey(workspaceStreamTask) === tab.taskKey
          ? workspaceStreamTask
          : undefined) ||
        tab.fallbackTask;
      return (
        <FilePreview
          key={`task-tab-${tab.taskKey}`}
          taskItem={liveTask}
          taskList={sessionFileTasks}
          className="h-full"
        />
      );
    },
    [conversation.chatList, sessionFileTasks, taskList, workspaceStreamTask]
  );

  /**
   * 新派发的子 Agent 自动开一次详情 tab（保持旧「自动展开」体验）。
   *
   * 用 toolCallId 记忆而不是「当前有没有开着的详情 tab」：用户手动关掉 tab 后
   * 不该被立刻重新弹开，但下一个新子 Agent 仍应自动展开。
   */
  useEffect(() => {
    if (thinkingDetail != null) {
      return;
    }
    if (!(streamLoading || activeChat?.loading)) {
      return;
    }
    const tool = findLatestRunningAgentTask(activeChat);
    if (!tool) {
      return;
    }
    const toolCallId = identityKeys(readTaskIdentity(tool))[0] || "";
    if (!toolCallId || autoOpenedAgentRef.current === toolCallId) {
      return;
    }
    autoOpenedAgentRef.current = toolCallId;
    openAgentPanel(tool, activeChat);
  }, [activeChat, openAgentPanel, streamLoading, thinkingDetail]);
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
    setPendingAgentTab(null);
    setPendingAgentDirectory(false);
    setPendingPreviewFile(undefined);
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

  /**
   * 工作区何时占位。
   *
   * - `workspaceOpenRequested`：用户显式打开过（点工具卡 / 文件 / 子智能体 / 思考块）；
   * - `workspaceHasTabs`：还有真实 tab → 工作区必须在，不能因为流结束就消失；
   * - 覆盖层（thinking / 桌面预览）与待打开意图：内容马上就有。
   *
   * 注意**不包含** `showAction`：它只表示「流式里有值得看的任务」，而工作区现在
   * 「有 tab 才有内容」—— 直接拿它当展开条件就会得到一张空白面板（旧「动态」空态
   * 卡出现的情形）。流式自动跟随改由下面的 effect 负责：展开的同时**一定**会开一个
   * 真实任务 tab。
   */
  const hasWorkspaceContent = Boolean(
    workspaceOpenRequested ||
      workspaceHasTabs ||
      thinkingDetail ||
      pendingTaskTab ||
      pendingAgentTab ||
      pendingAgentDirectory ||
      pendingSourceTab ||
      pendingDesktopStreamUrl
  );
  /** 工作区此刻是否真的占着右侧（收起 / 无内容都算「不可见」）。等价于渲染时的 `hasWorkspaceLayout`。 */
  const workspaceVisible = hasWorkspaceContent && !isRightCollapsed;

  /**
   * 流式「自动跟随」（原「动态」模式）现在落成一个**真实 tab**。
   *
   * 旧 follow 的行为是：`showAction`（最新任务里有值得看的产物）让工作区自动展开，
   * 内容区固定渲染当前流式任务。现在内容区必须有 tab，所以这个 effect 负责
   * 「把工作区拉起来，并给它一个真实 tab」—— 只展开不给 tab 就会退化成空白面板。
   *
   * **开哪种 tab 取决于这个任务在展示什么**：
   * - 有可预览的主产物（工具写出的文件）→ 开**文件 tab**：tab 上是文件名 + 文件类型
   *   角标，内容与任务 tab 完全一致（`openFileInTab` 的 `task` 就是产它的那个任务），
   *   而且正是用户点「打开文件」会得到的那一个 —— 身份键一致，天然不会出现两个一样的
   *   预览；tab 标题也才是用户认的那个「文件名」，而不是工具名。
   * - 没有产物（纯工具输出 / 深搜阶段面板）→ 退回**任务 tab**，标题用工具名。
   *
   * 三条闸门，缺一不可：
   * - 工作区已经可见（用户开的 / 上次自动跟随开的）→ 一概不动：不抢用户正在看的
   *   tab，也不会每个工具事件都堆一个新 tab；
   * - 本轮用户手动关过工作区 → 不再自动弹回（`userClosedWorkspaceRef`）；
   * - 同一个任务只自动开一次（`lastAutoFollowTaskKeyRef`）。
   */
  useEffect(() => {
    const shouldFollow = shouldAutoFollowWorkspaceTask({
      workspaceVisible,
      showAction,
      hasLiveTask: Boolean(workspaceStreamTask),
      userClosedWorkspace: userClosedWorkspaceRef.current,
      hasTabs: workspaceHasTabs,
    });
    if (!shouldFollow || !workspaceStreamTask) {
      return;
    }
    const taskKey = getTaskStableKey(workspaceStreamTask);
    if (!taskKey || lastAutoFollowTaskKeyRef.current === taskKey) {
      return;
    }
    lastAutoFollowTaskKeyRef.current = taskKey;

    // 「跟随」跟的是这个任务**在展示什么**：有可预览主产物就开文件 tab（tab 上是
    // 文件名 + 文件类型角标，也是用户点「打开文件」会得到的那一个），否则退回任务 tab。
    const primaryFile = getPrimaryTaskFile(workspaceStreamTask);
    const tabKind = resolveAutoFollowTabKind({
      messageType: workspaceStreamTask.messageType,
      hasPrimaryFile: Boolean(primaryFile),
    });
    if (tabKind === "file" && primaryFile) {
      changeFile(primaryFile, activeChat);
      return;
    }

    setPendingTaskTab({
      taskKey,
      title:
        resolvePreviewTitle(workspaceStreamTask) ||
        workspaceStreamTask.messageType ||
        "任务",
      task: workspaceStreamTask,
    });
    openRightWorkspace();
  }, [
    activeChat,
    changeFile,
    openRightWorkspace,
    showAction,
    workspaceHasTabs,
    workspaceStreamTask,
    workspaceVisible,
  ]);

  /**
   * ActionView 上报 tab 数量：**只在 `>0 → 0`** 时收起工作区。
   *
   * 首次挂载必然是 0（pending tab 的消费 effect 与上报 effect 同批提交），
   * 必须忽略；同时如果此刻正有 pending 打开意图（「关最后一个 tab」与「开新 tab」
   * 并发），也不能收起 —— 否则刚点开的 tab 会被立刻连带关掉。
   */
  const handleWorkspaceTabsChange = useMemoizedFn((count: number) => {
    setWorkspaceHasTabs(count > 0);
    const decision = decideWorkspaceTabCountChange({
      hadTabs: hadWorkspaceTabsRef.current,
      count,
      hasPendingOpenIntent: Boolean(
        pendingTaskTab ||
          pendingAgentTab ||
          pendingAgentDirectory ||
          pendingSourceTab ||
          pendingPreviewFile ||
          thinkingDetail != null ||
          pendingDesktopStreamUrl
      ),
    });
    hadWorkspaceTabsRef.current = decision.hadTabs;
    if (decision.collapse) {
      collapseWorkspace();
    }
  });

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
            subAgentCount={subAgentCount}
            subAgentRunningCount={subAgentRunningCount}
            onOpenSubAgents={() => setAgentsPanelVariant("panel")}
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
        className={cn(
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
              openToolDiffPanel(task, chat, fileIndex)
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

    // 左栏内容。单栏 / 双栏共用，但外层容器不同：
    // 双栏交给 `react-resizable-panels` 的 Panel，单栏必须用普通 div ——
    // Panel 的外层会硬编码 `flexBasis: 0`、内层带 `maxWidth: 100%`，
    // 单栏时会把 `mx-auto max-w-[980px]` 的居中布局压坏。
    const chatColumn = (
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
              className={cn(
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
                className={cn(
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

              {/* 右上角「智能体」浮动面板：全部子智能体（同步 + 后台）的运行态入口
                      （移植 ZCode ConversationStatusPanel）；已结束的从页脚进目录 tab */}
              <SubAgentsPanel
                chat={activeChat}
                chats={conversation.chatList}
                variant={agentsPanelVariant}
                onVariantChange={setAgentsPanelVariant}
                onOpenAgent={openAgentPanel}
                onOpenDirectory={openSubAgentDirectory}
              />

              <ConversationTurnNavigator
                chats={turnChatList}
                onBeforeJump={() =>
                  conversationContextRef.current?.stopScroll()
                }
              />

              {!readOnly ? (
                <div
                  className={cn(
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
                    className={cn(
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
    );

    // 单栏：不套 Group / Panel，保持原来的居中布局。
    if (!hasWorkspaceLayout) {
      return (
        <div
          className="reactor-chat-workspace-layout reactor-single-chat-shell flex h-full w-full justify-center overflow-hidden bg-background px-4 pt-4 md:px-6"
          data-workspace-open="false"
          data-workspace-empty={hasWorkspaceContent ? "false" : "true"}
          data-chat-read-only={readOnly ? "true" : "false"}
        >
          {chatColumn}
        </div>
      );
    }

    return (
      <Group
        id="chat-workspace"
        orientation="horizontal"
        elementRef={workspaceRef}
        groupRef={groupRef}
        className={cn(
          "reactor-chat-workspace-layout flex h-full w-full",
          // 双栏：外壳铺一层浅底（与左侧会话栏同色），两个面板保持纯白圆角、**不描边**。
          // 分栏靠这层底色差表达，而不是靠 border —— 这样「面板边框」这条线就不存在了。
          "gap-0 bg-[var(--chat-nav)] p-1"
        )}
        data-workspace-open="true"
        data-workspace-empty={hasWorkspaceContent ? "false" : "true"}
        data-chat-read-only={readOnly ? "true" : "false"}
        onLayoutChange={handleLayoutChange}
        onLayoutChanged={notifyResizeEnd}
      >
        {/* Left Panel - Chat Area */}
        <Panel
          id="chat"
          className={cn(
            "reactor-chat-panel-left flex min-h-0 flex-col overflow-hidden bg-background",
            // 双栏时只保留圆角，不加描边 —— 与预览面板的分界由外壳底色差表达。
            "rounded-[var(--workspace-panel-radius)]",
            isLeftCollapsed && !isFocusMode && "w-14 min-w-14",
            (!isLeftCollapsed || isFocusMode) && "shrink-0"
          )}
          minSize={isFocusMode ? 24 : MIN_LEFT_WIDTH}
          maxSize={isFocusMode ? 28 : MAX_LEFT_WIDTH}
          defaultSize={isFocusMode ? IMMERSIVE_LEFT_WIDTH : leftPanelWidth}
        >
          {chatColumn}
        </Panel>

        {!isFocusMode && !isLeftCollapsed ? (
          <Separator
            aria-label="调整对话区和工作区宽度"
            className={cn(
              // ZCode WorkspaceShellLayout.tsx:1626 —— 分隔条本体是一条透明命中条，
              // 平时完全不可见（无底色、无描边、无手柄），只有 hover / 拖拽 / 焦点时才浮出指示线。
              // 宽度 `w-1`（4px）与 ZCode 的分隔条一致；容器 `gap-0`，所以两栏之间**就只有这 4px**，
              // 外壳浅底从这条缝里透出来当分界。
              "reactor-workspace-resizer group/handle relative z-10 flex h-full w-1 shrink-0 touch-none cursor-ew-resize items-center justify-center bg-transparent outline-none",
              "focus:outline-none focus-visible:ring-0",
              // 透明命中扩展：把 4px 视觉条的可拖拽区域撑到 12px，兼顾 ZCode 的极简外观与可用性。
              "before:absolute before:-inset-x-1 before:inset-y-0 before:content-['']",
              // 指示线：2px 圆角、三级前景半透明；上下按面板圆角内缩，默认完全透明。
              "after:pointer-events-none after:absolute after:left-1/2 after:-translate-x-1/2 after:w-0.5 after:rounded-full after:bg-foreground-subtlest/50 after:opacity-0 after:transition-opacity after:content-['']",
              "after:inset-y-[var(--workspace-panel-radius)]",
              // hover / 键盘焦点 / 拖拽中点亮（拖拽态由容器 data-workspace-resizing 驱动）
              "hover:after:opacity-100 focus-visible:after:opacity-100 [[data-workspace-resizing=true]_&]:after:opacity-100"
            )}
            title="拖拽调整左右区域宽度"
          />
        ) : null}

        <button
          type="button"
          className="reactor-workspace-scrim lg:hidden"
          aria-label="关闭工作区"
          onClick={closeMobileWorkspace}
        />

        <Panel
          id="workspace"
          className="reactor-workspace-panel relative flex min-h-0 flex-1 flex-col overflow-hidden rounded-[var(--workspace-panel-radius)] bg-background"
          data-workspace-collapsed="false"
        >
          {/* ActionView 常驻挂载，独占面板改为**覆盖层**。
                原因：ActionView 持有工作区 tab 列表（文件 / 子智能体详情 / 目录），
                一旦被独占面板顶掉就会卸载、tab 全部丢失 —— 而从子智能体详情里点
                思考块恰恰会走 openThinkingPanel，等于自毁当前 tab。 */}
          <ActionView
            key={conversation.id}
            pendingPreviewFile={pendingPreviewFile}
            onPendingPreviewFileConsumed={clearPendingPreviewFile}
            pendingSourceTab={pendingSourceTab}
            onPendingSourceTabConsumed={() => setPendingSourceTab(null)}
            pendingAgentTab={pendingAgentTab}
            onPendingAgentTabConsumed={() => setPendingAgentTab(null)}
            pendingAgentDirectoryTab={pendingAgentDirectory}
            onPendingAgentDirectoryTabConsumed={() =>
              setPendingAgentDirectory(false)
            }
            pendingTaskTab={pendingTaskTab}
            onPendingTaskTabConsumed={() => setPendingTaskTab(null)}
            agentRoster={subAgentRoster}
            onOpenAgent={openAgentPanel}
            renderAgentTab={renderAgentTab}
            renderTaskTab={renderTaskTab}
            onTabsChange={handleWorkspaceTabsChange}
            workspaceCaption={workspaceCaption}
            taskList={sessionFileTasks}
            plan={plan}
            isFocusMode={isFocusMode}
            onToggleFocusMode={toggleFocusMode}
            ref={actionViewRef}
            onClose={closeActionView}
          />

          {pendingDesktopStreamUrl ? (
            <div className="absolute inset-0 z-20 flex min-h-0 flex-col bg-background">
              <DesktopPreviewPanel
                streamUrl={pendingDesktopStreamUrl}
                onClose={closeActionView}
              />
            </div>
          ) : thinkingDetail != null ? (
            <div className="absolute inset-0 z-20 flex min-h-0 flex-col bg-background">
              <ThinkingPanel
                text={thinkingDetail}
                onClose={() => {
                  closeThinkingPanel();
                  if (!showAction && !workspaceOpenRequested) {
                    // 走统一的收起入口：tab 哨兵必须一起复位，否则下次打开时
                    // ActionView 首次上报的 0 会被误判成「刚关掉最后一个 tab」。
                    collapseWorkspace();
                  }
                }}
              />
            </div>
          ) : null}
        </Panel>
      </Group>
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
