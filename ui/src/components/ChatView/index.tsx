import { useEffect, useMemo, useRef, useState } from "react";
import { ActionViewItemEnum } from "@/utils";
import querySSE from "@/utils/querySSE";
import { getStableTaskIdentity } from "@/utils/chat";
import Dialogue from "@/components/Dialogue";
import DataDialogue from "@/components/Dialogue/DataDialogue";
import GeneralInput from "@/components/GeneralInput";
import ActionView from "@/components/ActionView";
import SessionTaskComposerBar from "./SessionTaskComposerBar";
import { getProductByType, toRequestOutputStyle } from "@/utils/constants";
import { useMemoizedFn } from "ahooks";
import classNames from "classnames";
import { Modal } from "antd";
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation";
import { FolderOpen, PanelLeftClose, PanelRightClose } from "lucide-react";
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
    regenerateLastMessage,
  } = useConversationStream({
    conversation,
    onConversationChange,
    onPrepareStreamingWorkspace: () => {
      // 新一轮请求开始后，工作区恢复自动跟随，避免仍停留在上一轮手动点开的旧任务上。
      setActiveTask(undefined);
      setPendingPreviewFile(undefined);
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
  }, [conversation.id]);

  useEffect(() => {
    // 任务列表会在流式过程中不断补全字段。保留稳定 key 后用最新任务替换
    // 当前选中对象，既能更新状态，又不会因为对象引用变化丢失用户选择。
    setActiveTask((prevActiveTask) => {
      if (!prevActiveTask) {
        return prevActiveTask;
      }

      const activeTaskKey = getTaskStableKey(prevActiveTask);
      if (!activeTaskKey) {
        return prevActiveTask;
      }

      const matchedTask = taskList.find((task) => getTaskStableKey(task) === activeTaskKey);
      if (matchedTask) {
        return matchedTask;
      }

      if (getTaskStableKey(workspaceStreamTask) === activeTaskKey && workspaceStreamTask) {
        return workspaceStreamTask;
      }

      return prevActiveTask;
    });
  }, [taskList, workspaceStreamTask]);

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

  const changeTask = (task: CHAT.Task, chat?: CHAT.ChatItem) => {
    // Structured data / 子智能体 JSON 观察值当前不渲染，禁止打开空白右侧面板。
    if (!canOpenTaskWorkspacePanel(task)) {
      return;
    }
    // 任务点击同时建立工作区可见性和当前运行快照；先恢复动态跟随，避免
    // 上一轮打开的文件预览遮住用户刚选择的任务。
    setWorkspaceOpenRequested(true);
    setIsRightCollapsed(false);
    // 工具点击回到「动态」预览，避免被已打开的文件 tab 挡住
    setPendingPreviewFile(undefined);
    actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
    changeActionStatus(true);
    setActiveTask(task);
    setActiveRunState({
      status: chat?.metrics?.status,
      finishedAt: chat?.finishedAt,
    });
  };

  const changeFile = useMemoizedFn((file: CHAT.TFile, chat?: CHAT.ChatItem) => {
    // 只打开右侧预览 tab；左侧文件管理仅由「查看当前会话的文件」入口进入
    setWorkspaceOpenRequested(true);
    setIsRightCollapsed(false);
    changeActionStatus(true);
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
    setWorkspaceOpenRequested(true);
    setIsRightCollapsed(false);
    changeActionStatus(true);
    actionViewRef.current?.openPlanView();
  };

  const toggleRightPanel = useMemoizedFn(() => {
    setWorkspaceOpenRequested(true);
    changeActionStatus(isRightCollapsed);
    toggleWorkspaceRightPanel();
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

  const handleRegenerate = useMemoizedFn(() => {
    regenerateLastMessage();
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

  // 顶栏只展示运行状态（含模型重试），不再显示会话标题
  const headerStatus = useMemo(() => {
    const activeChat = conversation.chatList?.[conversation.chatList.length - 1];
    if (!(loading || activeChat?.loading)) {
      return "";
    }
    const tip = activeChat?.tip?.trim();
    return tip || "正在推进任务…";
  }, [conversation.chatList, loading]);

  const renderHeaderStatus = (opts?: { showDeepThink?: boolean; badge?: string }) => (
    <div className="flex min-w-0 items-center gap-3">
      {headerStatus ? (
        <div
          className="thinking-shimmer truncate text-[16px] font-semibold tracking-tight text-[var(--chat-text-soft)]"
          role="status"
          aria-live="polite"
        >
          {headerStatus}
        </div>
      ) : (
        <div className="h-[24px]" aria-hidden="true" />
      )}
      {opts?.showDeepThink && conversation.deepThink ? (
        <div className="flex shrink-0 items-center gap-1.5 rounded-full bg-[var(--chat-surface-muted)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
          <i className="font_family icon-shendusikao text-[11px]"></i>
          <span>深度研究</span>
        </div>
      ) : null}
      {opts?.badge ? (
        <div className="flex shrink-0 items-center gap-1.5 rounded-full bg-[var(--chat-surface-muted)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
          <i className="font_family icon-shendusikao text-[11px]"></i>
          <span>{opts.badge}</span>
        </div>
      ) : null}
    </div>
  );

  const renderChatDialogues = () => (
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
          onRegenerate={readOnly ? undefined : handleRegenerate}
        />
      ))}
    </>
  );

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
    if (!showAction && !workspaceOpenRequested) {
      return (
        <div className="flex h-full w-full justify-center overflow-hidden bg-stone-50 px-4 pt-4 md:px-6">
          <div
            className="flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden"
            id="chat-view"
          >
            <div className="mb-3 flex min-h-[36px] items-center justify-between px-1">
              {renderHeaderStatus({ showDeepThink: true })}
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
              <div className="shrink-0 bg-gradient-to-t from-stone-50 via-stone-50/95 to-transparent pb-5 pt-4">
                <div className="mx-auto w-full max-w-[860px]">
                  <SessionTaskComposerBar
                    chat={conversation.chatList?.[conversation.chatList.length - 1]}
                    taskList={taskList}
                  />
                  <GeneralInput
                    key={`input-${conversation.sessionId}-single`}
                    sessionId={conversation.sessionId}
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
          "flex h-full w-full gap-0.5 bg-stone-50 p-2",
          isDragging && "cursor-col-resize select-none"
        )}
      >
        {/* Left Panel - Chat Area */}
        <div
          className={classNames(
            "flex min-h-0 flex-col overflow-hidden rounded-[24px] bg-white",
            isDragging
              ? "transition-none"
              : "transition-[width,min-width,max-width,opacity] duration-[280ms] ease-[cubic-bezier(0.77,0,0.175,1)]",
            isLeftCollapsed && !isFocusMode && "w-14 min-w-14",
            (!isLeftCollapsed || isFocusMode) && "shrink-0"
          )}
          style={{
            ...((!isLeftCollapsed || isFocusMode)
              ? {
                width: `${leftPanelWidth}%`,
                minWidth: isFocusMode ? 280 : undefined,
                maxWidth: isFocusMode ? "28%" : undefined,
              }
              : {}),
            boxShadow: "var(--chat-soft-shadow)",
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
              {/* Header：运行状态（替换会话标题） */}
              <div className={classNames(
                "flex items-center justify-between py-4",
                isFocusMode ? "px-3" : "px-5"
              )}>
                {renderHeaderStatus({ showDeepThink: !isFocusMode })}
                {!isFocusMode ? (
                  <button
                    type="button"
                    onClick={() => onOpenTaskFiles?.()}
                    className="flex h-8 w-8 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                    title="查看当前会话的文件"
                    aria-label="查看当前会话的文件"
                  >
                    <FolderOpen className="h-4 w-4" />
                  </button>
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
                  <div className={classNames(
                    "shrink-0 bg-gradient-to-t from-white via-white/95 to-transparent pb-4 pt-3",
                    isFocusMode ? "px-2" : "px-4"
                  )}>
                    <SessionTaskComposerBar
                      chat={conversation.chatList?.[conversation.chatList.length - 1]}
                      taskList={taskList}
                    />
                    <GeneralInput
                      key={`input-${conversation.sessionId}-left`}
                      sessionId={conversation.sessionId}
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
              "group relative flex w-4 shrink-0 touch-none cursor-col-resize items-center justify-center rounded-full transition-colors",
              "hover:bg-[#0071e3]/10",
              isDragging && "bg-[#0071e3]/15"
            )}
            title="拖拽调整左右区域宽度"
          >
            {/* 宽命中区保障可拖动，内部细线保持界面克制。 */}
            <div className="absolute inset-y-3 left-1/2 w-px -translate-x-1/2 bg-[#e5e5ea] transition-colors group-hover:bg-[#b9b9c0]" />
            <div
              className={classNames(
                "relative h-14 w-1 rounded-full transition-colors duration-150",
                isDragging
                  ? "bg-[#0071e3]"
                  : "bg-[#d2d2d7] group-hover:bg-[#86868b]"
              )}
            />
          </div>
        )}

        {/* Right Panel - Action/Workspace Area */}
        <div
          className={classNames(
            "flex min-h-0 flex-col overflow-hidden rounded-[24px] bg-white",
            isDragging
              ? "transition-none"
              : "transition-[width,min-width,max-width,opacity] duration-[280ms] ease-[cubic-bezier(0.77,0,0.175,1)]",
            isRightCollapsed && "w-14 min-w-14",
            !isRightCollapsed && "flex-1"
          )}
          style={{ boxShadow: "var(--chat-soft-shadow)" }}
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
          ) : (
            // 展开状态 - 工作空间
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
      <div className="flex h-full w-full justify-center overflow-hidden bg-stone-50 px-4 pt-4 md:px-6">
        <div
          className="flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden"
          id="chat-view"
        >
          <div className="mb-3 flex min-h-[36px] items-center justify-between px-1">
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
            <div className="shrink-0 bg-gradient-to-t from-stone-50 via-stone-50/95 to-transparent pb-5 pt-4">
              <div className="mx-auto w-full max-w-[860px]">
                <GeneralInput
                  key={`input-${conversation.sessionId}-data`}
                  sessionId={conversation.sessionId}
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
