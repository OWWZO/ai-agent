import { describe, expect, it } from "vitest";
import { mergePendingAskUserQuestions } from "./askUserRestore";

function emptyChat(requestId: string): CHAT.ChatItem {
  return {
    sessionId: "s1",
    requestId,
    query: "q",
    files: [],
    forceStop: false,
    loading: false,
    tip: "",
    multiAgent: { tasks: [] },
    tasks: [],
    metrics: { status: "WAITING_INPUT" },
  } as CHAT.ChatItem;
}

describe("mergePendingAskUserQuestions", () => {
  it("injects pending ask card into waiting chat", () => {
    const conversation = {
      id: "c1",
      sessionId: "s1",
      title: "t",
      productType: "html",
      deepThink: true,
      role: null,
      createdAt: 1,
      updatedAt: 1,
      chatList: [emptyChat("r1")],
      dataChatList: [],
    } as CHAT.ConversationHistory;

    const { conversation: next, autoResumes } = mergePendingAskUserQuestions(conversation, [
      {
        messageType: "ask_user_question",
        questionId: "uq_1",
        sessionId: "s1",
        requestId: "r1",
        status: "pending",
        questions: [{ question: "选哪个？", header: "方向", options: [{ label: "A" }] }],
      },
    ]);

    expect(autoResumes).toHaveLength(0);
    const chat = next.chatList[0];
    expect(chat.metrics?.status).toBe("WAITING_INPUT");
    expect(chat.tip).toBe("需要你的帮助");
    const flat = (chat.multiAgent?.tasks || []).flat();
    expect(flat.some((t) => t.messageType === "ask_user_question")).toBe(true);
    const card = flat.find((t) => t.messageType === "ask_user_question");
    const nested = (card?.resultMap as any)?.resultMap || card?.resultMap || card;
    expect(nested?.questionId || (card as any)?.questionId).toBeTruthy();
    expect(Array.isArray(nested?.questions)).toBe(true);
    expect(nested?.questions?.[0]?.question).toBe("选哪个？");
    // 界面时间线读 chat.tasks（容器.children），注入后必须重建派生层
    const rendered = (chat.tasks || [])
      .flat()
      .flatMap((item) => [
        item,
        ...(((item as CHAT.Task).children || []) as CHAT.Task[]),
      ]);
    expect(rendered.some((t) => t.messageType === "ask_user_question")).toBe(true);
  });

  it("queues auto resume for answered pending", () => {
    const conversation = {
      id: "c1",
      sessionId: "s1",
      title: "t",
      productType: "html",
      deepThink: true,
      role: null,
      createdAt: 1,
      updatedAt: 1,
      chatList: [emptyChat("r1")],
      dataChatList: [],
    } as CHAT.ConversationHistory;

    const { autoResumes } = mergePendingAskUserQuestions(conversation, [
      {
        questionId: "uq_2",
        sessionId: "s1",
        requestId: "r1",
        status: "answered",
        resumeRequestId: "resume_abc",
        questions: [{ question: "选哪个？", header: "方向", options: [{ label: "A" }] }],
      },
    ]);

    expect(autoResumes).toEqual([
      {
        resumeRequestId: "resume_abc",
        sessionId: "s1",
        questionId: "uq_2",
      },
    ]);
  });
});
