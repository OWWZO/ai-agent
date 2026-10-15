import { describe, expect, it } from "vitest";
import {
  buildChildSessionChat,
  isTerminalRunStatus,
  pickChildSessionRun,
  readChildSessionEventData,
  shouldFetchChildSessionReplay,
} from "./subAgentChildSession";
import type {
  ConversationHistoryPage,
  ConversationRunReplay,
} from "@/services/agentConversation";

function eventData(
  resultMap: Record<string, unknown>,
  overrides: Partial<MESSAGE.EventData> = {}
): MESSAGE.EventData {
  return {
    messageOrder: 1,
    messageType: "task",
    messageId: "m1",
    taskId: "t1",
    taskOrder: 1,
    resultMap: resultMap as unknown as MESSAGE.Task,
    ...overrides,
  } as unknown as MESSAGE.EventData;
}

function frameOf(data: unknown) {
  return { resultMap: { eventData: data } };
}

function historyPage(
  runs: ConversationHistoryPage["runs"],
  latestRequestId?: string
): ConversationHistoryPage {
  return {
    sessionId: "sub_1",
    title: "",
    status: "",
    deepThink: false,
    runCount: runs.length,
    finishedRunCount: 0,
    failedRunCount: 0,
    latestRequestId,
    runs,
  };
}

function replayOf(
  requestId: string,
  frames: unknown[],
  status = "SUCCESS"
): ConversationRunReplay {
  return {
    requestId,
    status,
    replayFrames: frames.map((frame) => ({
      reqId: requestId,
      status,
      finished: true,
      resultMap: { eventData: frame as MESSAGE.EventData },
    })),
  };
}

describe("readChildSessionEventData", () => {
  it("控制帧不产出事件", () => {
    expect(readChildSessionEventData({ packageType: "heartbeat" })).toBeUndefined();
    expect(readChildSessionEventData({ packageType: "follow_idle" })).toBeUndefined();
    expect(readChildSessionEventData({ packageType: "follow_pending" })).toBeUndefined();
  });

  it("从 SSE 信封与回放帧里都能取到 eventData", () => {
    const data = eventData({
      messageType: "tool_thought",
      toolThought: "hi"
    });
    expect(readChildSessionEventData(frameOf(data))?.resultMap?.messageType).toBe(
      "tool_thought"
    );
    expect(readChildSessionEventData(data)?.resultMap?.messageType).toBe(
      "tool_thought"
    );
  });

  it("结构非法的负载返回 undefined 而不抛", () => {
    expect(readChildSessionEventData({ resultMap: { eventData: { foo: 1 } } })).toBeUndefined();
  });
});

describe("pickChildSessionRun / shouldFetchChildSessionReplay", () => {
  it("优先 latestRequestId，否则取最后一条 run", () => {
    const page = historyPage(
      [
        {
          requestId: "r1",
          status: "SUCCESS",
          hasReplay: true
        },
        {
          requestId: "r2",
          status: "SUCCESS",
          hasReplay: true
        },
      ],
      "r1"
    );
    expect(pickChildSessionRun(page)?.requestId).toBe("r1");
    expect(pickChildSessionRun(historyPage(page.runs))?.requestId).toBe("r2");
    expect(pickChildSessionRun(null)).toBeUndefined();
  });

  it("只有终态 run 才需要补拉账本回放", () => {
    expect(
      shouldFetchChildSessionReplay({
        requestId: "r1",
        status: "SUCCESS",
        hasReplay: true,
      })
    ).toBe(true);
    expect(
      shouldFetchChildSessionReplay({
        requestId: "r1",
        status: "RUNNING",
        hasReplay: true,
      })
    ).toBe(false);
    expect(shouldFetchChildSessionReplay(undefined)).toBe(false);
  });

  it("终态判定覆盖失败 / 停止 / 等待输入", () => {
    expect(isTerminalRunStatus("FAILED")).toBe(true);
    expect(isTerminalRunStatus("STOPPED")).toBe(true);
    expect(isTerminalRunStatus("WAITING_INPUT")).toBe(true);
    expect(isTerminalRunStatus("RUNNING")).toBe(false);
  });
});

