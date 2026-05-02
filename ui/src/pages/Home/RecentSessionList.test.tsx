import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import RecentSessionList from "./RecentSessionList";

describe("RecentSessionList", () => {
  it("renders recent sessions without exposing summary body", () => {
    const html = renderToStaticMarkup(
      <RecentSessionList
        sessions={[
          {
            sessionId: "session-history-002",
            title: "项目风险分析",
            status: "FAILED",
            latestQueryText: "继续补充方案",
            runCount: 2,
            finishedRunCount: 1,
            failedRunCount: 1,
            startedAt: "2026-05-02T10:00:00",
            lastActiveAt: "2026-05-02T10:06:00",
          },
        ]}
        selectedSessionId="session-history-002"
        onSelect={() => {}}
      />
    );

    expect(html).toContain("近期会话");
    expect(html).toContain("项目风险分析");
    expect(html).toContain("继续补充方案");
    expect(html).toContain("当前会话");
    expect(html).toContain("data-session-id=\"session-history-002\"");
    expect(html).not.toContain("summary:req-history-002");
  });
});
