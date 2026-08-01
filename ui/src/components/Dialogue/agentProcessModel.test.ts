import { describe, expect, it } from "vitest";

import {
  deriveAgentProcessModel,
  formatProcessDuration,
  parseMessageTimeMs,
  resolveProcessStepKind,
} from "./agentProcessModel";

function createChat(overrides?: Partial<CHAT.ChatItem>): CHAT.ChatItem {
  return {
    sessionId: "process-session",
    requestId: "process-request",
    query: "排查 JSON",
    files: [],
    forceStop: false,
    loading: false,
    tasks: [],
    timeline: [],
    multiAgent: { tasks: [] },
    startedAt: "1714041600000",
    finishedAt: "1714041612000",
    ...overrides,
  } as CHAT.ChatItem;
}

function tool(
  partial: Partial<CHAT.Task> & { messageType: string }
): CHAT.Task {
  const messageType = partial.messageType;
  return {
    ...partial,
    id: partial.id || partial.messageId || `${messageType}-id`,
    messageId: partial.messageId || partial.id || `${messageType}-mid`,
    messageType,
    messageTime: partial.messageTime || "1714041601000",
    finish: partial.finish,
    isFinal: partial.isFinal,
    resultMap: {
      isFinal: partial.resultMap?.isFinal ?? partial.isFinal ?? partial.finish,
      toolName: partial.resultMap?.toolName,
      ...(partial.resultMap || {}),
    },
    toolThought: partial.toolThought,
    toolResult: partial.toolResult,
    children: partial.children,
  } as CHAT.Task;
}