describe("buildChildSessionChat", () => {
  it("终态走 replay：带出 query 与结论", () => {
    const chat = buildChildSessionChat({
      childSessionId: "sub_1",
      replay: replayOf("r1", [
        eventData({
          messageType: "tool_thought",
          toolThought: "正在检索"
        }),
        eventData({
          messageType: "result",
          result: "子结论"
        }),
      ]),
    });

    expect(chat.sessionId).toBe("sub_1");
    expect(chat.requestId).toBe("r1");
    expect(chat.loading).toBe(false);
    expect(chat.conclusion?.result).toBe("子结论");
    expect(JSON.stringify(chat.tasks)).toContain("正在检索");
  });

  it("无 replay 时用实时帧，并按 eventSeq 去重", () => {
    const chat = buildChildSessionChat({
      childSessionId: "sub_1",
      history: historyPage([{
        requestId: "r1",
        status: "RUNNING",
        hasReplay: true
      }]),
      frames: [
        {
          eventSeq: 1,
          ...frameOf(eventData({
            messageType: "agent_stream",
            result: "A"
          }))
        },
        {
          eventSeq: 2,
          ...frameOf(eventData({
            messageType: "agent_stream",
            result: "B"
          }))
        },
        {
          eventSeq: 2,
          ...frameOf(eventData({
            messageType: "agent_stream",
            result: "B"
          }))
        },
      ],
    });

    expect(chat.loading).toBe(true);
    expect(chat.conclusion?.result).toBe("AB");
  });

  it("有 replay 时忽略实时帧，避免流式增量被追加两次", () => {
    const chat = buildChildSessionChat({
      childSessionId: "sub_1",
      replay: replayOf("r1", [
        eventData({
          messageType: "agent_stream",
          result: "A"
        }),
      ]),
      frames: [
        {
          eventSeq: 1,
          ...frameOf(eventData({
            messageType: "agent_stream",
            result: "B"
          }))
        },
      ],
    });

    expect(chat.conclusion?.result).toBe("A");
  });

  it("子会话 result 无条件作为本会话结论（不做父子结论去重）", () => {
    const chat = buildChildSessionChat({
      childSessionId: "sub_1",
      replay: replayOf("r1", [
        eventData({
          messageType: "result",
          result: "带父标记也要收下",
          parentToolUseId: "tool-parent",
        }),
      ]),
    });

    expect(chat.conclusion?.result).toBe("带父标记也要收下");
  });

  it("没有 result 帧时用 run 终态摘要兜底", () => {
    const chat = buildChildSessionChat({
      childSessionId: "sub_1",
      replay: {
        requestId: "r1",
        status: "SUCCESS",
        finalSummaryText: "账本摘要",
        replayFrames: [],
      },
    });

    expect(chat.conclusion?.result).toBe("账本摘要");
  });

  it("history 未回来时用父卡文案兜底 query", () => {
    const chat = buildChildSessionChat({
      childSessionId: "sub_1",
      fallbackQuery: "帮我查一下",
    });

    expect(chat.query).toBe("帮我查一下");
  });

  it("实时帧 finished 时收口 loading", () => {
    const chat = buildChildSessionChat({
      childSessionId: "sub_1",
      history: historyPage([{
        requestId: "r1",
        status: "RUNNING",
        hasReplay: true
      }]),
      frames: [
        {
          eventSeq: 1,
          finished: true,
          ...frameOf(eventData({
            messageType: "result",
            result: "完成"
          })),
        },
      ],
    });

    expect(chat.loading).toBe(false);
    expect(chat.metrics?.status).toBe("SUCCESS");
  });
});
