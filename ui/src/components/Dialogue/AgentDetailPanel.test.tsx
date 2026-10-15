import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AgentDetailPanel } from "./AgentDetailPanel";

const childSessionTool = {
  id: "agent-1",
  messageId: "agent-1",
  messageType: "tool_call",
  messageTime: "1714041600000",
  resultMap: {
    toolName: "Agent",
    childSessionId: "sub_agent-1",
    input: {
      prompt: "research",
      subagent_type: "Explore",
    },
  },
} as unknown as CHAT.Task;

const childSessionChat = {
  sessionId: "s1",
  requestId: "r1",
} as CHAT.ChatItem;

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

describe("AgentDetailPanel real child session (flag on)", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it("flag 开启且父卡带 childSessionId 时显示加载骨架，不塞合成投影", async () => {
    vi.stubEnv("VITE_SUBAGENT_CHILD_SESSION", "1");
    vi.resetModules();
    const { AgentDetailPanel: Panel } = await import("./AgentDetailPanel");

    const html = renderToStaticMarkup(
      <Panel tool={childSessionTool} chat={childSessionChat} onClose={vi.fn()} />
    );

    // SSR 不跑 effect → 订阅未启动 → 只应看到骨架，不应出现合成投影里的子工具内容
    expect(html).toContain("加载子会话");
    expect(html).not.toContain("搜索完成");
  });

  it("flag 开启但没有 childSessionId 的旧父卡回退合成投影", async () => {
    vi.stubEnv("VITE_SUBAGENT_CHILD_SESSION", "1");
    vi.resetModules();
    const { AgentDetailPanel: Panel } = await import("./AgentDetailPanel");

    const html = renderToStaticMarkup(
      <Panel
        tool={
          {
            ...childSessionTool,
            resultMap: {
              toolName: "Agent",
              input: {
                prompt: "research",
                subagent_type: "Explore"
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
                    docs: []
                  },
                },
              },
            ],
          } as unknown as CHAT.Task
        }
        chat={childSessionChat}
        onClose={vi.fn()}
      />
    );

    expect(html).not.toContain("加载子会话");
    expect(html).toContain("query");
  });
});