describe("agentProcessModel", () => {
  it("formats duration in Cursor-like seconds", () => {
    expect(formatProcessDuration(80)).toBe("0.1s");
    expect(formatProcessDuration(400)).toBe("0.4s");
    expect(formatProcessDuration(1600)).toBe("1.6s");
    expect(formatProcessDuration(3000)).toBe("3s");
  });

  it("parses messageTime as ms", () => {
    expect(parseMessageTimeMs("1714041600000")).toBe(1714041600000);
    expect(parseMessageTimeMs("1714041600")).toBe(1714041600000);
  });

  it("classifies tool kinds from tool names", () => {
    expect(
      resolveProcessStepKind(
        tool({
          messageType: "tool_call",
          resultMap: {
            toolName: "Read",
            isFinal: true
          },
        })
      )
    ).toBe("read");
    expect(
      resolveProcessStepKind(
        tool({
          messageType: "tool_call",
          resultMap: {
            toolName: "Edit",
            isFinal: true
          },
        })
      )
    ).toBe("edit");
    expect(
      resolveProcessStepKind(
        tool({
          messageType: "tool_call",
          resultMap: {
            toolName: "Bash",
            isFinal: true
          },
        })
      )
    ).toBe("terminal");
    expect(
      resolveProcessStepKind(
        tool({
          messageType: "llm_reasoning",
          toolThought: "分析中"
        })
      )
    ).toBe("thinking");
    expect(
      resolveProcessStepKind(
        tool({
          messageType: "tool_thought",
          toolThought: "我先读文件"
        })
      )
    ).toBe("assistant_reply");
  });

  it("splits by assistant reply: thinking → reply → tool groups", () => {
    const chat = createChat({
      loading: false,
      startedAt: "1714041600000",
      finishedAt: "1714041608000",
      tasks: [
        [
          {
            id: "container-1",
            task: "",
            children: [
              tool({
                id: "r1",
                messageType: "llm_reasoning",
                messageTime: "1714041600500",
                toolThought: "需要重算变量",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "a1",
                messageType: "tool_thought",
                messageTime: "1714041601000",
                toolThought: "看到列名用的是中文引号。现在重新计算所有变量。",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "s1",
                messageType: "tool_call",
                messageTime: "1714041601500",
                resultMap: {
                  toolName: "Bash",
                  isFinal: true
                },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "a2",
                messageType: "tool_thought",
                messageTime: "1714041603000",
                toolThought: "变量计算成功！相关矩阵已出。",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "s2",
                messageType: "tool_call",
                messageTime: "1714041604000",
                resultMap: {
                  toolName: "Read",
                  isFinal: true
                },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "s3",
                messageType: "tool_call",
                messageTime: "1714041605000",
                resultMap: {
                  toolName: "Bash",
                  isFinal: true
                },
                isFinal: true,
                finish: true,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
      ],
    });

    const model = deriveAgentProcessModel({
      chat,
      isPlanSolve: false,
      nowMs: 1714041608000,
    });

    expect(model.hasProcess).toBe(true);
    expect(model.segments.map((s) => s.type)).toEqual([
      "thinking",
      "assistant_reply",
      "group",
      "assistant_reply",
      "group",
    ]);
    expect(model.segments[0].type === "thinking").toBe(true);
    expect(
      model.segments[1].type === "assistant_reply" && model.segments[1].text
    ).toContain("中文引号");
    expect(
      model.segments[2].type === "group" && model.segments[2].group.stepCount
    ).toBe(1);
    expect(
      model.segments[3].type === "assistant_reply" && model.segments[3].text
    ).toContain("变量计算成功");
    expect(
      model.segments[4].type === "group" && model.segments[4].group.title
    ).toBe("执行了 2 个步骤");
    expect(model.totalStepCount).toBe(3);
  });

  it("marks last group active while loading", () => {
    const chat = createChat({
      loading: true,
      startedAt: "1714041600000",
      finishedAt: undefined,
      tasks: [
        [
          {
            id: "c1",
            children: [
              tool({
                id: "running",
                messageType: "tool_call",
                messageTime: "1714041601000",
                resultMap: {
                  toolName: "Edit",
                  isFinal: false
                },
                isFinal: false,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
      ],
    });

    const model = deriveAgentProcessModel({
      chat,
      isPlanSolve: false,
      nowMs: 1714041602500,
    });

    expect(model.groups[0].active).toBe(true);
    expect(model.groups[0].title).toContain("正在执行");
    expect(model.groups[0].steps[0].active).toBe(true);
    expect(model.groups[0].steps[0].kind).toBe("edit");
  });

  it("uses plan task label for PlanSolve groups", () => {
    const chat = createChat({
      tasks: [
        [
          {
            id: "plan-task",
            task: "收集资料",
            children: [
              tool({
                id: "search-1",
                messageType: "deep_search",
                resultMap: {
                  messageType: "search",
                  isFinal: true
                },
                isFinal: true,
                finish: true,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
      ],
    });

    const model = deriveAgentProcessModel({
      chat,
      isPlanSolve: true,
    });

    expect(model.groups[0].title).toBe("收集资料");
    expect(model.groups[0].steps[0].kind).toBe("search");
  });

  it("splits work groups by SendUserMessage and never folds the message", () => {
    const chat = createChat({
      loading: false,
      tasks: [
        [
          {
            id: "c1",
            children: [
              tool({
                id: "t1",
                messageType: "tool_call",
                messageTime: "1714041601000",
                resultMap: {
                  toolName: "Read",
                  isFinal: true
                },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "t2",
                messageType: "tool_call",
                messageTime: "1714041602000",
                resultMap: {
                  toolName: "Edit",
                  isFinal: true
                },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "brief-1",
                messageType: "user_brief",
                messageTime: "1714041603000",
                resultMap: {
                  message: "问题在第58行，我先修一下。",
                  status: "normal",
                  isFinal: true,
                } as CHAT.Task["resultMap"],
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "t3",
                messageType: "tool_call",
                messageTime: "1714041604000",
                resultMap: {
                  toolName: "Bash",
                  isFinal: true
                },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "final-thought",
                messageType: "tool_thought",
                messageTime: "1714041605000",
                toolThought: "已经修好了，JSON 合法。",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
      ],
    });

    const model = deriveAgentProcessModel({
      chat,
      isPlanSolve: false,
      nowMs: 1714041606000,
    });

    expect(model.segments.map((s) => s.type)).toEqual([
      "group",
      "user_message",
      "group",
      "assistant_reply",
    ]);
    expect(model.segments[0].type === "group" && model.segments[0].group.title).toBe(
      "执行了 2 个步骤"
    );
    expect(model.segments[1].type).toBe("user_message");
    expect(model.segments[2].type === "group" && model.segments[2].group.title).toBe(
      "执行了 1 个步骤"
    );
    expect(
      model.segments[3].type === "assistant_reply" && model.segments[3].text
    ).toBe("已经修好了，JSON 合法。");
  });

  it("empty assistant reply does not split; thinking joins tool group", () => {
    const chat = createChat({
      loading: false,
      tasks: [
        [
          {
            id: "c1",
            children: [
              tool({
                id: "r0",
                messageType: "llm_reasoning",
                messageTime: "1714041600000",
                toolThought: "先规划目录",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "a0",
                messageType: "tool_thought",
                messageTime: "1714041600500",
                toolThought: "我来帮你搭建一个简洁主页。",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "t1",
                messageType: "tool_call",
                messageTime: "1714041601000",
                resultMap: { toolName: "Bash", isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "r1",
                messageType: "llm_reasoning",
                messageTime: "1714041602000",
                toolThought: "接下来写样式",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "empty-reply",
                messageType: "tool_thought",
                messageTime: "1714041602500",
                toolThought: "   ",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "t2",
                messageType: "tool_call",
                messageTime: "1714041603000",
                resultMap: { toolName: "Edit", isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "r2",
                messageType: "llm_reasoning",
                messageTime: "1714041604000",
                toolThought: "再写页面",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
              tool({
                id: "t3",
                messageType: "tool_call",
                messageTime: "1714041605000",
                resultMap: { toolName: "Write", isFinal: true },
                isFinal: true,
                finish: true,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
      ],
    });

    const model = deriveAgentProcessModel({
      chat,
      isPlanSolve: false,
      nowMs: 1714041608000,
    });

    expect(model.segments.map((s) => s.type)).toEqual([
      "thinking",
      "assistant_reply",
      "group",
    ]);
    const group = model.segments[2];
    expect(group.type).toBe("group");
    if (group.type === "group") {
      // 空回复不切开：3 工具 + 2 组内深度思考
      expect(group.group.stepCount).toBe(3);
      expect(group.group.title).toBe("执行了 3 个步骤");
      expect(group.group.steps.map((s) => s.kind)).toEqual([
        "terminal",
        "thinking",
        "edit",
        "thinking",
        "edit",
      ]);
    }
  });

  it("merges multi-container history tools into one collapsible group for ReAct", () => {
    // 模拟历史投影：每个工具独立 task 容器（不同 taskId 拆组）
    const chat = createChat({
      loading: false,
      tasks: [
        [
          {
            id: "c1",
            taskId: "task-1",
            children: [
              tool({
                id: "t1",
                messageType: "tool_call",
                messageTime: "1714041601000",
                resultMap: { toolName: "Read", isFinal: true },
                isFinal: true,
                finish: true,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
        [
          {
            id: "c2",
            taskId: "task-2",
            children: [
              tool({
                id: "t2",
                messageType: "tool_call",
                messageTime: "1714041602000",
                resultMap: { toolName: "Edit", isFinal: true },
                isFinal: true,
                finish: true,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
        [
          {
            id: "c3",
            taskId: "task-3",
            children: [
              tool({
                id: "t3",
                messageType: "tool_call",
                messageTime: "1714041603000",
                resultMap: { toolName: "Bash", isFinal: true },
                isFinal: true,
                finish: true,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
      ],
    });

    const model = deriveAgentProcessModel({
      chat,
      isPlanSolve: false,
      nowMs: 1714041608000,
    });

    expect(model.segments.map((s) => s.type)).toEqual(["group"]);
    const group = model.segments[0];
    expect(group.type).toBe("group");
    if (group.type === "group") {
      expect(group.group.stepCount).toBe(3);
      expect(group.group.title).toBe("执行了 3 个步骤");
      expect(group.group.completed).toBe(true);
    }
  });

  it("does not promote lone assistant-reply to final_reply (终答只走 conclusion)", () => {
    const chat = createChat({
      loading: false,
      tasks: [
        [
          {
            id: "c1",
            children: [
              tool({
                id: "only-think",
                messageType: "tool_thought",
                toolThought: "这是最终答复正文。",
                resultMap: { isFinal: true },
                isFinal: true,
                finish: true,
              }),
            ],
          } as unknown as CHAT.Task,
        ],
      ],
    });

    const model = deriveAgentProcessModel({
      chat,
      isPlanSolve: false
    });
    expect(model.segments).toHaveLength(1);
    expect(model.segments[0].type).toBe("assistant_reply");
    expect(model.finalReply).toBeUndefined();
  });
});
