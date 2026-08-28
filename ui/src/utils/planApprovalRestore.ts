import {
  dispatchPlanApprovalResume,
  planApprovalApi,
  type PlanApprovalResumeEventDetail,
} from "@/services/planApproval";
import { buildConversationTaskData, combineData } from "@/utils/chat";
import { applyWaitingUserInputState } from "@/components/ChatView/streamState";

function chatHasApproval(chat: CHAT.ChatItem, approvalId: string): boolean {
  if (!approvalId) {
    return false;
  }
  const groups = chat.multiAgent?.tasks || chat.tasks || [];
  for (const group of groups) {
    for (const tool of group || []) {
      if (tool?.messageType !== "plan_approval") {
        continue;
      }
      const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
      const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
      const toolAny = tool as unknown as Record<string, unknown>;
      const id = String(
        nested.approvalId ||
          resultMap.approvalId ||
          toolAny.approvalId ||
          tool.messageId ||
          ""
      );
      if (id === approvalId) {
        return true;
      }
    }
  }
  return false;
}

function buildPlanApprovalEventData(payload: Record<string, unknown>): MESSAGE.EventData {
  const approvalId = String(payload.approvalId || "");
  const status = String(payload.status || "pending");
  const inner = {
    messageType: "plan_approval",
    messageId: approvalId,
    approvalId,
    sessionId: payload.sessionId,
    requestId: payload.requestId,
    toolCallId: payload.toolCallId,
    status,
    persistenceStatus: payload.persistenceStatus,
    approved: payload.approved,
    feedback: payload.feedback,
    editedPlanContent: payload.editedPlanContent,
    planContent: payload.planContent,
    planFilePath: payload.planFilePath,
    resumeRequestId: payload.resumeRequestId,
    expiresAt: payload.expiresAt,
    isFinal: status !== "pending",
    finish: status !== "pending",
  };
  return {
    taskId: String(payload.requestId || approvalId || `plan-${Date.now()}`),
    taskOrder: 1,
    messageType: "task",
    messageOrder: 1,
    messageId: approvalId,
    resultMap: {
      messageType: "plan_approval",
      messageId: approvalId,
      isFinal: status !== "pending",
      finish: status !== "pending",
      resultMap: inner,
    } as unknown as MESSAGE.Task,
  };
}

export function mergePendingPlanApprovals(
  conversation: CHAT.ConversationHistory,
  pending: Record<string, unknown>[]
): { conversation: CHAT.ConversationHistory; autoResumes: PlanApprovalResumeEventDetail[] } {
  if (!conversation?.chatList?.length || !pending?.length) {
    return {
      conversation,
      autoResumes: [],
    };
  }

  const chatList = conversation.chatList.map((chat) => ({
    ...chat,
    multiAgent: {
      ...(chat.multiAgent || { tasks: [] }),
      tasks: [...(chat.multiAgent?.tasks || [])].map((group) => [...(group || [])]),
    },
  }));
  const autoResumes: PlanApprovalResumeEventDetail[] = [];

  for (const payload of pending) {
    const approvalId = String(payload.approvalId || "");
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
    if (!chatHasApproval(chat, approvalId)) {
      combineData(buildPlanApprovalEventData(payload), chat);
    } else {
      const groups = chat.multiAgent?.tasks || [];
      for (const group of groups) {
        for (const tool of group || []) {
          if (tool?.messageType !== "plan_approval") {
            continue;
          }
          const resultMap = tool.resultMap as unknown as Record<string, unknown>;
          const nested = (resultMap.resultMap || resultMap) as Record<
            string,
            unknown
          >;
          const toolAny = tool as unknown as Record<string, unknown>;
          const cardApprovalId = String(
            nested.approvalId ||
              resultMap.approvalId ||
              toolAny.approvalId ||
              tool.messageId ||
              ""
          );
          if (cardApprovalId !== approvalId) {
            continue;
          }
          nested.status = clientStatus;
          nested.persistenceStatus = persistenceStatus || undefined;
          if (typeof payload.approved === "boolean") {
            nested.approved = payload.approved;
          }
          if (typeof payload.editedPlanContent === "string") {
            nested.editedPlanContent = payload.editedPlanContent;
            if (payload.editedPlanContent.trim()) {
              nested.planContent = payload.editedPlanContent;
            }
          }
          resultMap.status = clientStatus;
          resultMap.persistenceStatus = persistenceStatus || undefined;
          if (typeof payload.approved === "boolean") {
            resultMap.approved = payload.approved;
          }
          if (typeof payload.editedPlanContent === "string") {
            resultMap.editedPlanContent = payload.editedPlanContent;
            if (payload.editedPlanContent.trim()) {
              resultMap.planContent = payload.editedPlanContent;
            }
          }
          resultMap.isFinal = clientStatus !== "pending";
          tool.finish = clientStatus !== "pending";
          tool.isFinal = clientStatus !== "pending";
        }
      }
    }

    if (clientStatus === "pending") {
      chat = applyWaitingUserInputState(chat);
    } else if (clientStatus === "decided") {
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
          approvalId,
          ...(typeof payload.approved === "boolean"
            ? { approved: payload.approved }
            : {}),
        });
      }
    } else if (clientStatus === "cancelled" || clientStatus === "timeout") {
      chat = {
        ...chat,
        loading: false,
        tip: "",
        metrics: {
          ...(chat.metrics || {}),
          status: "FAILED",
        },
      };
    }

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

export async function restorePlanApprovalsForSession(
  conversation: CHAT.ConversationHistory
): Promise<CHAT.ConversationHistory> {
  const sessionId = conversation?.sessionId;
  if (!sessionId) {
    return conversation;
  }
  try {
    const pending = await planApprovalApi.pending(sessionId);
    const { conversation: next, autoResumes } = mergePendingPlanApprovals(
      conversation,
      Array.isArray(pending) ? pending : []
    );
    if (autoResumes.length) {
      window.setTimeout(() => {
        for (const item of autoResumes) {
          dispatchPlanApprovalResume(item);
        }
      }, 0);
    }
    return next;
  } catch (error) {
    console.warn("恢复 PlanApproval pending 失败", error);
    return conversation;
  }
}
