import { describe, expect, it } from "vitest";
import {
  findLatestPendingAskUser,
  findLatestPendingPlanApproval,
  resolveHitlDockSlot,
} from "./hitlDockModel";

function askTask(status: string, id = "ask-1"): CHAT.Task {
  return {
    id,
    messageId: id,
    messageType: "ask_user_question",
    messageTime: "1",
    requestId: "r1",
    finish: false,
    isFinal: false,
    resultMap: {
      messageType: "ask_user_question",
      status,
      questions: [{ question: "选一个？", options: ["A", "B"] }],
    },
  } as unknown as CHAT.Task;
}

function approvalTask(status: string, id = "appr-1"): CHAT.Task {
  return {
    id,
    messageId: id,
    messageType: "plan_approval",
    messageTime: "2",
    requestId: "r1",
    finish: false,
    isFinal: false,
    resultMap: {
      messageType: "plan_approval",
      status,
      approvalId: id,
      planContent: "# Plan\n\n1. do x",
    },
  } as unknown as CHAT.Task;
}

describe("hitlDockModel", () => {
  it("prefers pending ask over pending approval", () => {
    const chat = {
      multiAgent: {
        tasks: [[askTask("pending"), approvalTask("pending")]],
      },
      tasks: [],
    } as unknown as CHAT.ChatItem;

    expect(resolveHitlDockSlot(chat)).toBe("ask");
    expect(findLatestPendingAskUser(chat)?.messageId).toBe("ask-1");
  });

  it("falls back to approval when ask is answered", () => {
    const chat = {
      multiAgent: {
        tasks: [[askTask("answered"), approvalTask("pending")]],
      },
      tasks: [],
    } as unknown as CHAT.ChatItem;

    expect(resolveHitlDockSlot(chat)).toBe("approval");
    expect(findLatestPendingPlanApproval(chat)?.messageId).toBe("appr-1");
  });

  it("returns composer when nothing pending", () => {
    const chat = {
      multiAgent: {
        tasks: [[askTask("answered"), approvalTask("approved")]],
      },
      tasks: [],
    } as unknown as CHAT.ChatItem;

    expect(resolveHitlDockSlot(chat)).toBe("composer");
    expect(findLatestPendingAskUser(chat)).toBeUndefined();
    expect(findLatestPendingPlanApproval(chat)).toBeUndefined();
  });

  it("treats the restored decided state as non-interactive", () => {
    const chat = {
      multiAgent: {
        tasks: [[approvalTask("decided")]],
      },
      tasks: [],
    } as unknown as CHAT.ChatItem;

    expect(resolveHitlDockSlot(chat)).toBe("composer");
    expect(findLatestPendingPlanApproval(chat)).toBeUndefined();
  });

  it("prefers a decided chat fact over a stale pending task projection", () => {
    const chat = {
      multiAgent: {
        tasks: [[approvalTask("decided")]],
      },
      tasks: [],
    } as unknown as CHAT.ChatItem;

    expect(
      resolveHitlDockSlot(chat, [approvalTask("pending")])
    ).toBe("composer");
  });

  it("does not treat finish-without-status approval as pending", () => {
    const settled = {
      ...approvalTask(""),
      finish: true,
      isFinal: true,
      resultMap: {
        messageType: "plan_approval",
        approvalId: "appr-1",
        planContent: "# Plan\n\n1. do x",
        isFinal: true,
      },
    } as unknown as CHAT.Task;
    const chat = {
      multiAgent: {
        tasks: [[settled]],
      },
      tasks: [],
    } as unknown as CHAT.ChatItem;

    expect(resolveHitlDockSlot(chat)).toBe("composer");
    expect(findLatestPendingPlanApproval(chat)).toBeUndefined();
  });

  it("reads from taskList when multiAgent empty", () => {
    const taskList = [approvalTask("pending", "appr-2")];
    expect(resolveHitlDockSlot(undefined, taskList)).toBe("approval");
  });
});
