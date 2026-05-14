import { describe, expect, it } from "vitest";

import { resolveInitialSessionId } from "./sessionBootstrap";

describe("sessionBootstrap", () => {
  const sessions = [
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

  it("首屏进入时即使存在本地 sessionId 也不自动恢复历史会话", () => {
    expect(
      resolveInitialSessionId({
        recentSessions: sessions,
        storedSessionId: "session-001",
      })
    ).toBeNull();
  });

  it("首屏进入时即使存在最近会话也保持主界面空白态", () => {
    expect(
      resolveInitialSessionId({
        recentSessions: sessions,
        storedSessionId: "session-stale-001",
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
