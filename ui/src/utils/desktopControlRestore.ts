import {
  desktopControlApi,
  dispatchDesktopControlResume,
  type DesktopControlResumeEventDetail,
} from "@/services/desktopControl";
import { combineData } from "@/utils/chat";
import { applyWaitingUserInputState } from "@/components/ChatView/streamState";

function chatHasControl(chat: CHAT.ChatItem, controlId: string, toolCallId: string): boolean {
  if (!controlId && !toolCallId) {
    return false;
  }
  const groups = chat.multiAgent?.tasks || chat.tasks || [];
  for (const group of groups) {
    for (const tool of group || []) {
      if (tool?.messageType !== "desktop_control") {
        continue;
      }
      const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
      const nested = (resultMap.resultMap || resultMap) as Record<string, unknown>;
      const cardControlId = String(nested.controlId || resultMap.controlId || "");
      const cardToolCallId = String(nested.toolCallId || resultMap.toolCallId || "");
      if (
        (controlId && cardControlId === controlId) ||
        (toolCallId && cardToolCallId === toolCallId)
      ) {
        return true;
      }
    }
  }
  return false;
}

function buildDesktopEventData(payload: Record<string, unknown>): MESSAGE.EventData {
  const controlId = String(payload.controlId || "");
  const status = String(payload.status || "pending");
  const inner = {
    messageType: "desktop_control",
    messageId: controlId,
    controlId,
    sessionId: payload.sessionId,
    requestId: payload.requestId,
    toolCallId: payload.toolCallId,
    status,
    reason: payload.reason,
    streamUrl: payload.streamUrl,
    holdUntil: payload.holdUntil,
    resumeRequestId: payload.resumeRequestId,
    isFinal: status !== "pending",
    finish: status !== "pending",
  };
  return {
    taskId: String(payload.requestId || controlId || `dc-${Date.now()}`),
    taskOrder: 1,
    messageType: "task",
    messageOrder: 1,
    messageId: controlId,
    resultMap: {
      messageType: "desktop_control",
      messageId: controlId,
      isFinal: status !== "pending",
      finish: status !== "pending",
      resultMap: inner,
    } as unknown as MESSAGE.Task,
  };
}

export function mergePendingDesktopControls(
  conversation: CHAT.ConversationHistory,
  pending: Record<string, unknown>[]
): { conversation: CHAT.ConversationHistory; autoResumes: DesktopControlResumeEventDetail[] } {
  if (!conversation?.chatList?.length || !pending?.length) {
    return {
      conversation,
      autoResumes: [],
    };
  }
  const autoResumes: DesktopControlResumeEventDetail[] = [];
  let nextList = conversation.chatList;
  for (const payload of pending) {
    const controlId = String(payload.controlId || "");
    const toolCallId = String(payload.toolCallId || "");
    const status = String(payload.status || "pending");
    const resumeRequestId = String(payload.resumeRequestId || "");
    const target = nextList[nextList.length - 1];
    if (!target) {
      continue;
    }
    if (!chatHasControl(target, controlId, toolCallId)) {
      const chat = { ...target };
      combineData(buildDesktopEventData(payload), chat);
      if (status === "pending") {
        applyWaitingUserInputState(chat);
      }
      nextList = [...nextList.slice(0, -1), chat];
    }
    if (status !== "pending" && resumeRequestId) {
      autoResumes.push({
        resumeRequestId,
        sessionId: String(payload.sessionId || conversation.sessionId || ""),
        controlId,
      });
    }
  }
  return {
    conversation: {
      ...conversation,
      chatList: nextList,
    },
    autoResumes,
  };
}

export async function restoreDesktopControlsForSession(
  conversation: CHAT.ConversationHistory
): Promise<CHAT.ConversationHistory> {
  const sessionId = conversation.sessionId;
  if (!sessionId) {
    return conversation;
  }
  try {
    const pending = await desktopControlApi.pending(sessionId);
    const { conversation: next, autoResumes } = mergePendingDesktopControls(
      conversation,
      Array.isArray(pending) ? pending : []
    );
    for (const item of autoResumes) {
      dispatchDesktopControlResume(item);
    }
    return next;
  } catch (error) {
    console.warn("恢复 DesktopControl pending 失败", error);
    return conversation;
  }
}
