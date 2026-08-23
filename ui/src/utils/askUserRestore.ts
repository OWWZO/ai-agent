import {
  askUserApi,
  dispatchAskUserResume,
  type AskUserResumeEventDetail,
} from "@/services/askUser";
import { buildConversationTaskData, combineData } from "@/utils/chat";
import { applyWaitingUserInputState } from "@/components/ChatView/streamState";

function chatHasQuestion(
  chat: CHAT.ChatItem,
  questionId: string,
  toolCallId: string
): boolean {
  if (!questionId && !toolCallId) {
    return false;
  }
  const groups = chat.multiAgent?.tasks || chat.tasks || [];
  for (const group of groups) {
    for (const tool of group || []) {
      if (tool?.messageType !== "ask_user_question") {
        continue;
      }
      const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
      const nested = (resultMap.resultMap || resultMap) as Record<string, unknown>;
      const cardQuestionId = String(
        nested.questionId || resultMap.questionId || ""
      );
      const cardToolCallId = String(
        nested.toolCallId || resultMap.toolCallId || ""
      );
      if (
        (questionId && cardQuestionId === questionId) ||
        (toolCallId && cardToolCallId === toolCallId)
      ) {
        return true;
      }
    }
  }
  return false;
}

function buildAskUserEventData(payload: Record<string, unknown>): MESSAGE.EventData {
  const questionId = String(payload.questionId || "");
  const status = String(payload.status || "pending");
  // 对齐 live SSE：外层 messageType + 内层 resultMap 承载 questions，
  // 避免 AskUserQuestionCard 读 tool.resultMap.questions 时拿不到题干。
  const inner = {
    messageType: "ask_user_question",
    messageId: questionId,
    questionId,
    sessionId: payload.sessionId,
    requestId: payload.requestId,
    toolCallId: payload.toolCallId,
    status,
    questions: payload.questions,
    answers: payload.answers,
    resumeRequestId: payload.resumeRequestId,
    timeoutMs: payload.timeoutMs,
    createdAtMs: payload.createdAtMs,
    isFinal: status !== "pending",
    finish: status !== "pending",
  };
  return {
    taskId: String(payload.requestId || questionId || `ask-${Date.now()}`),
    taskOrder: 1,
    messageType: "task",
    messageOrder: 1,
    messageId: questionId,
    resultMap: {
      messageType: "ask_user_question",
      messageId: questionId,
      isFinal: status !== "pending",
      finish: status !== "pending",
      resultMap: inner,
    } as unknown as MESSAGE.Task,
  };
}

/**
 * 将会话未决 AskUserQuestion 注入历史聊天快照，刷新后仍可作答。
 * 对已回答但未续跑（answered + resumeRequestId）的记录自动派发 resume。
 */
export function mergePendingAskUserQuestions(
  conversation: CHAT.ConversationHistory,
  pending: Record<string, unknown>[]
): { conversation: CHAT.ConversationHistory; autoResumes: AskUserResumeEventDetail[] } {
  if (!conversation?.chatList?.length || !pending?.length) {
    return { conversation, autoResumes: [] };
  }

  const chatList = conversation.chatList.map((chat) => ({
    ...chat,
    multiAgent: {
      ...(chat.multiAgent || { tasks: [] }),
      tasks: [...(chat.multiAgent?.tasks || [])].map((group) => [...(group || [])]),
    },
  }));
  const autoResumes: AskUserResumeEventDetail[] = [];

  for (const payload of pending) {
    const questionId = String(payload.questionId || "");
    const toolCallId = String(payload.toolCallId || "");
    const requestId = String(payload.requestId || "");
    const clientStatus = String(payload.status || "pending").toLowerCase();
    const persistenceStatus = String(payload.persistenceStatus || "").toUpperCase();
    let index = requestId
      ? chatList.findIndex((chat) => chat.requestId === requestId)
      : -1;
    if (index < 0) {
      index = chatList.findIndex(
        (chat) => String(chat.metrics?.status || "").toUpperCase() === "WAITING_INPUT"
      );
    }
    if (index < 0) {
      index = chatList.length - 1;
    }
    if (index < 0) {
      continue;
    }

    let chat = chatList[index];
    if (!chatHasQuestion(chat, questionId, toolCallId)) {
      combineData(buildAskUserEventData(payload), chat);
    } else {
      // 已有卡片时同步 pending 状态，避免刷新后仍显示可点但服务端已结束
      const groups = chat.multiAgent?.tasks || [];
      for (const group of groups) {
        for (const tool of group || []) {
          if (tool?.messageType !== "ask_user_question") {
            continue;
          }
          const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
          const nested = (resultMap.resultMap || resultMap) as Record<
            string,
            unknown
          >;
          const cardQuestionId = String(
            nested.questionId || resultMap.questionId || ""
          );
          const cardToolCallId = String(
            nested.toolCallId || resultMap.toolCallId || ""
          );
          if (
            !(
              (questionId && cardQuestionId === questionId) ||
              (toolCallId && cardToolCallId === toolCallId)
            )
          ) {
            continue;
          }
          nested.status = clientStatus;
          if (payload.answers && typeof payload.answers === "object") {
            const answers = payload.answers as Record<string, string | true>;
            nested.answers = answers;
            resultMap.answers = answers;
          }
          resultMap.status = clientStatus;
          resultMap.isFinal = clientStatus !== "pending";
          tool.finish = clientStatus !== "pending";
          tool.isFinal = clientStatus !== "pending";
        }
      }
    }

    if (clientStatus === "pending") {
      chat = applyWaitingUserInputState(chat);
    } else if (clientStatus === "answered") {
      const resumePending =
        !persistenceStatus ||
        persistenceStatus === "RESUME_PENDING" ||
        persistenceStatus === "RESUMING";
      chat = {
        ...chat,
        loading: resumePending,
        tip: resumePending ? "正在推进任务…" : "",
        metrics: {
          ...(chat.metrics || {}),
          status: resumePending ? "RUNNING" : "SUCCESS",
        },
      };
      const resumeRequestId = String(payload.resumeRequestId || "");
      if (resumeRequestId && resumePending) {
        autoResumes.push({
          resumeRequestId,
          sessionId: String(payload.sessionId || conversation.sessionId || ""),
          questionId,
          answers:
            payload.answers && typeof payload.answers === "object"
              ? (payload.answers as Record<string, string>)
              : undefined,
        });
      }
    }

    // Dialogue 渲染 chat.tasks，必须像历史 hydrate 一样从 multiAgent 重建派生层
    const derived = buildConversationTaskData(chat, conversation.deepThink);
    chatList[index] = {
      ...derived.currentChat,
      tip: chat.tip,
      loading: chat.loading,
      metrics: chat.metrics,
    };
  }

  return {
    conversation: {
      ...conversation,
      chatList,
    },
    autoResumes,
  };
}

export async function restoreAskUserQuestionsForSession(
  conversation: CHAT.ConversationHistory
): Promise<CHAT.ConversationHistory> {
  const sessionId = conversation?.sessionId;
  if (!sessionId) {
    return conversation;
  }
  try {
    const pending = await askUserApi.pending(sessionId);
    const { conversation: next, autoResumes } = mergePendingAskUserQuestions(
      conversation,
      Array.isArray(pending) ? pending : []
    );
    // 下一帧再派发，确保 ChatView 已挂上 resume 事件监听
    if (autoResumes.length) {
      window.setTimeout(() => {
        for (const item of autoResumes) {
          dispatchAskUserResume(item);
        }
      }, 0);
    }
    return next;
  } catch (error) {
    console.warn("恢复 AskUserQuestion pending 失败", error);
    return conversation;
  }
}
