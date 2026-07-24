import { describe, expect, it } from "vitest";

import {
  deriveConversationMetaFromInput,
  mergeLocalRecentConversations,
  mergeRecentSessions,
  shouldHydrateConversationHistory,
  toRecentSessionItem,
} from "./homeState";

describe("homeState", () => {
  it("切到 dataAgent 时应清空角色并关闭 deepThink", () => {
    expect(
      deriveConversationMetaFromInput(
        {
          outputStyle: "dataAgent",
          deepThink: true,
        },
        {
          productType: "html",
          currentRole: {
            agentId: "agent-1",
            agentName: "默认角色",
            available: true,
            defaultRole: true,
          },
        }
      )
    ).toMatchObject({
      productType: "dataAgent",
      deepThink: false,
      role: null,
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
          currentRole: null,
        }
      )
    ).toMatchObject({
      productType: "task",
      deepThink: true,
      role: null,
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
});
