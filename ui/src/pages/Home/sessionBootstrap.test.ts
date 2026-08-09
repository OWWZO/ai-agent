import { describe, expect, it } from "vitest";

import { resolveInitialSessionId } from "./sessionBootstrap";

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
});
