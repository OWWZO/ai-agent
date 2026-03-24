import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { ActionViewItemEnum, getUniqId } from "@/utils";
import querySSE from "@/utils/querySSE";
import { handleTaskData, combineData } from "@/utils/chat";
import Dialogue from "@/components/Dialogue";
import DataDialogue from "@/components/Dialogue/DataDialogue";
import GeneralInput from "@/components/GeneralInput";
import ActionView from "@/components/ActionView";
import { RESULT_TYPES, productList, defaultProduct } from "@/utils/constants";
import { useMemoizedFn } from "ahooks";
import classNames from "classnames";
import { Modal } from "antd";
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation";
import { PanelLeftClose, PanelRightClose } from "lucide-react";

type Props = {
  inputInfo: CHAT.TInputInfo;
  product?: CHAT.Product;
  conversation: CHAT.ConversationHistory;
  onConversationChange: (
    conversationId: string,
    nextConversation: CHAT.ConversationHistory
  ) => void;
  onInputConsumed?: () => void;
};

const getProductByType = (type?: string) => {
  return productList.find((item) => item.type === type) ?? defaultProduct;
};

const ChatView: GenieType.FC<Props> = (props) => {
  const {
    inputInfo: inputInfoProp,
    product,
    conversation,
    onConversationChange,
    onInputConsumed,
  } = props;

  const [taskList, setTaskList] = useState<MESSAGE.Task[]>([]);
  const [activeTask, setActiveTask] = useState<CHAT.Task>();
  const [plan, setPlan] = useState<CHAT.Plan>();
  const [showAction, setShowAction] = useState(false);
  const [loading, setLoading] = useState(false);
  const [, setChatVersion] = useState(0);
  const actionViewRef = ActionView.useActionView();
  const [modal, contextHolder] = Modal.useModal();
  const conversationRef = useRef(conversation);

  useEffect(() => {
    conversationRef.current = conversation;
  }, [conversation]);

  useEffect(() => {
    setTaskList([]);
    setActiveTask(undefined);
    setPlan(undefined);
    setShowAction(false);
    setLoading(false);
  }, [conversation.id]);

  const commitConversation = useMemoizedFn(
    (conversationId: string, nextConversation: CHAT.ConversationHistory) => {
      onConversationChange(conversationId, {
        ...nextConversation,
        updatedAt: Date.now(),
      });
    }
  );

  const combineCurrentChat = (
    inputInfo: CHAT.TInputInfo,
    sessionId: string,
    requestId: string
  ): CHAT.ChatItem => {
    return {
      query: inputInfo.message!,
      files: inputInfo.files!,
      responseType: "txt",
      sessionId,
      requestId,
      loading: true,
      forceStop: false,
      tasks: [],
      thought: "",
      response: "",
      taskStatus: 0,
      tip: "",
      multiAgent: { tasks: [] },
    };
  };

  const sendMessage = useMemoizedFn((inputInfo: CHAT.TInputInfo) => {
    const baseConversation = conversationRef.current;
    const conversationId = baseConversation.id;
    const { message, deepThink, outputStyle } = inputInfo;
    const requestId = getUniqId();
    let currentChat = combineCurrentChat(inputInfo, baseConversation.sessionId, requestId);
    const isChatMode = outputStyle === "chat";
    const normalizedDeepThink = isChatMode ? false : Boolean(deepThink);
    let runningConversation: CHAT.ConversationHistory = {
      ...baseConversation,
      chatTitle: baseConversation.chatTitle || message || "",
      title:
        baseConversation.title === "新对话" && message
          ? message.slice(0, 30)
          : baseConversation.title,
      productType: outputStyle || baseConversation.productType,
      deepThink: normalizedDeepThink,
      chatList: [...baseConversation.chatList, currentChat],
    };

    commitConversation(conversationId, runningConversation);
    setLoading(true);

    const params = {
      sessionId: baseConversation.sessionId,
      requestId,
      query: message,
      deepThink: normalizedDeepThink ? 1 : 0,
      outputStyle: outputStyle || baseConversation.productType,
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
      if (!pendingConversation && !pendingTaskData && !taskDataDirty) return;
      const now = performance.now();
      if (taskDataDirty && (force || now - lastTaskFlushAt >= TASK_FLUSH_INTERVAL)) {
        pendingTaskData = handleTaskData(
          currentChat,
          normalizedDeepThink,
          currentChat.multiAgent
        );
        taskDataDirty = false;
      }
      const shouldFlushConversation =
        !!pendingConversation &&
        (force || now - lastConversationFlushAt >= CONVERSATION_FLUSH_INTERVAL);
      const shouldFlushTask =
        !!pendingTaskData && (force || now - lastTaskFlushAt >= TASK_FLUSH_INTERVAL);

      if (shouldFlushTask && pendingTaskData) {
        setTaskList(pendingTaskData.taskList);
        temporaryChangeTask(pendingTaskData.taskList);
        updatePlan(pendingTaskData.plan!);
        openAction(pendingTaskData.taskList);
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

      if (pendingFlushFrame) return;
      pendingFlushFrame = requestAnimationFrame(() => {
        pendingFlushFrame = null;
        flushNonChatUpdates(false);
        if (pendingConversation || pendingTaskData) {
          scheduleNonChatFlush(false);
        }
      });
    };

    const handleMessage = (data: MESSAGE.Answer) => {
      const { finished, resultMap, packageType, status } = data;
      if (status === "tokenUseUp") {
        modal.info({
          title: "您的试用次数已用尽",
          content: "如需额外申请，请联系 liyang.1236@jd.com",
        });
        const taskData = handleTaskData(
          currentChat,
          normalizedDeepThink,
          currentChat.multiAgent
        );
        currentChat.loading = false;
        setLoading(false);
        setTaskList(taskData.taskList);
        runningConversation = {
          ...runningConversation,
          chatList: runningConversation.chatList.map((chat) =>
            chat.requestId === currentChat.requestId ? currentChat : chat
          ),
        };
        commitConversation(conversationId, runningConversation);
        return;
      }
      if (packageType !== "heartbeat") {
        if (isChatMode) {
          requestAnimationFrame(() => {
            const eventData: any = resultMap?.eventData;
            const inner = eventData?.resultMap;
            const innerType = inner?.messageType;
            if (innerType === "agent_stream") {
              const text = inner?.result || "";
              if (text) {
                currentChat.response = (currentChat.response || "") + text;
              }
            } else if (innerType === "result") {
              if (!currentChat.response) {
                currentChat.response = inner?.result || "";
              }
            }

            if (innerType) {
              const newChatList = [...runningConversation.chatList];
              newChatList.splice(newChatList.length - 1, 1, currentChat);
              runningConversation = {
                ...runningConversation,
                chatList: newChatList,
              };
              commitConversation(conversationId, runningConversation);
              setChatVersion((v) => v + 1);
            }

            if (innerType && (inner?.finish || finished)) {
              currentChat.loading = false;
              setLoading(false);
              const newChatList = [...runningConversation.chatList];
              newChatList.splice(newChatList.length - 1, 1, currentChat);
              runningConversation = {
                ...runningConversation,
                chatList: newChatList,
              };
              commitConversation(conversationId, runningConversation);
            }
          });
          return;
        }

        if (resultMap?.eventData) {
          currentChat = combineData(resultMap.eventData || {}, currentChat);
          taskDataDirty = true;
          if (finished) {
            currentChat.loading = false;
            setLoading(false);
          }
          const newChatList = [...runningConversation.chatList];
          newChatList.splice(newChatList.length - 1, 1, currentChat);
          runningConversation = {
            ...runningConversation,
            chatList: newChatList,
          };
          pendingConversation = runningConversation;
          scheduleNonChatFlush(finished);
        }
      }
    };

    const openAction = (tasks: MESSAGE.Task[]) => {
      if (tasks.filter((item) => !RESULT_TYPES.includes(item.messageType)).length) {
        setShowAction(true);
      }
    };

    const handleError = (error: unknown) => {
      throw error;
    };

    const handleClose = () => {
      scheduleNonChatFlush(true);
      console.log("close");
    };

    querySSE({
      body: params,
      handleMessage,
      handleError,
      handleClose,
    });
  });

  const temporaryChangeTask = (tasks: MESSAGE.Task[]) => {
    const task = tasks[tasks.length - 1] as CHAT.Task;
    if (!["task_summary", "result"].includes(task?.messageType)) {
      setActiveTask(task);
    }
  };

  const changeTask = (task: CHAT.Task) => {
    actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
    changeActionStatus(true);
    setActiveTask(task);
  };

  const updatePlan = (currentPlan: CHAT.Plan) => {
    setPlan(currentPlan);
  };

  const changeFile = (file: CHAT.TFile) => {
    changeActionStatus(true);
    actionViewRef.current?.setFilePreview(file);
  };

  const changePlan = () => {
    changeActionStatus(true);
    actionViewRef.current?.openPlanView();
  };

  const changeActionStatus = (status: boolean) => {
    setShowAction(status);
  };

  const sendDataMessage = useMemoizedFn((inputInfo: CHAT.TInputInfo) => {
    const baseConversation = conversationRef.current;
    const conversationId = baseConversation.id;
    const params = {
      content: inputInfo.message,
    };
    const currentChat = {
      query: inputInfo.message,
      loading: true,
      think: "",
      chartData: undefined,
      error: "",
    };
    let runningConversation: CHAT.ConversationHistory = {
      ...baseConversation,
      chatTitle: baseConversation.chatTitle || inputInfo.message || "",
      title:
        baseConversation.title === "新对话" && inputInfo.message
          ? inputInfo.message.slice(0, 30)
          : baseConversation.title,
      productType: "dataAgent",
      deepThink: false,
      dataChatList: [...baseConversation.dataChatList, currentChat],
    };
    commitConversation(conversationId, runningConversation);
    setLoading(true);

    const handleMessage = (data: any) => {
      switch (data.eventType) {
        case "THINK":
          currentChat.think = data.data;
          break;
        case "CHART_DATA":
          currentChat.chartData = data.data;
          break;
        case "ERROR":
          currentChat.error = data.data;
          currentChat.loading = false;
          setLoading(false);
          break;
        case "READY":
          currentChat.loading = false;
          setLoading(false);
          break;
        default:
          break;
      }
      const newDataChatList = [...runningConversation.dataChatList];
      newDataChatList.splice(newDataChatList.length - 1, 1, currentChat);
      runningConversation = {
        ...runningConversation,
        dataChatList: newDataChatList,
      };
      commitConversation(conversationId, runningConversation);
    };
    const handleError = (error: unknown) => {
      throw error;
    };

    const handleClose = () => {
      console.log("close");
    };
    querySSE(
      {
        body: params,
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
    const last = conversation.chatList[conversation.chatList.length - 1];
    if (!last || loading) return;
    sendMessage({
      message: last.query,
      outputStyle: conversation.productType,
      deepThink: conversation.deepThink,
    });
  });

  const currentProduct = useMemo(() => {
    return getProductByType(conversation.productType || product?.type);
  }, [conversation.productType, product?.type]);

  const headerTitle = conversation.chatTitle || conversation.title;

  // 50/50 布局拖拽调整大小功能
  const [leftPanelWidth, setLeftPanelWidth] = useState(50); // 百分比
  const [isDragging, setIsDragging] = useState(false);
  const dragStartXRef = useRef(0);
  const dragStartWidthRef = useRef(50);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleDragStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsDragging(true);
    dragStartXRef.current = e.clientX;
    dragStartWidthRef.current = leftPanelWidth;
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  }, [leftPanelWidth]);

  useEffect(() => {
    const handleDragMove = (e: MouseEvent) => {
      if (!isDragging || !containerRef.current) return;
      const containerWidth = containerRef.current.offsetWidth;
      const deltaPixels = e.clientX - dragStartXRef.current;
      const deltaPercent = (deltaPixels / containerWidth) * 100;
      const newWidth = Math.max(30, Math.min(70, dragStartWidthRef.current + deltaPercent));
      setLeftPanelWidth(newWidth);
    };

    const handleDragEnd = () => {
      if (isDragging) {
        setIsDragging(false);
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
      }
    };

    if (isDragging) {
      document.addEventListener("mousemove", handleDragMove);
      document.addEventListener("mouseup", handleDragEnd);
    }

    return () => {
      document.removeEventListener("mousemove", handleDragMove);
      document.removeEventListener("mouseup", handleDragEnd);
    };
  }, [isDragging]);

  // 切换左右面板显示/隐藏
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);
  const [isRightCollapsed, setIsRightCollapsed] = useState(false);

  const toggleLeftPanel = useCallback(() => {
    setIsLeftCollapsed((prev) => !prev);
    if (isLeftCollapsed) {
      setLeftPanelWidth(50);
    }
  }, [isLeftCollapsed]);

  const toggleRightPanel = useCallback(() => {
    setIsRightCollapsed((prev) => !prev);
    if (isRightCollapsed) {
      setShowAction(true);
    } else {
      setShowAction(false);
    }
  }, [isRightCollapsed]);

  const renderMultAgent = () => {
    // 如果没有工作空间内容，显示单面板
    if (!showAction) {
      return (
        <div className="flex h-full w-full justify-center px-4 pt-4 md:px-6">
          <div className="flex min-h-0 w-full max-w-[980px] flex-col" id="chat-view">
            <div className="mb-3 flex min-h-[36px] items-center justify-between px-1">
              <div className="flex min-w-0 items-center gap-3">
                <h2 className="truncate text-[16px] font-semibold tracking-tight text-[var(--chat-text)]">
                  {headerTitle}
                </h2>
                {conversation.deepThink && (
                  <div className="flex shrink-0 items-center gap-1.5 rounded-full border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-1 text-[12px] font-medium text-[var(--chat-text-soft)]">
                    <i className="font_family icon-shendusikao text-[11px]"></i>
                    <span>深度研究</span>
                  </div>
                )}
              </div>
            </div>

            <Conversation className="chat-fade-bottom min-h-0 flex-1">
              <ConversationContent className="mx-auto w-full max-w-[860px] px-1 pb-6">
                <AnimatePresence mode="sync" initial={false}>
                  {conversation.chatList.map((chat) => (
                    <motion.div
                      key={chat.requestId}
                      initial={{ opacity: 0, y: 12 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -8 }}
                      transition={{
                        duration: 0.22,
                        ease: [0.25, 0.46, 0.45, 0.94],
                      }}
                    >
                      <Dialogue
                        chat={chat}
                        deepThink={conversation.deepThink}
                        changeTask={changeTask}
                        changeFile={changeFile}
                        changePlan={changePlan}
                        onRegenerate={handleRegenerate}
                      />
                    </motion.div>
                  ))}
                </AnimatePresence>
              </ConversationContent>
              <ConversationScrollButton />
            </Conversation>

            <div className="sticky bottom-0 z-10 bg-gradient-to-t from-[var(--page-gradient)] via-[var(--page-gradient)]/95 to-transparent pb-5 pt-4">
              <div className="mx-auto w-full max-w-[860px]">
                <GeneralInput
                  placeholder={loading ? "任务进行中..." : "希望 Genie 为你做哪些任务呢？"}
                  showBtn={false}
                  size="medium"
                  disabled={loading}
                  product={currentProduct}
                  send={(info) =>
                    sendMessage({
                      ...info,
                      outputStyle: conversation.productType,
                      deepThink: conversation.deepThink,
                    })
                  }
                />
              </div>
            </div>
          </div>
        </div>
      );
    }

    // 50/50 双面板布局
    return (
      <div
        ref={containerRef}
        className="flex h-full w-full gap-0.5 p-2"
      >
        {/* Left Panel - Chat Area */}
        <div
          className={classNames(
            "flex min-h-0 flex-col overflow-hidden rounded-[24px] border border-[#e8e8ed] bg-white/90 shadow-[0_4px_24px_rgba(0,0,0,0.04)] transition-all duration-300",
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
              <div className="flex items-center justify-between border-b border-[#e8e8ed] px-5 py-4">
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
              <div className="flex min-h-0 flex-1 flex-col">
                <Conversation className="chat-fade-bottom min-h-0 flex-1 px-5 pt-5">
                  <ConversationContent>
                    <AnimatePresence mode="sync" initial={false}>
                      {conversation.chatList.map((chat) => (
                        <motion.div
                          key={chat.requestId}
                          initial={{ opacity: 0, y: 12 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: -8 }}
                          transition={{
                            duration: 0.22,
                            ease: [0.25, 0.46, 0.45, 0.94],
                          }}
                        >
                          <Dialogue
                            chat={chat}
                            deepThink={conversation.deepThink}
                            changeTask={changeTask}
                            changeFile={changeFile}
                            changePlan={changePlan}
                            onRegenerate={handleRegenerate}
                          />
                        </motion.div>
                      ))}
                    </AnimatePresence>
                  </ConversationContent>
                  <ConversationScrollButton />
                </Conversation>

                {/* Input */}
                <div className="sticky bottom-0 z-10 bg-gradient-to-t from-white via-white/95 to-transparent px-4 pb-4 pt-3">
                  <GeneralInput
                    placeholder={loading ? "任务进行中..." : "希望 Genie 为你做哪些任务呢？"}
                    showBtn={false}
                    size="medium"
                    disabled={loading}
                    product={currentProduct}
                    send={(info) =>
                      sendMessage({
                        ...info,
                        outputStyle: conversation.productType,
                        deepThink: conversation.deepThink,
                      })
                    }
                  />
                </div>
              </div>
            </>
          )}
        </div>

        {/* Drag Handle */}
        {!isLeftCollapsed && !isRightCollapsed && (
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
            "flex min-h-0 flex-col overflow-hidden rounded-[24px] border border-[#e8e8ed] bg-white/90 shadow-[0_4px_24px_rgba(0,0,0,0.04)] transition-all duration-300",
            isRightCollapsed && "w-14 min-w-14",
            !isRightCollapsed && "flex-1"
          )}
          style={!isRightCollapsed ? { flex: `0 0 ${100 - leftPanelWidth - (isLeftCollapsed ? 0 : 0)}%` } : undefined}
        >
          {isRightCollapsed ? (
            // 折叠状态
            <div className="flex h-full flex-col items-center py-4">
              <button
                onClick={toggleRightPanel}
                className="flex h-10 w-10 items-center justify-center rounded-full text-[#86868b] transition-colors hover:bg-[#f5f5f7] hover:text-[#1d1d1f]"
                title="展开工作空间"
              >
                <PanelLeftClose className="h-5 w-5" />
              </button>
            </div>
          ) : (
            // 展开状态 - 工作空间
            <ActionView
              activeTask={activeTask}
              taskList={taskList}
              plan={plan}
              ref={actionViewRef}
              onClose={() => {
                changeActionStatus(false);
                setIsRightCollapsed(true);
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
      <div className="mx-auto flex h-full w-full max-w-[1600px] px-4 pt-4 md:px-6">
        <div
          className={classNames(
            "mx-auto flex min-h-0 w-full max-w-[980px] flex-1 flex-col overflow-hidden rounded-[24px] border border-[#e8e8ed] bg-white/80 px-5 pt-4 shadow-[0_4px_24px_rgba(0,0,0,0.04)] md:px-6"
          )}
        >
          {/* Header */}
          <div className="flex items-center justify-between border-b border-[#e8e8ed] pb-5">
            <div className="flex min-w-0 items-center gap-4">
              <h2 className="truncate text-[17px] font-semibold tracking-tight text-[#1d1d1f]">
                {headerTitle}
              </h2>
            </div>
          </div>

          {/* Messages */}
          <Conversation className="chat-fade-bottom mb-2 mt-4 min-h-0 flex-1">
            <ConversationContent>
              <AnimatePresence mode="sync" initial={false}>
                {conversation.dataChatList.map((chat, idx) => (
                  <motion.div
                    key={`${conversation.id}-${idx}`}
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{
                      duration: 0.22,
                      ease: [0.25, 0.46, 0.45, 0.94],
                    }}
                  >
                    <DataDialogue chat={chat} />
                  </motion.div>
                ))}
              </AnimatePresence>
            </ConversationContent>
            <ConversationScrollButton />
          </Conversation>

          {/* Input */}
          <div className="sticky bottom-0 z-10 bg-gradient-to-t from-white via-white/95 to-transparent pb-5 pt-3">
            <GeneralInput
              placeholder={loading ? "任务进行中..." : "希望 Genie 为你做哪些任务呢？"}
              showBtn={false}
              size="medium"
              disabled={loading}
              product={currentProduct}
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
