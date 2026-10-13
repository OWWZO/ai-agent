import { describe, expect, it } from "vitest";
import {
  collectPendingReplayIds,
  groupChatsIntoTurns,
  isContinuationRun,
  mergeTurnChats,
} from "./turnGroups";

function chat(overrides: Record<string, unknown> = {}): CHAT.ChatItem {
  return {
    sessionId: "s1",
    requestId: "req-1",
    query: "测试一下 asktool",
    files: [],
    loading: false,
    forceStop: false,
    tasks: [],
    multiAgent: { tasks: [] },
    timeline: [],
    ...overrides,
  } as unknown as CHAT.ChatItem;
}

describe("isContinuationRun", () => {
  it("requestId 带 resume_ 前缀视为续跑", () => {
    expect(isContinuationRun(chat({ requestId: "resume_abc" }))).toBe(true);
  });

  it("没有 query 的 run 兜底视为续跑", () => {
    expect(isContinuationRun(chat({ query: "" }))).toBe(true);
  });

  it("有 query 的普通 run 不是续跑", () => {
    expect(isContinuationRun(chat())).toBe(false);
  });
});

describe("groupChatsIntoTurns", () => {
  it("把 resume_ 的续跑 run 归到前一个提问 run", () => {
    const turns = groupChatsIntoTurns([
      chat({ requestId: "req-1" }),
      chat({
        requestId: "resume_a",
        query: ""
      }),
    ]);

    expect(turns).toHaveLength(1);
    expect(turns[0].key).toBe("req-1");
    expect(turns[0].chats.map((item) => item.requestId)).toEqual([
      "req-1",
      "resume_a",
    ]);
  });

  it("两个独立提问切成两个 turn", () => {
    const turns = groupChatsIntoTurns([
      chat({ requestId: "req-1" }),
      chat({ requestId: "req-2" }),
    ]);

    expect(turns.map((turn) => turn.key)).toEqual(["req-1", "req-2"]);
  });

  it("续跑出现在列表开头时自成 turn（没有可归属的前驱）", () => {
    const turns = groupChatsIntoTurns([chat({
      requestId: "resume_a",
      query: ""
    })]);

    expect(turns).toHaveLength(1);
    expect(turns[0].key).toBe("resume_a");
  });

  it("支持连续多段续跑", () => {
    const turns = groupChatsIntoTurns([
      chat({ requestId: "req-1" }),
      chat({
        requestId: "resume_a",
        query: ""
      }),
      chat({
        requestId: "resume_b",
        query: ""
      }),
      chat({ requestId: "req-2" }),
    ]);

    expect(turns).toHaveLength(2);
    expect(turns[0].chats).toHaveLength(3);
    expect(turns[1].chats).toHaveLength(1);
  });
});

describe("mergeTurnChats", () => {
  it("单 run 的 turn 原样返回同一个对象", () => {
    const single = chat();
    const merged = mergeTurnChats({
      key: "req-1",
      chats: [single]
    });

    expect(merged).toBe(single);
  });

  it("过程数据按 run 顺序拼接", () => {
    const merged = mergeTurnChats({
      key: "req-1",
      chats: [
        chat({
          requestId: "req-1",
          tasks: [[{ id: "t1" } as never]],
          timeline: [{ seq: 1 } as never],
        }),
        chat({
          requestId: "resume_a",
          query: "",
          tasks: [[{ id: "t2" } as never]],
          timeline: [{ seq: 2 } as never],
          multiAgent: { tasks: [{ id: "m2" } as never] },
        }),
      ],
    });

    expect(merged.tasks.map((group) => group[0]?.id)).toEqual(["t1", "t2"]);
    expect(merged.timeline?.map((entry) => entry.seq)).toEqual([1, 2]);
    expect(merged.multiAgent.tasks).toHaveLength(1);
  });

  it("时长求和、loading 取任一、终态取最后一个 run", () => {
    const merged = mergeTurnChats({
      key: "req-1",
      chats: [
        chat({
          requestId: "req-1",
          runDurationMs: 13_000,
          loading: true,
          metrics: { status: "WAITING_INPUT" },
          response: "第一段",
        }),
        chat({
          requestId: "resume_a",
          query: "",
          runDurationMs: 6_000,
          loading: false,
          metrics: { status: "SUCCESS" },
          response: "最终回答",
        }),
      ],
    });

    expect(merged.runDurationMs).toBe(19_000);
    expect(merged.loading).toBe(true);
    expect(merged.metrics?.status).toBe("SUCCESS");
    expect(merged.response).toBe("最终回答");
    // requestId 取最后一个 run：streamingThought / replayLoading / onUndo 都按最新 run 索引
    expect(merged.requestId).toBe("resume_a");
    // query 取首个 run：用户消息只出现一次
    expect(merged.query).toBe("测试一下 asktool");
  });

  it("没有任何时长时不写 runDurationMs（避免渲染成「已工作 0 秒」）", () => {
    const merged = mergeTurnChats({
      key: "req-1",
      chats: [
        chat({ requestId: "req-1" }),
        chat({
          requestId: "resume_a",
          query: ""
        }),
      ],
    });

    expect(merged.runDurationMs).toBeUndefined();
  });
});

describe("collectPendingReplayIds", () => {
  it("收集 turn 内所有还没补过回放的 run（含 AskUser 之前那个）", () => {
    expect(
      collectPendingReplayIds([
        chat({ requestId: "req-1" }),
        chat({
          requestId: "resume_a",
          query: ""
        }),
      ])
    ).toEqual(["req-1", "resume_a"]);
  });

  it("已加载或明确无回放的 run 不再请求", () => {
    expect(
      collectPendingReplayIds([
        chat({
          requestId: "req-1",
          replayLoaded: true
        }),
        chat({
          requestId: "resume_a",
          query: "",
          replayAvailable: false
        }),
        chat({
          requestId: "resume_b",
          query: ""
        }),
      ])
    ).toEqual(["resume_b"]);
  });

  it("没有 requestId 的 run 被跳过", () => {
    expect(
      collectPendingReplayIds([
        chat({ requestId: "" }),
        chat({
          requestId: "resume_a",
          query: ""
        }),
      ])
    ).toEqual(["resume_a"]);
  });
});
