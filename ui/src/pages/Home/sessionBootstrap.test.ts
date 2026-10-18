import { describe, expect, it, vi } from "vitest";
import type {
  ConversationHistoryPage,
  ConversationReplayFrame,
  ConversationRunReplay,
} from "@/services/agentConversation";
import { buildConversationTaskData, combineData } from "@/utils/chat";

import {
  hydrateSessionWithRunningReplay,
  resolveInitialSession,
  resolveInitialSessionId,
} from "./sessionBootstrap";

function createReplayFrame(eventData: MESSAGE.EventData): ConversationReplayFrame {
  return {
    reqId: "req-running",
    status: "success",
    finished: false,
    resultMap: { eventData },
  };
}

function createRunningPage(): ConversationHistoryPage {
  return {
    sessionId: "session-running",
    title: "执行中的会话",
    status: "RUNNING",
    deepThink: false,
    runCount: 2,
    finishedRunCount: 1,
    failedRunCount: 0,
    runs: [
      {
        requestId: "req-completed",
        status: "SUCCESS",
        queryPreview: "已完成的问题",
        finalSummaryPreview: "已完成的结论",
        hasReplay: true,
      },
      {
        requestId: "req-running",
        status: "RUNNING",
        queryPreview: "正在执行的问题",
        hasReplay: true,
      },
    ],
  };
}

function createPlanEvent(): MESSAGE.EventData {
  return {
    taskId: "task-running",
    taskOrder: 1,
    messageType: "plan",
    messageOrder: 1,
    messageId: "plan-running",
    resultMap: {
      title: "恢复的执行计划",
      stages: ["分析"],
      steps: ["读取资料"],
      stepStatus: ["in_progress"],
      notes: [""],
    } as unknown as MESSAGE.Task,
  };
}

function createToolCallEvent(): MESSAGE.EventData {
  return {
    taskId: "task-running",
    taskOrder: 1,
    messageType: "task",
    messageOrder: 2,
    messageId: "tool-call-running",
    resultMap: {
      requestId: "req-running",
      messageId: "tool-call-running",
      messageType: "tool_call",
      finish: false,
      isFinal: false,
      resultMap: {
        messageType: "tool_call",
        status: "running",
        toolName: "read_file",
        toolCallId: "call-running",
        summary: "读取资料",
        input: { fileName: "brief.md" },
      },
    } as unknown as MESSAGE.Task,
  } as unknown as MESSAGE.EventData;
}

function createResultEvent(): MESSAGE.EventData {
  return {
    taskId: "task-running",
    taskOrder: 1,
    messageType: "task",
    messageOrder: 3,
    messageId: "result-running",
    resultMap: {
      requestId: "req-running",
      messageId: "result-running",
      messageType: "result",
      messageTime: "1714620002000",
      finish: true,
      isFinal: true,
      result: "最终结论",
      taskSummary: "最终结论",
    } as unknown as MESSAGE.Task,
  } as unknown as MESSAGE.EventData;
}

function createReplay(
  status: string,
  replayFrames: ConversationReplayFrame[] = [],
  requestId = "req-running"
): ConversationRunReplay {
  return {
    requestId,
    sessionId: "session-running",
    status,
    queryText: "正在执行的问题",
    replayFrames,
  };
}

