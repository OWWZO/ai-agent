import { describe, expect, it } from "vitest";
import { combineData } from "@/utils/chat";
import { resolveSubAgentDisplay } from "./subagent";

/**
 * 父流 subagent_start / subagent_end 摘要帧的落地契约。
 * 这两条帧只在 flag 开启时出现，用来在观察结果到达之前给父卡补
 * childSessionId / phase / summary，因此必须命中已有父卡而不是新建卡片。
 */

function lifecycleEvent(
  messageType: "subagent_start" | "subagent_end",
  payload: Record<string, unknown>,
  overrides: Partial<MESSAGE.EventData> = {}
): MESSAGE.EventData {
  return {
    messageOrder: 1,
    messageType: "task",
    messageId: `m-${messageType}`,
    taskId: "t1",
    taskOrder: 1,
    resultMap: { messageType, ...payload } as unknown as MESSAGE.Task,
    ...overrides,
  } as unknown as MESSAGE.EventData;
}

function chatWithParentCard() {
  return {
    sessionId: "s1",
    requestId: "r1",
    query: "",
    files: [],
    loading: true,
    forceStop: false,
    tasks: [],
    multiAgent: {
      tasks: [
        [
          {
            messageType: "tool_call",
            messageId: "call-1",
            toolCallId: "call-1",
            resultMap: {
              toolName: "Agent",
              status: "running",
              input: { description: "探索代码库", subagent_type: "Explore" },
            },
          } as unknown as MESSAGE.Task,
        ],
      ],
    },
    timeline: [],
  } as unknown as CHAT.ChatItem;
}

function parentCard(chat: CHAT.ChatItem) {
  for (const group of chat.multiAgent.tasks) {
    if (group?.length) {
      return group[0];
    }
  }
  throw new Error("父 Agent 卡片不存在");
}

describe("subagent_start / subagent_end 摘要帧", () => {
  it("start 帧把 childSessionId 与 working 相位写到父卡", () => {
    const chat = chatWithParentCard();

    combineData(
      lifecycleEvent("subagent_start", {
        childSessionId: "sub_agent-1",
        parentToolCallId: "call-1",
        subAgentId: "agent-1",
        subagentType: "Explore",
        description: "探索代码库",
        status: "running",
      }),
      chat
    );

    const card = parentCard(chat);
    expect(card.resultMap?.childSessionId).toBe("sub_agent-1");
    expect(card.resultMap?.subAgentPhase).toBe("working");
    expect(card.resultMap?.subAgentType).toBe("Explore");
  });

  it("end 帧写入终态相位与摘要", () => {
    const chat = chatWithParentCard();

    combineData(
      lifecycleEvent("subagent_end", {
        childSessionId: "sub_agent-1",
        parentToolCallId: "call-1",
        subAgentId: "agent-1",
        status: "completed",
        summary: "找到 3 处调用",
      }),
      chat
    );

    const card = parentCard(chat);
    expect(card.resultMap?.subAgentPhase).toBe("completed");
    expect(card.resultMap?.subAgentSummary).toBe("找到 3 处调用");
    expect(card.resultMap?.childSessionId).toBe("sub_agent-1");
  });

  it("stopped 收口为 suspended，不误判成失败", () => {
    const chat = chatWithParentCard();

    combineData(
      lifecycleEvent("subagent_end", {
        parentToolCallId: "call-1",
        status: "stopped",
      }),
      chat
    );

    expect(parentCard(chat).resultMap?.subAgentPhase).toBe("suspended");
  });

  it("摘要帧不新建时间线卡片", () => {
    const chat = chatWithParentCard();

    combineData(
      lifecycleEvent("subagent_start", {
        childSessionId: "sub_agent-1",
        parentToolCallId: "call-1",
      }),
      chat
    );
    combineData(
      lifecycleEvent("subagent_end", {
        childSessionId: "sub_agent-1",
        parentToolCallId: "call-1",
        status: "completed",
      }),
      chat
    );

    expect(chat.multiAgent.tasks[0]).toHaveLength(1);
  });

  it("父卡不存在时挂 pending，父卡晚到后自动回灌", () => {
    const chat = chatWithParentCard();
    chat.multiAgent.tasks = [[]];

    combineData(
      lifecycleEvent("subagent_start", {
        childSessionId: "sub_agent-1",
        parentToolCallId: "call-1",
        subAgentId: "agent-1",
      }),
      chat
    );
    expect(chat.multiAgent.tasks.flat()).toHaveLength(0);

    // 父卡随后到达：走 tool_call 分支，pending 桶按 toolCallId 回灌
    combineData(
      {
        messageOrder: 1,
        messageType: "task",
        messageId: "call-1",
        taskId: "t1",
        taskOrder: 1,
        resultMap: {
          messageType: "tool_call",
          toolCallId: "call-1",
          toolName: "Agent",
          status: "running",
          input: { description: "探索代码库" },
        } as unknown as MESSAGE.Task,
      } as unknown as MESSAGE.EventData,
      chat
    );

    const card = parentCard(chat);
    expect(card.resultMap?.childSessionId).toBe("sub_agent-1");
    expect(card.resultMap?.subAgentPhase).toBe("working");
  });

  it("父卡上的 childSessionId 能被详情入口解析到", () => {
    const chat = chatWithParentCard();

    combineData(
      lifecycleEvent("subagent_start", {
        childSessionId: "sub_agent-1",
        parentToolCallId: "call-1",
      }),
      chat
    );

    expect(resolveSubAgentDisplay(parentCard(chat) as CHAT.Task).childSessionId).toBe(
      "sub_agent-1"
    );
  });
});
