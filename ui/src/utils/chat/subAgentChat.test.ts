import { describe, expect, it } from "vitest";
import { chatItemFromSubAgent, subAgentLiveRevision } from "./subAgentChat";
import { deriveAgentProcessModel } from "@/components/Dialogue/agentProcessModel";
import { resolveTaskSummaryText } from "@/components/Dialogue/contentHelpers";

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
        } as CHAT.Task,
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

  it("liveRevision changes when nested children grow or stream", () => {
    const tool = agentTool({ children: [] });
    const before = subAgentLiveRevision(tool);
    tool.children = [
      {
        id: "read-1",
        messageId: "read-1",
        messageType: "tool_call",
        resultMap: { toolName: "Read", status: "running" },
        finish: false,
        isFinal: false,
      } as CHAT.Task,
    ];
    const afterAdd = subAgentLiveRevision(tool);
    expect(afterAdd).not.toBe(before);
    tool.children[0].toolThought = "正在读文件";
    expect(subAgentLiveRevision(tool)).not.toBe(afterAdd);
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
        } as CHAT.Task,
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
});