describe("sessionBootstrap", () => {
  const sessions = [
    {
      sessionId: "session-running",
      title: "执行中的会话",
      status: "RUNNING",
      latestQueryText: "正在执行的问题",
      runCount: 1,
      finishedRunCount: 0,
      failedRunCount: 0,
      startedAt: "2026-05-08T10:20:00",
      lastActiveAt: "2026-05-08T10:20:00",
    },
    {
      sessionId: "session-002",
      title: "第二个会话",
      status: "SUCCESS",
      latestQueryText: "继续完善方案",
      runCount: 2,
      finishedRunCount: 2,
      failedRunCount: 0,
      startedAt: "2026-05-08T10:00:00",
      lastActiveAt: "2026-05-08T10:10:00",
    },
    {
      sessionId: "session-001",
      title: "第一个会话",
      status: "SUCCESS",
      latestQueryText: "初始问题",
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      startedAt: "2026-05-08T09:00:00",
      lastActiveAt: "2026-05-08T09:10:00",
    },
  ] as CHAT.ConversationSessionItem[];

  it("刷新后只恢复本地记录且仍在执行的会话", () => {
    expect(
      resolveInitialSessionId({
        recentSessions: sessions,
        storedSessionId: "session-running",
      })
    ).toBe("session-running");
  });

  it("已完成的本地会话不自动恢复", () => {
    expect(
      resolveInitialSessionId({
        recentSessions: sessions,
        storedSessionId: "session-001",
      })
    ).toBeNull();
  });

  it("当前 visitor 没有会话时返回空值", () => {
    expect(
      resolveInitialSessionId({
        recentSessions: [],
        storedSessionId: "session-001",
      })
    ).toBeNull();
  });

  it("列表首批未包含的运行中会话通过详情恢复并复用详情结果", async () => {
    const detail = createRunningPage();
    const loadSessionDetail = vi.fn(async () => detail);

    await expect(
      resolveInitialSession({
        recentSessions: sessions.slice(1),
        storedSessionId: "session-running",
        allowRemoteLookup: true,
        loadSessionDetail,
      })
    ).resolves.toEqual({
      sessionId: "session-running",
      detail,
    });
    expect(loadSessionDetail).toHaveBeenCalledOnce();
    expect(loadSessionDetail).toHaveBeenCalledWith("session-running");
  });

  it("刷新未持久化的空会话时不请求不存在的详情", async () => {
    const loadSessionDetail = vi.fn(async () => createRunningPage());

    await expect(
      resolveInitialSession({
        recentSessions: sessions,
        storedSessionId: "session-empty-draft",
        allowRemoteLookup: false,
        loadSessionDetail,
      })
    ).resolves.toEqual({
      sessionId: null,
      detail: null,
    });
    expect(loadSessionDetail).not.toHaveBeenCalled();
  });

  it("列表首批外的已完成会话不自动恢复", async () => {
    const detail = {
      ...createRunningPage(),
      status: "SUCCESS",
    };
    const loadSessionDetail = vi.fn(async () => detail);

    await expect(
      resolveInitialSession({
        recentSessions: sessions.slice(1),
        storedSessionId: "session-running",
        allowRemoteLookup: true,
        loadSessionDetail,
      })
    ).resolves.toEqual({
      sessionId: null,
      detail: null,
    });
    expect(loadSessionDetail).toHaveBeenCalledOnce();
  });

  it("RUNNING run 跳过账本回放（交给 SSE 全量重放），同 turn 已完成 run 仍补齐", async () => {
    const replay = createReplay("SUCCESS", [
      createReplayFrame(createPlanEvent()),
      createReplayFrame(createToolCallEvent()),
    ], "req-completed");
    const loadReplay = vi.fn(async () => replay);

    const conversation = await hydrateSessionWithRunningReplay(
      createRunningPage(),
      loadReplay
    );

    expect(loadReplay).not.toHaveBeenCalled();
    expect(conversation.chatList.map((chat) => chat.requestId)).toEqual([
      "req-completed",
      "req-running",
    ]);
    expect(conversation.chatList[0].conclusion?.result).toBe("已完成的结论");
    // RUNNING run 保持空壳：账本回放拿不到进行中的流式正文，
    // 内容由 follow SSE 全量重放重建（acceptReplayedHistory）。
    expect(conversation.chatList[1].loading).toBe(true);
    expect(conversation.chatList[1].metrics?.status).toBe("RUNNING");
    expect(conversation.chatList[1].replayLoaded).toBe(false);
    expect(conversation.chatList[1].multiAgent.plan).toBeUndefined();
    expect(conversation.chatList[1].conclusion).toBeUndefined();
  });

  it("replay 与 follow SSE 收到同一工具事件时保持单张卡片", async () => {
    const toolCall = createToolCallEvent();
    const page = createRunningPage();
    page.runCount = 1;
    page.runs = [{
      ...page.runs[1],
      status: "SUCCESS",
      finalSummaryPreview: ""
    }];
    const conversation = await hydrateSessionWithRunningReplay(
      page,
      async () => createReplay("SUCCESS", [createReplayFrame(toolCall)])
    );
    const chat = conversation.chatList[0];
    const afterFollow = buildConversationTaskData(
      combineData(toolCall, chat),
      conversation.deepThink
    ).currentChat;

    expect(
      afterFollow.multiAgent.tasks.flat().filter(
        (task) => task.messageType === "tool_call"
      )
    ).toHaveLength(1);
  });

  it("replay 与 follow SSE 收到同一最终事件时不重复结论", async () => {
    const replayResult = createResultEvent();
    const followResult = createResultEvent();
    const page = createRunningPage();
    page.runCount = 1;
    page.finishedRunCount = 0;
    // 只有非 RUNNING run 才走账本回放；这里模拟 run 已结束、summary 已落终态的场景。
    page.runs = [{
      ...page.runs[1],
      status: "SUCCESS",
      finalSummaryPreview: ""
    }];
    const conversation = await hydrateSessionWithRunningReplay(
      page,
      async () => createReplay("SUCCESS", [createReplayFrame(replayResult)])
    );
    const chat = conversation.chatList[0];
    expect(
      chat.multiAgent.tasks.flat().filter((task) => task.messageType === "result")
    ).toHaveLength(1);
    const afterFollow = buildConversationTaskData(
      combineData(followResult, chat),
      conversation.deepThink
    ).currentChat;

    expect(afterFollow.conclusion?.result).toBe("最终结论");
    expect(
      afterFollow.multiAgent.tasks.flat().filter(
        (task) => task.messageType === "result"
      )
    ).toHaveLength(1);
  });

  it("已完成 run 的回放返回终态时不再保留 loading", async () => {
    const page = createRunningPage();
    page.runCount = 1;
    page.runs = [{
      ...page.runs[1],
      status: "SUCCESS",
      finalSummaryPreview: ""
    }];
    const conversation = await hydrateSessionWithRunningReplay(
      page,
      async () => ({
        ...createReplay("SUCCESS"),
        finalSummaryText: "已完成",
      })
    );

    expect(conversation.chatList[0].loading).toBe(false);
    expect(conversation.chatList[0].metrics?.status).toBe("SUCCESS");
    expect(conversation.chatList[0].conclusion?.result).toBe("已完成");
  });

  it("replay 请求失败时保留 shell 并报告 requestId", async () => {
    const error = new Error("replay unavailable");
    const onReplayError = vi.fn();
    const conversation = await hydrateSessionWithRunningReplay(
      createRunningPage(),
      async () => {
        throw error;
      },
      onReplayError
    );

    // RUNNING run 被跳过，不发出任何回放请求。
    expect(onReplayError).not.toHaveBeenCalled();
    expect(conversation.chatList[0].replayLoaded).toBe(false);
    expect(conversation.chatList[1].loading).toBe(true);
    expect(conversation.chatList[1].metrics?.status).toBe("RUNNING");
    expect(conversation.chatList[1].replayLoaded).toBe(false);
  });

  it("没有 RUNNING run 时补最后一个 turn：提问 run 与续跑 run 一起补齐", async () => {
    const page = createRunningPage();
    page.runCount = 3;
    page.runs = [
      {
        requestId: "req-old",
        status: "SUCCESS",
        queryPreview: "更早的问题",
        hasReplay: true,
      },
      {
        requestId: "req-ask",
        status: "WAITING_INPUT",
        queryPreview: "写文件再提问",
        hasReplay: true,
      },
      {
        requestId: "resume_1",
        status: "SUCCESS",
        queryPreview: "",
        hasReplay: true,
      },
    ];
    const requested: string[] = [];

    const conversation = await hydrateSessionWithRunningReplay(
      page,
      async (requestId) => {
        requested.push(requestId);
        return createReplay("SUCCESS", [], requestId);
      }
    );

    // 合并后的 Dialogue 只认最后一个 run 的 requestId，所以 turn 内两个 run 都要补 ——
    // 漏掉 req-ask 就会让 AskUserQuestion 之前那个 run 的工具卡整段消失。
    expect(requested).toEqual(["req-ask", "resume_1"]);
    expect(conversation.chatList.map((chat) => chat.replayLoaded)).toEqual([
      false,
      true,
      true,
    ]);
  });
});
