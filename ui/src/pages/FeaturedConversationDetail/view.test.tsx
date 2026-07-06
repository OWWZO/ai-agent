import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const chatViewMock = vi.fn((props: any) => (
  <div
    data-chat-view="true"
    data-read-only={String(Boolean(props.readOnly))}
    data-chat-count={String(props.conversation?.chatList?.length ?? 0)}
  >
    {props.conversation?.chatList?.[0]?.query || "empty"}
  </div>
));

vi.mock("@/components/ChatView", () => ({default: (props: any) => chatViewMock(props),}));

import { FeaturedConversationDetailView } from "./view";

function createResultReplayFrame(result: string) {
  return {
    reqId: "req-detail-001",
    status: "success",
    finished: true,
    resultMap: {
      agentType: "history",
      multiAgent: {},
      eventData: {
        taskId: "task-detail-1",
        taskOrder: 1,
        messageType: "task",
        messageOrder: 1,
        messageId: "msg-detail-result-1",
        resultMap: {
          requestId: "req-detail-001",
          messageId: "msg-detail-result-1",
          messageType: "result",
          messageTime: "1714620002000",
          isFinal: true,
          finish: true,
          result,
          taskSummary: result,
          fileList: [],
        } as unknown as MESSAGE.Task,
      },
    },
  };
}

describe("FeaturedConversationDetailView", () => {
  beforeEach(() => {
    chatViewMock.mockClear();
  });

  it("removes the top meta card and maximizes the read-only transcript area", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <FeaturedConversationDetailView
          loading={false}
          detail={{
            featuredId: "featured-detail-001",
            sessionId: "session-detail-001",
            title: "精品详情",
            summary: "只读案例",
            tags: ["研究"],
            coverUrl: "",
            publishedAt: "2026-07-06T10:00:00",
            contentLastActiveAt: "2026-07-06T11:00:00",
            contentAvailable: true,
            contentUnavailableReason: "",
            historyDetail: {
              sessionId: "session-detail-001",
              title: "原会话",
              status: "SUCCESS",
              outputStyle: "chat",
              deepThink: false,
              role: null,
              runCount: 1,
              finishedRunCount: 1,
              failedRunCount: 0,
              startedAt: "2026-07-06T10:00:00",
              lastActiveAt: "2026-07-06T11:00:00",
              runs: [
                {
                  requestId: "req-detail-001",
                  status: "SUCCESS",
                  queryText: "请给我一个示例",
                  finalSummaryText: "这是最终结论",
                  startedAt: "2026-07-06T10:00:00",
                  finishedAt: "2026-07-06T11:00:00",
                  replayFrames: [createResultReplayFrame("这是最终结论")],
                },
              ],
            },
          }}
        />
      </MemoryRouter>
    );

    expect(html).not.toContain("发布时间");
    expect(html).not.toContain("内容最近更新");
    expect(html).not.toContain("精品详情");
    expect(html).toContain('data-chat-view="true"');
    expect(html).toContain('data-read-only="true"');
    expect(html).toContain("请给我一个示例");
    expect(chatViewMock).toHaveBeenCalledTimes(1);
    expect(chatViewMock.mock.calls[0]?.[0]).toMatchObject({
      readOnly: true,
      conversation: expect.objectContaining({
        sessionId: "session-detail-001",
        productType: "chat",
      }),
    });
  });

  it("renders a readable fallback when live content is unavailable", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <FeaturedConversationDetailView
          loading={false}
          detail={{
            featuredId: "featured-detail-002",
            sessionId: "session-detail-002",
            title: "异常案例",
            summary: "正文暂不可用",
            tags: [],
            coverUrl: "",
            publishedAt: "2026-07-06T10:00:00",
            contentLastActiveAt: "",
            contentAvailable: false,
            contentUnavailableReason: "session_history_missing",
            historyDetail: null,
          }}
        />
      </MemoryRouter>
    );

    expect(html).toContain("正文暂不可用");
    expect(html).toContain("session_history_missing");
    expect(chatViewMock).not.toHaveBeenCalled();
  });
});
