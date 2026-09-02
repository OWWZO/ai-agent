import { describe, expect, it } from "vitest";
import { chatItemFromSubAgent } from "./subAgentChat";
import { deriveAgentProcessModel } from "@/components/Dialogue/agentProcessModel";
import { resolveTaskSummaryText } from "@/components/Dialogue/contentHelpers";
import { buildDeepSearchPreviewModel } from "@/utils/deepSearch";
import { getTaskFiles } from "@/utils/taskArtifacts";

function parentChat(): CHAT.ChatItem {
  return {
    sessionId: "s1",
    requestId: "r1",
    query: "parent query",
    files: [],
    forceStop: false,
    loading: true,
    tasks: [],
    timeline: [],
    multiAgent: { tasks: [] },
  } as CHAT.ChatItem;
}

function agentTool(overrides?: Partial<CHAT.Task>): CHAT.Task {
  const { resultMap, ...rest } = overrides || {};
  return {
    id: "agent-1",
    messageId: "agent-1",
    messageType: "tool_call",
    requestId: "r1",
    messageTime: "1714041600000",
    finish: false,
    isFinal: false,
    ...rest,
    resultMap: {
      toolName: "Agent",
      toolCallId: "tc-agent",
      status: "running",
      input: {
        description: "探前端",
        prompt: "scan ui/",
        subagent_type: "Explore",
      },
      ...resultMap,
    },
  } as CHAT.Task;
}

