import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { ActionViewItemEnum } from "@/utils";
import querySSE from "@/utils/querySSE";
import { getStableTaskIdentity } from "@/utils/chat";
import Dialogue from "@/components/Dialogue";
import DataDialogue from "@/components/Dialogue/DataDialogue";
import GeneralInput from "@/components/GeneralInput";
import ActionView from "@/components/ActionView";
import { productList, defaultProduct } from "@/utils/constants";
import { useMemoizedFn } from "ahooks";
import classNames from "classnames";
import { Modal } from "antd";
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation";
import { PanelLeftClose, PanelRightClose } from "lucide-react";
import { parseDataChatEvent } from "@/utils/sseParsers";
import type { DataConversationRuntime } from "./chatView.types";
import {
  createConversationDraftController,
  createDraftConversation,
  useConversationStream,
} from "./useConversationStream";
import { useWorkspacePanels } from "./useWorkspacePanels";

type Props = {
  inputInfo: CHAT.TInputInfo;
  product?: CHAT.Product;
  conversation: CHAT.ConversationHistory;
  chatRoles: CHAT.FixRole[];
  onConversationChange: (
    conversationId: string,
    nextConversation: CHAT.ConversationHistory
  ) => void;
  onRoleSelect: (role: CHAT.FixRole) => void;
  onInputConsumed?: () => void;
};

