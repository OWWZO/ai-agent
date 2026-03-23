import { useEffect, useMemo, useRef, useState } from "react";
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
import Logo from "../Logo";
import { Modal } from "antd";
import {
  Conversation,
  ConversationContent,
  ConversationScrollButton,
} from "@/components/ai-elements/conversation";

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
      tip: "已接收到你的任务，将立刻开始处理...",
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

        requestAnimationFrame(() => {
          if (resultMap?.eventData) {
            currentChat = combineData(resultMap.eventData || {}, currentChat);
            const taskData = handleTaskData(
              currentChat,
              normalizedDeepThink,
              currentChat.multiAgent
            );
            setTaskList(taskData.taskList);
            temporaryChangeTask(taskData.taskList);
            updatePlan(taskData.plan!);
            openAction(taskData.taskList);
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
            commitConversation(conversationId, runningConversation);
          }
        });
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

  const renderMultAgent = () => {
    return (
      <div className="h-full w-full flex items-stretch gap-4 px-4 md:px-6 pb-4">
        <div
          className={classNames("min-w-0 flex flex-col flex-1 rounded-[14px] bg-transparent", {
            "max-w-[980px] mx-auto": !showAction,
            "basis-[58%]": showAction,
          })}
          id="chat-view"
        >
          <div className="px-3 md:px-5 pt-6 md:pt-8">
            <div className="w-full flex justify-between">
              <div className="w-full flex items-center pb-6">
                <Logo />
                <div className="overflow-hidden whitespace-nowrap text-ellipsis text-[16px] font-[500] text-[#27272A] mr-8">
                  {headerTitle}
                </div>
                {conversation.deepThink && (
                  <div className="rounded-[4px] px-6 border-1 border-solid border-gray-300 flex items-center shrink-0">
                    <i className="font_family icon-shendusikao mr-6 text-[12px]"></i>
                    <span className="ml-[-4px]">深度研究</span>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="px-3 md:px-5 min-h-0 flex-1 flex flex-col">
            <Conversation className="flex-1 mb-[20px]">
              <ConversationContent>
                {conversation.chatList.map((chat) => (
                  <div key={chat.requestId}>
                    <Dialogue
                      chat={chat}
                      deepThink={conversation.deepThink}
                      changeTask={changeTask}
                      changeFile={changeFile}
                      changePlan={changePlan}
                      onRegenerate={handleRegenerate}
                    />
                  </div>
                ))}
              </ConversationContent>
              <ConversationScrollButton />
            </Conversation>
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
        {contextHolder}
        <div
          className={classNames("transition-all min-w-0", {
            "opacity-0 overflow-hidden": !showAction,
            "w-0": !showAction,
            "w-[460px] min-w-[400px] max-w-[560px]": showAction,
          })}
        >
          <ActionView
            activeTask={activeTask}
            taskList={taskList}
            plan={plan}
            ref={actionViewRef}
            onClose={() => changeActionStatus(false)}
          />
        </div>
      </div>
    );
  };

  const renderDataAgent = () => {
    return (
      <div
        className={classNames(
          "flex flex-col flex-1 w-0 max-w-[980px] mx-auto px-4 md:px-6 pt-6 md:pt-8 pb-4"
        )}
      >
        <div className="w-full flex justify-between">
          <div className="w-full flex items-center pb-6">
            <Logo />
            <div className="overflow-hidden whitespace-nowrap text-ellipsis text-[16px] font-[500] text-[#27272A] mr-8">
              {headerTitle}
            </div>
          </div>
        </div>
        <Conversation className="flex-1 mb-[20px]">
          <ConversationContent>
            {conversation.dataChatList.map((chat, index) => (
              <div key={`${conversation.id}-${index}`}>
                <DataDialogue chat={chat} />
              </div>
            ))}
          </ConversationContent>
          <ConversationScrollButton />
        </Conversation>
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
    );
  };

  const isDataConversation =
    conversation.productType === "dataAgent" && !conversation.deepThink;

  return (
    <div className="h-full w-full flex justify-center">
      {isDataConversation ? renderDataAgent() : renderMultAgent()}
    </div>
  );
};

export default ChatView;
