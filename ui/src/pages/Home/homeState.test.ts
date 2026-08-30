import { describe, expect, it } from "vitest";

import {
  deriveConversationMetaFromInput,
  mergeLocalRecentConversations,
  mergeRecentSessions,
  shouldApplyConversationToView,
  shouldHydrateConversationHistory,
  toRecentSessionItem,
} from "./homeState";

describe("homeState", () => {
  it("仅当前会话的流式更新才写入主视图", () => {
    expect(shouldApplyConversationToView("conv-a", "conv-a")).toBe(true);
    expect(shouldApplyConversationToView("conv-a", "conv-b")).toBe(false);
    expect(shouldApplyConversationToView(undefined, "conv-a")).toBe(false);
  });

  it("切到 dataAgent 时关闭 deepThink", () => {
    expect(
      deriveConversationMetaFromInput(
        {
          outputStyle: "dataAgent",
          deepThink: true,
        },
        {
          productType: "task",
        }
      )
    ).toMatchObject({
      productType: "dataAgent",
      deepThink: false,
    });
  });

  it("未显式选择输出格式时应保留通用任务态", () => {
    expect(
      deriveConversationMetaFromInput(
        {
          deepThink: true,
        },
        {
          productType: "task",
        }
      )
    ).toMatchObject({
      productType: "task",
      deepThink: true,
    });
  });

  it("仅在未 hydrate 且没有内容时才恢复历史", () => {
    expect(
      shouldHydrateConversationHistory({
        conversation: {
          sessionId: "session-1",
          chatList: [],
          dataChatList: [],
        } as unknown as CHAT.ConversationHistory,
        hydratedSessionIds: new Set<string>(),
      })
    ).toBe(true);

    expect(
      shouldHydrateConversationHistory({
        conversation: {
          sessionId: "session-1",
          chatList: [{} as CHAT.ChatItem],
          dataChatList: [],
        } as unknown as CHAT.ConversationHistory,
        hydratedSessionIds: new Set<string>(),
      })
    ).toBe(false);
  });

  it("把用户主动创建的空会话转换成最近会话入口", () => {
    const session = toRecentSessionItem({
      sessionId: "session-new",
      title: "新对话",
      chatTitle: "",
      chatList: [],
      dataChatList: [],
      createdAt: 1000,
      updatedAt: 2000,
    } as unknown as CHAT.ConversationHistory);

    expect(session).toMatchObject({
      sessionId: "session-new",
      title: "新对话",
      status: "RUNNING",
      latestQueryText: "",
      runCount: 0,
    });
    expect(session?.lastActiveAt).toBe(new Date(2000).toISOString());
  });

  it("本地当前会话应置顶并按 sessionId 覆盖服务端旧条目", () => {
    const merged = mergeRecentSessions(
      [
        {
          sessionId: "session-existing",
          title: "服务端旧标题",
          status: "SUCCESS",
          latestQueryText: "旧问题",
          runCount: 1,
          finishedRunCount: 1,
          failedRunCount: 0,
          startedAt: "2026-05-08T09:00:00.000Z",
          lastActiveAt: "2026-05-08T09:10:00.000Z",
        },
        {
          sessionId: "session-other",
          title: "其它会话",
          status: "SUCCESS",
          latestQueryText: "其它问题",
          runCount: 1,
          finishedRunCount: 1,
          failedRunCount: 0,
          startedAt: "2026-05-08T08:00:00.000Z",
          lastActiveAt: "2026-05-08T08:10:00.000Z",
        },
      ],
      [
        {
          sessionId: "session-existing",
          title: "本地新标题",
          status: "RUNNING",
          latestQueryText: "",
          runCount: 0,
          finishedRunCount: 0,
          failedRunCount: 0,
          startedAt: "2026-05-08T09:00:00.000Z",
          lastActiveAt: "2026-05-08T09:20:00.000Z",
        },
      ]
    );

    expect(merged.map((item) => item.sessionId)).toEqual([
      "session-existing",
      "session-other",
    ]);
    expect(merged[0].title).toBe("本地新标题");
  });

  it("连续新建空会话时本地最近只保留一个空白入口", () => {
    const oldEmpty = {
      sessionId: "session-empty-old",
      title: "新对话",
      chatList: [],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;
    const existingConversation = {
      sessionId: "session-existing",
      title: "已有内容",
      chatList: [{ query: "已有问题" } as CHAT.ChatItem],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;
    const nextEmpty = {
      sessionId: "session-empty-next",
      title: "新对话",
      chatList: [],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;

    const merged = mergeLocalRecentConversations(
      [oldEmpty, existingConversation],
      nextEmpty
    );

    expect(merged.map((item) => item.sessionId)).toEqual([
      "session-empty-next",
      "session-existing",
    ]);
  });

  it("同轮流式增量不置顶，避免双会话并发时侧边栏来回跳", () => {
    const sessionA = {
      sessionId: "session-a",
      title: "任务 A",
      chatList: [{ query: "A", loading: true } as CHAT.ChatItem],
      dataChatList: [],
      updatedAt: 1,
    } as unknown as CHAT.ConversationHistory;
    const sessionB = {
      sessionId: "session-b",
      title: "任务 B",
      chatList: [{ query: "B", loading: true } as CHAT.ChatItem],
      dataChatList: [],
      updatedAt: 2,
    } as unknown as CHAT.ConversationHistory;

    const afterBOnTop = mergeLocalRecentConversations([sessionA], sessionB);
    expect(afterBOnTop.map((item) => item.sessionId)).toEqual([
      "session-b",
      "session-a",
    ]);

    const streamUpdateA = {
      ...sessionA,
      chatList: [
        {
          query: "A",
          loading: true,
          thought: "继续思考",
        } as CHAT.ChatItem,
      ],
      updatedAt: 3,
    } as unknown as CHAT.ConversationHistory;

    const afterStreamA = mergeLocalRecentConversations(
      afterBOnTop,
      streamUpdateA
    );
    expect(afterStreamA.map((item) => item.sessionId)).toEqual([
      "session-b",
      "session-a",
    ]);
    expect(afterStreamA[1].chatList[0].thought).toBe("继续思考");
  });

  it("新一轮 run 仍置顶", () => {
    const sessionA = {
      sessionId: "session-a",
      title: "任务 A",
      chatList: [{ query: "A1" } as CHAT.ChatItem],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;
    const sessionB = {
      sessionId: "session-b",
      title: "任务 B",
      chatList: [{ query: "B" } as CHAT.ChatItem],
      dataChatList: [],
    } as unknown as CHAT.ConversationHistory;
    const sessionANewRun = {
      ...sessionA,
      chatList: [
        { query: "A1" } as CHAT.ChatItem,
        { query: "A2", loading: true } as CHAT.ChatItem,
      ],
    } as unknown as CHAT.ConversationHistory;

    const merged = mergeLocalRecentConversations(
      [sessionB, sessionA],
      sessionANewRun
    );
    expect(merged.map((item) => item.sessionId)).toEqual([
      "session-a",
      "session-b",
    ]);
  });
});