describe("chatItemFromSubAgent", () => {
  it("maps prompt to query and nests children as process tasks", () => {
    const tool = agentTool({
      children: [
        {
          id: "read-1",
          messageId: "read-1",
          messageType: "tool_result",
          resultMap: {
            toolName: "Read",
            isFinal: true
          },
          finish: true,
          isFinal: true,
        } as unknown as CHAT.Task,
      ],
    });

    const chat = chatItemFromSubAgent(tool, parentChat());
    expect(chat.query).toBe("scan ui/");
    expect(chat.loading).toBe(true);
    expect(chat.tasks[0][0].children).toHaveLength(1);
    expect(chat.tasks[0][0].children?.[0].resultMap.toolName).toBe("Read");
  });

  it("injects liveText as assistant_reply before tools when no tool_thought", () => {
    const tool = agentTool({
      resultMap: {
        toolName: "Agent",
        status: "running",
        input: {
          description: "探前端",
          prompt: "scan ui/",
          subagent_type: "Explore",
        },
        subAgentLiveText: "正在查看目录结构",
      },
      children: [
        {
          id: "think-1",
          messageId: "think-1",
          messageType: "llm_reasoning",
          toolThought: "先列文件",
          resultMap: { isFinal: true },
          finish: true,
          isFinal: true,
        } as CHAT.Task,
        {
          id: "read-1",
          messageId: "read-1",
          messageType: "tool_call",
          resultMap: {
            toolName: "Read",
            isFinal: true
          },
          finish: true,
          isFinal: true,
        } as CHAT.Task,
      ],
    });

    const chat = chatItemFromSubAgent(tool, parentChat());
    const model = deriveAgentProcessModel({
      chat,
      isPlanSolve: false,
    });
    expect(model.segments.map((s) => s.type)).toEqual([
      "thinking",
      "assistant_reply",
      "group",
    ]);
    expect(
      model.segments[1].type === "assistant_reply" && model.segments[1].text
    ).toBe("正在查看目录结构");
  });

  it("same-length live text replacement shows up in the projected chat", () => {
    const before = chatItemFromSubAgent(
      agentTool({
        resultMap: {
          toolName: "Agent",
          status: "running",
          input: {
            prompt: "scan ui/",
            subagent_type: "Explore",
          },
          subAgentLiveText: "abc",
        },
      }),
      parentChat()
    );
    const after = chatItemFromSubAgent(
      agentTool({
        resultMap: {
          toolName: "Agent",
          status: "running",
          input: {
            prompt: "scan ui/",
            subagent_type: "Explore",
          },
          subAgentLiveText: "xyz",
        },
      }),
      parentChat()
    );
    const thought = (chat: CHAT.ChatItem) =>
      chat.tasks[0]?.[0]?.children?.find((child) => child.messageType === "tool_thought")
        ?.toolThought;
    expect(thought(before)).toBe("abc");
    expect(thought(after)).toBe("xyz");
  });

  it("does not inject heartbeat progress lines when nested tools already exist", () => {
    const tool = agentTool({
      resultMap: {
        toolName: "Agent",
        status: "running",
        input: {
          prompt: "scan ui/",
          subagent_type: "Explore",
        },
        subAgentProgressLines: ["running · Explore · 调研媒体评论抓取 · 12s"],
      },
      children: [
        {
          id: "read-1",
          messageId: "read-1",
          messageType: "tool_call",
          resultMap: {
            toolName: "Read",
            status: "running",
          },
          finish: false,
          isFinal: false,
        } as CHAT.Task,
      ],
    });

    const chat = chatItemFromSubAgent(tool, parentChat());
    const children = chat.tasks[0][0].children || [];
    expect(children).toHaveLength(1);
    expect(children[0].resultMap?.toolName).toBe("Read");
    expect(children.some((child) => String(child.toolThought || "").includes("running ·"))).toBe(false);
  });

  it("does not inject liveText when nested tool_thought already exists", () => {
    const tool = agentTool({
      resultMap: {
        toolName: "Agent",
        status: "running",
        input: {
          prompt: "scan ui/",
          subagent_type: "Explore"
        },
        subAgentLiveText: "duplicate",
      },
      children: [
        {
          id: "t1",
          messageId: "t1",
          messageType: "tool_thought",
          toolThought: "真实过程回复",
          resultMap: { isFinal: true },
          finish: true,
          isFinal: true,
        } as CHAT.Task,
      ],
    });

    const chat = chatItemFromSubAgent(tool, parentChat());
    const children = chat.tasks[0][0].children || [];
    expect(children).toHaveLength(1);
    expect(children[0].toolThought).toBe("真实过程回复");
  });

  it("does not use Agent dispatch receipt as conclusion", () => {
    const receipt = JSON.stringify({
      tool: "Agent",
      ok: true,
      status: "running",
      task_id: "09ae2a848328",
      message: "后台子 Agent 已启动。用 TaskOutput 取结果",
      run_in_background: true,
    });
    const tool = agentTool({
      messageType: "tool_result",
      finish: true,
      isFinal: true,
      toolResult: {
        toolName: "Agent",
        toolResult: receipt,
      },
      resultMap: {
        toolName: "Agent",
        status: "success",
        isFinal: true,
        input: {
          prompt: "scan ui/",
          subagent_type: "Explore",
          run_in_background: true,
        },
      },
    });
    const chat = chatItemFromSubAgent(tool, parentChat());
    expect(chat.conclusion).toBeUndefined();
  });

  it("uses nested result child as conclusion, not Agent observation", () => {
    const tool = agentTool({
      messageType: "tool_result",
      finish: true,
      isFinal: true,
      toolResult: {
        toolName: "Agent",
        toolResult: JSON.stringify({
          tool: "Agent",
          ok: true,
          status: "running",
          message: "后台子 Agent 已启动",
        }),
      },
      resultMap: {
        toolName: "Agent",
        status: "success",
        isFinal: true,
        input: {
          prompt: "scan ui/",
          subagent_type: "Explore"
        },
      },
      children: [
        {
          id: "sub-result",
          messageId: "sub-result",
          messageType: "result",
          result: "页面已经生成完毕。",
          resultMap: {
            result: "页面已经生成完毕。",
            isFinal: true
          },
          finish: true,
          isFinal: true,
        } as unknown as CHAT.Task,
      ],
    });
    const chat = chatItemFromSubAgent(tool, parentChat());
    expect(resolveTaskSummaryText(chat.conclusion)).toBe("页面已经生成完毕。");
    expect(
      (chat.tasks[0]?.[0]?.children || []).some((c) => c.messageType === "result")
    ).toBe(false);
  });

  it("maps observation content to conclusion", () => {
    const tool = agentTool({
      messageType: "tool_result",
      finish: true,
      isFinal: true,
      toolResult: {
        toolName: "Agent",
        toolResult: "status=completed\nagentType=Explore\n\n找到 3 个页面。",
      },
      resultMap: {
        toolName: "Agent",
        status: "success",
        isFinal: true,
        input: {
          prompt: "scan ui/",
          subagent_type: "Explore"
        },
      },
    });

    const chat = chatItemFromSubAgent(tool, parentChat());
    expect(chat.loading).toBe(false);
    expect(resolveTaskSummaryText(chat.conclusion)).toContain("找到 3 个页面");
  });

  it("reads a nested Agent tool result in the detail panel projection", () => {
    const tool = agentTool({
      messageType: "tool_result",
      finish: true,
      isFinal: true,
      resultMap: {
        status: "success",
        toolResult: {
          toolName: "Agent",
          toolResult: "status=completed\nagentType=Explore\n\n嵌套结果",
        },
      } as unknown as CHAT.Task["resultMap"],
    });

    const chat = chatItemFromSubAgent(tool, parentChat());
    expect(chat.loading).toBe(false);
    expect(resolveTaskSummaryText(chat.conclusion)).toContain("嵌套结果");
  });

  it("preserves child result fileList for the shared conclusion renderer", () => {
    const tool = agentTool({
      children: [
        {
          id: "child-result",
          messageId: "child-result",
          messageType: "result",
          result: "报告已完成。$$$ child-report.md",
          fileList: [
            {
              fileName: "child-report.md",
              domainUrl: "https://example.com/child-report.md",
              ossUrl: "https://example.com/child-report.md",
              fileSize: 32,
            },
          ],
          resultMap: {
            taskSummary: "报告已完成。$$$ child-report.md",
          },
          finish: true,
          isFinal: true,
        } as unknown as CHAT.Task,
      ],
    });

    const chat = chatItemFromSubAgent(tool, parentChat());
    expect(resolveTaskSummaryText(chat.conclusion)).toBe("报告已完成。");
    expect(getTaskFiles(chat.conclusion)).toEqual([
      expect.objectContaining({
        name: "child-report.md",
        url: "https://example.com/child-report.md",
      }),
    ]);
  });

  it("projects child deep search into the same per-query inline cards as the main timeline", () => {
    const tool = agentTool({
      children: [
        {
          id: "deep-search-child",
          messageId: "deep-search-child",
          messageType: "deep_search",
          messageTime: "1714041600000",
          resultMap: {
            messageType: "search",
            searchResult: {
              query: ["查询一", "查询二"],
              docs: [
                [
                  {
                    link: "https://example.com/1",
                    title: "来源一",
                  },
                ],
                [
                  {
                    link: "https://example.com/2",
                    title: "来源二",
                  },
                ],
              ],
            },
          },
          finish: true,
          isFinal: true,
        } as unknown as CHAT.Task,
      ],
    });

    const chat = chatItemFromSubAgent(tool, parentChat());
    const children = chat.tasks[0]?.[0]?.children || [];

    expect(children).toHaveLength(2);
    expect(children.map((child) => buildDeepSearchPreviewModel(child)?.query)).toEqual([
      "查询一",
      "查询二",
    ]);
  });
});