const getProductByType = (type?: string) => {
  return productList.find((item) => item.type === type) ?? defaultProduct;
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
    onConversationChange,
    onRoleSelect,
    onInputConsumed,
  } = props;

  const [activeTask, setActiveTask] = useState<CHAT.Task>();
  const {
    leftPanelWidth,
    isDragging,
    isLeftCollapsed,
    isRightCollapsed,
    isFocusMode,
    containerRef,
    handleDragStart,
    setIsRightCollapsed,
    setIsFocusMode,
    toggleLeftPanel,
    toggleRightPanel: toggleWorkspaceRightPanel,
    toggleFocusMode,
  } = useWorkspacePanels();
  const actionViewRef = ActionView.useActionView();
  const [modal, contextHolder] = Modal.useModal();
  const conversationRef = useRef(conversation);
  const [isConversationSwitching, setIsConversationSwitching] = useState(false);
  const [dataLoading, setDataLoading] = useState(false);
  const {
    taskList,
    workspaceStreamTask,
    activeRunState,
    setActiveRunState,
    plan,
    showAction,
    changeActionStatus,
    loading: streamLoading,
    streamingThoughtMap,
    sendMessage,
    regenerateLastMessage,
  } = useConversationStream({
    conversation,
    onConversationChange,
    onPrepareStreamingWorkspace: () => {
      // 新一轮请求开始后，工作区恢复自动跟随，避免仍停留在上一轮手动点开的旧任务上。
      setActiveTask(undefined);
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
    setDataLoading(false);
  }, [conversation.id]);

  useEffect(() => {
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

  // Ensure fade-in starts before the browser paints after conversation switch.
  useLayoutEffect(() => {
    setIsConversationSwitching(true);
    const timer = setTimeout(() => setIsConversationSwitching(false), 220);
    return () => clearTimeout(timer);
  }, [conversation.id]);

  const commitConversation = useMemoizedFn(
    (conversationId: string, nextConversation: CHAT.ConversationHistory) => {
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
        setDataLoading(false);
        break;
      case "READY":
        runtime.currentChat.loading = false;
        setDataLoading(false);
        break;
      default:
        break;
    }

    const nextConversation = runtime.draftController.replaceLastItem({ ...runtime.currentChat });
    runtime.draftController.commit(nextConversation);
  });

  const changeTask = (task: CHAT.Task, chat?: CHAT.ChatItem) => {
    setIsRightCollapsed(false);
    actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
    changeActionStatus(true);
    setActiveTask(task);
    setActiveRunState({
      status: chat?.metrics?.status,
      finishedAt: chat?.finishedAt,
    });
  };

  const changeFile = (file: CHAT.TFile, chat?: CHAT.ChatItem) => {
    setIsRightCollapsed(false);
    changeActionStatus(true);
    setActiveRunState({
      status: chat?.metrics?.status,
      finishedAt: chat?.finishedAt,
    });
    actionViewRef.current?.setFilePreview(file);
  };

  const changePlan = () => {
    setIsRightCollapsed(false);
    changeActionStatus(true);
    actionViewRef.current?.openPlanView();
  };

  const toggleRightPanel = useMemoizedFn(() => {
    changeActionStatus(isRightCollapsed);
    toggleWorkspaceRightPanel();
  });

  const sendDataMessage = useMemoizedFn((inputInfo: CHAT.TInputInfo) => {
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
    if (inputInfoProp.message?.length !== 0) {
      const targetOutput =
        inputInfoProp.outputStyle || conversationRef.current.productType;
      if (targetOutput === "dataAgent" && !inputInfoProp.deepThink) {
        sendDataMessage(inputInfoProp);
      } else {
        sendMessage(inputInfoProp);
      }
      onInputConsumed?.();
    }
  }, [inputInfoProp, onInputConsumed, sendDataMessage, sendMessage]);

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

  const headerTitle = conversation.chatTitle || conversation.title;

  const renderChatDialogues = () => {
    if (isConversationSwitching) {
      return (
        <motion.div
          key={`switch-${conversation.id}`}
          initial={{
            opacity: 0.9,
            y: 6
          }}
          animate={{
            opacity: 1,
            y: 0
          }}
          transition={{
            duration: 0.14,
            ease: [0.25, 0.46, 0.45, 0.94]
          }}
        >
          {conversation.chatList.map((chat) => (
            <Dialogue
              key={chat.requestId}
              chat={chat}
              streamingThought={streamingThoughtMap[chat.requestId]}
              deepThink={conversation.deepThink}
              changeTask={changeTask}
              changeFile={changeFile}
              changePlan={changePlan}
              onRegenerate={handleRegenerate}
            />
          ))}
        </motion.div>
      );
    }

    return (
      <AnimatePresence mode="popLayout" initial={false}>
        {conversation.chatList.map((chat) => (
          <motion.div
            key={chat.requestId}
            initial={{
              opacity: 0.9,
              y: 6
            }}
            animate={{
              opacity: 1,
              y: 0
            }}
            exit={{
              opacity: 0.85,
              y: -4
            }}
            transition={{
              duration: 0.14,
              ease: [0.25, 0.46, 0.45, 0.94],
            }}
          >
            <Dialogue
              chat={chat}
              streamingThought={streamingThoughtMap[chat.requestId]}
              deepThink={conversation.deepThink}
              changeTask={changeTask}
              changeFile={changeFile}
              changePlan={changePlan}
              onRegenerate={handleRegenerate}
            />
          </motion.div>
        ))}
      </AnimatePresence>
    );
  };

  const renderDataDialogues = () => {
    const visibleDataChats = optimisticDataChat
      ? [...conversation.dataChatList, optimisticDataChat]
      : conversation.dataChatList;

    if (isConversationSwitching) {
      return (
        <motion.div
          key={`switch-data-${conversation.id}`}
          initial={{
            opacity: 0.9,
            y: 6
          }}
          animate={{
            opacity: 1,
            y: 0
          }}
          transition={{
            duration: 0.14,
            ease: [0.25, 0.46, 0.45, 0.94]
          }}
        >
          {visibleDataChats.map((chat, idx) => (
            <DataDialogue key={`${conversation.id}-${idx}`} chat={chat} />
          ))}
        </motion.div>
      );
    }

    return (
      <AnimatePresence mode="popLayout" initial={false}>
        {visibleDataChats.map((chat, idx) => (
          <motion.div
            key={`${conversation.id}-${idx}`}
            initial={{
              opacity: 0.9,
              y: 6
            }}
            animate={{
              opacity: 1,
              y: 0
            }}
            exit={{
              opacity: 0.85,
              y: -4
            }}
            transition={{
              duration: 0.14,
              ease: [0.25, 0.46, 0.45, 0.94],
            }}
          >
            <DataDialogue chat={chat} />
          </motion.div>
        ))}
      </AnimatePresence>
    );
  };

  const renderMultAgent = () => {
    // 如果没有工作空间内容，显示单面板
    if (!showAction) {
      return (
        <div className="flex h-full w-full justify-center overflow-hidden px-4 pt-4 md:px-6">
          <div
            className="flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden"
            id="chat-view"
          >
            <div className="mb-3 flex min-h-[36px] items-center justify-between px-1">
              <div className="flex min-w-0 items-center gap-3">
                <h2 className="truncate text-[16px] font-semibold tracking-tight text-[var(--chat-text)]">
                  {headerTitle}
                </h2>
                {conversation.deepThink && (
                  <div className="flex shrink-0 items-center gap-1.5 rounded-full bg-[var(--chat-surface-muted)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
                    <i className="font_family icon-shendusikao text-[11px]"></i>
                    <span>深度研究</span>
                  </div>
                )}
              </div>
            </div>

            <Conversation className="chat-fade-bottom min-h-0 flex-1 overflow-hidden">
              <ConversationContent className="mx-auto w-full max-w-[860px] px-1 pb-6">
                {renderChatDialogues()}
              </ConversationContent>
              <ConversationScrollButton />
            </Conversation>

            <div className="shrink-0 bg-gradient-to-t from-[var(--page-gradient)] via-[var(--page-gradient)]/95 to-transparent pb-5 pt-4">
              <div className="mx-auto w-full max-w-[860px]">
                <GeneralInput
                  key={`input-${conversation.sessionId}-single`}
                  sessionId={conversation.sessionId}
                  placeholder={
                    conversation.role?.available === false
                      ? "当前角色已失效，请新建对话后重新选择角色"
                      : loading
                        ? "任务进行中..."
                        : "希望 Reactor 为你做哪些任务呢？"
                  }
                  showBtn={false}
                  size="medium"
                  disabled={loading || conversation.role?.available === false}
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
                      outputStyle: conversation.productType,
                      deepThink: conversation.deepThink,
                      aiAgentId: conversation.role?.agentId,
                    })
                  }
                />
              </div>
            </div>
          </div>
        </div>
      );
    }

    // 38/62 双面板布局；专注模式隐藏对话区，把工作区拉满
    return (
      <div
        ref={containerRef}
        className="flex h-full w-full gap-0.5 p-2"
      >
        {/* Left Panel - Chat Area */}
        {!isFocusMode && (
        <div
          className={classNames(
            "flex min-h-0 flex-col overflow-hidden rounded-[24px] bg-white/90 transition-all duration-300",
            isLeftCollapsed && "w-14 min-w-14",
            !isLeftCollapsed && "flex-1"
          )}
          style={!isLeftCollapsed ? { flex: `0 0 ${leftPanelWidth}%` } : undefined}
        >
          {isLeftCollapsed ? (
            // 折叠状态
            <div className="flex h-full flex-col items-center py-4">
              <button
                onClick={toggleLeftPanel}
                className="flex h-10 w-10 items-center justify-center rounded-full text-[#86868b] transition-colors hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
                title="展开聊天区"
              >
                <PanelRightClose className="h-5 w-5" />
              </button>
            </div>
          ) : (
            // 展开状态
            <>
              {/* Header */}
              <div className="flex items-center justify-between px-5 py-4">
                <div className="flex min-w-0 items-center gap-3">
                  <h2 className="truncate text-[17px] font-semibold tracking-tight text-[#1d1d1f]">
                    {headerTitle}
                  </h2>
                  {conversation.deepThink && (
                    <div className="flex shrink-0 items-center gap-1.5 rounded-full bg-[#1d1d1f] px-3 py-1 text-[12px] font-medium text-white">
                      <i className="font_family icon-shendusikao text-[11px]"></i>
                      <span>深度研究</span>
                    </div>
                  )}
                </div>
                <button
                  onClick={toggleLeftPanel}
                  className="flex h-8 w-8 items-center justify-center rounded-full text-[#86868b] transition-colors hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
                  title="收起聊天区"
                >
                  <PanelLeftClose className="h-4 w-4" />
                </button>
              </div>

              {/* Messages */}
              <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
                <Conversation className="chat-fade-bottom min-h-0 flex-1 overflow-hidden px-5 pt-5">
                  <ConversationContent>
                    {renderChatDialogues()}
                  </ConversationContent>
                  <ConversationScrollButton />
                </Conversation>

                {/* Input */}
                <div className="shrink-0 bg-gradient-to-t from-white via-white/95 to-transparent px-4 pb-4 pt-3">
                  <GeneralInput
                    key={`input-${conversation.sessionId}-left`}
                    sessionId={conversation.sessionId}
                    placeholder={
                      conversation.role?.available === false
                        ? "当前角色已失效，请新建对话后重新选择角色"
                        : loading
                          ? "任务进行中..."
                          : "希望 Reactor 为你做哪些任务呢？"
                    }
                    showBtn={false}
                    size="medium"
                    disabled={loading || conversation.role?.available === false}
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
                        outputStyle: conversation.productType,
                        deepThink: conversation.deepThink,
                        aiAgentId: conversation.role?.agentId,
                      })
                    }
                  />
                </div>
              </div>
            </>
          )}
        </div>
        )}

        {/* Drag Handle */}
        {!isFocusMode && !isLeftCollapsed && !isRightCollapsed && (
          <div
            onMouseDown={handleDragStart}
            className={classNames(
              "group relative flex w-3 shrink-0 cursor-col-resize items-center justify-center transition-colors",
              "hover:bg-[#0071e3]/8",
              isDragging && "bg-[#0071e3]/16"
            )}
            title="拖拽调整左右区域宽度"
          >
            {/* Wider hit area with slim visual indicator */}
            <div
              className={classNames(
                "h-10 w-0.5 rounded-full transition-all duration-200",
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
            "flex min-h-0 flex-col overflow-hidden rounded-[24px] bg-white/90 transition-all duration-300",
            isRightCollapsed && "w-14 min-w-14",
            !isRightCollapsed && "flex-1"
          )}
          style={!isRightCollapsed && !isFocusMode ? { flex: `0 0 ${100 - leftPanelWidth - (isLeftCollapsed ? 0 : 0)}%` } : undefined}
        >
          {isRightCollapsed ? (
            // 折叠状态
            <div className="flex h-full flex-col items-center py-4">
              <button
                onClick={toggleRightPanel}
                className="flex h-10 w-10 items-center justify-center rounded-full text-[#86868b] transition-colors hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
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
              taskList={taskList}
              plan={plan}
              runState={activeRunState}
              isFocusMode={isFocusMode}
              onToggleFocusMode={toggleFocusMode}
              ref={actionViewRef}
              onClose={() => {
                if (isFocusMode) {
                  setIsFocusMode(false);
                } else {
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
      <div className="flex h-full w-full justify-center overflow-hidden px-4 pt-4 md:px-6">
        <div
          className="flex h-full min-h-0 w-full max-w-[980px] flex-col overflow-hidden"
          id="chat-view"
        >
          <div className="mb-3 flex min-h-[36px] items-center justify-between px-1">
            <div className="flex min-w-0 items-center gap-3">
              <h2 className="truncate text-[16px] font-semibold tracking-tight text-[var(--chat-text)]">
                {headerTitle}
              </h2>
              <div className="flex shrink-0 items-center gap-1.5 rounded-full bg-[var(--chat-surface-muted)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
                <i className="font_family icon-shendusikao text-[11px]"></i>
                <span>数据分析</span>
              </div>
            </div>
          </div>

          <Conversation className="chat-fade-bottom min-h-0 flex-1 overflow-hidden">
            <ConversationContent className="mx-auto w-full max-w-[860px] px-1 pb-6">
              {renderDataDialogues()}
            </ConversationContent>
            <ConversationScrollButton />
          </Conversation>

          <div className="shrink-0 bg-gradient-to-t from-[var(--page-gradient)] via-[var(--page-gradient)]/95 to-transparent pb-5 pt-4">
            <div className="mx-auto w-full max-w-[860px]">
              <GeneralInput
                key={`input-${conversation.sessionId}-data`}
                sessionId={conversation.sessionId}
                placeholder={loading ? "任务进行中..." : "希望 Reactor 为你做哪些任务呢？"}
                showBtn={false}
                size="medium"
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
        </div>
      </div>
    );
  };

  const isDataConversation =
    conversation.productType === "dataAgent" && !conversation.deepThink;

  return (
    <div className="flex h-full w-full justify-center">
      {isDataConversation ? renderDataAgent() : renderMultAgent()}
    </div>
  );
};

export default ChatView;
