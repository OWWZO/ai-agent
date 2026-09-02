import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { AgentDetailPanel } from "./AgentDetailPanel";

describe("AgentDetailPanel shared dialogue renderer", () => {
  it("renders child rich tools through the shared timeline", () => {
    const html = renderToStaticMarkup(
      <AgentDetailPanel
        tool={
          {
            id: "agent-1",
            messageId: "agent-1",
            messageType: "tool_call",
            messageTime: "1714041600000",
            resultMap: {
              toolName: "Agent",
              input: {
                prompt: "research",
                subagent_type: "Explore",
              },
            },
            children: [
              {
                id: "child-1",
                messageId: "child-1",
                messageType: "deep_search",
                messageTime: "1714041601000",
                resultMap: {
                  messageType: "search",
                  searchResult: {
                    query: ["query"],
                    docs: [
                      [
                        {
                          link: "https://example.com",
                          title: "source",
                        },
                      ],
                    ],
                  },
                },
              },
            ],
          } as unknown as CHAT.Task
        }
        chat={
          {
            sessionId: "s1",
            requestId: "r1",
          } as CHAT.ChatItem
        }
        onClose={vi.fn()}
      />
    );

    expect(html).toContain("搜索完成");
    expect(html).toContain("query");
  });
});
