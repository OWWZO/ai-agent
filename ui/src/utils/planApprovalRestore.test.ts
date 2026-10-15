import { describe, expect, it } from "vitest";
import { mergePendingPlanApprovals } from "./planApprovalRestore";

function approvalTask(status = "pending"): CHAT.Task {
  return {
    id: "approval-task",
    messageId: "approval-1",
    messageType: "plan_approval",
    messageTime: "1",
    requestId: "request-1",
    finish: status !== "pending",
    isFinal: status !== "pending",
    resultMap: {
      messageType: "plan_approval",
      approvalId: "approval-1",
      planContent: "# 已批准计划",
      status,
    },
  } as unknown as CHAT.Task;
}

function conversation(): CHAT.ConversationHistory {
  return {
    id: "conversation-1",
    sessionId: "session-1",
    title: "计划任务",
    productType: "docs",
    deepThink: false,
    createdAt: 1,
    updatedAt: 1,
    chatTitle: "计划任务",
    dataChatList: [],
    chatList: [
      {
        sessionId: "session-1",
        requestId: "request-1",
        query: "执行任务",
        files: [],
        forceStop: false,
        loading: false,
        tasks: [],
        multiAgent: { tasks: [[approvalTask()]] },
        metrics: { status: "WAITING_INPUT" },
      } as unknown as CHAT.ChatItem,
    ],
  };
}

describe("planApprovalRestore", () => {
  it("restores ANSWERED as read-only without dispatching resume", () => {
    const result = mergePendingPlanApprovals(conversation(), [
      {
        approvalId: "approval-1",
        requestId: "request-1",
        status: "decided",
        persistenceStatus: "ANSWERED",
        resumeRequestId: "resume-finished",
      },
    ]);
    const chat = result.conversation.chatList[0];
    const task = chat.multiAgent.tasks[0][0];

    expect(result.autoResumes).toHaveLength(0);
    expect(chat.metrics?.status).toBe("SUCCESS");
    expect(task.resultMap?.status).toBe("decided");
    expect(task.isFinal).toBe(true);
  });

  it("dispatches resume only while the approval continuation is pending", () => {
    const result = mergePendingPlanApprovals(conversation(), [
      {
        approvalId: "approval-1",
        requestId: "request-1",
        status: "decided",
        persistenceStatus: "RESUME_PENDING",
        resumeRequestId: "resume-pending",
      },
    ]);

    expect(result.autoResumes).toEqual([
      {
        approvalId: "approval-1",
        resumeRequestId: "resume-pending",
        sessionId: "session-1",
      },
    ]);
    expect(result.conversation.chatList[0].metrics?.status).toBe("RUNNING");
  });

  it("does not dispatch resume while the continuation is already resuming", () => {
    const base = conversation();
    base.chatList.push({
      sessionId: "session-1",
      requestId: "resume-running",
      query: "执行任务",
      files: [],
      forceStop: false,
      loading: false,
      tasks: [],
      multiAgent: { tasks: [] },
      metrics: { status: "RUNNING" },
    } as unknown as CHAT.ChatItem);

    const result = mergePendingPlanApprovals(base, [
      {
        approvalId: "approval-1",
        requestId: "request-1",
        status: "decided",
        persistenceStatus: "RESUMING",
        resumeRequestId: "resume-running",
      },
    ]);

    expect(result.autoResumes).toHaveLength(0);
    // 源轮收口，避免续跑结束后它成为唯一可 follow 的轮
    expect(result.conversation.chatList[0].metrics?.status).toBe("SUCCESS");
    expect(result.conversation.chatList[0].loading).toBe(false);
    // 运行中标记落在续跑轮
    expect(result.conversation.chatList[1].metrics?.status).toBe("RUNNING");
    expect(result.conversation.chatList[1].loading).toBe(true);
  });
});
