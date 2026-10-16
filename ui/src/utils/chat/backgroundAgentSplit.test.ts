import { describe, expect, it } from "vitest";
import { combineData, handleTaskData } from "../chat";
import { projectSubAgentItems, projectTurnBlocks } from "./agentRuntimeProjector";
import { isRunInBackgroundAgent } from "./subagent";

function emptyChat(): CHAT.ChatItem {
  return {
    sessionId: "s1",
    requestId: "r1",
    query: "q",
    files: [],
    forceStop: false,
    loading: false,
    multiAgent: { tasks: [] },
    tasks: [],
  } as CHAT.ChatItem;
}

function taskEvent(
  messageType: string,
  resultMap: Record<string, unknown>
): MESSAGE.EventData {
  return {
    messageOrder: 1,
    messageType: "task",
    messageId: String(resultMap.messageId || resultMap.toolCallId || "m1"),
    taskId: "t1",
    taskOrder: 1,
    resultMap: {
      messageType,
      messageTime: "2026-01-01T00:00:00Z",
      requestId: "r1",
      finish: false,
      isFinal: false,
      id: String(resultMap.messageId || resultMap.toolCallId || "m1"),
      ...resultMap,
    } as unknown as MESSAGE.Task,
  } as MESSAGE.EventData;
}

describe("foreground vs background agent split", () => {
  it("puts run_in_background agents into the roster", () => {
    let chat = emptyChat();
    chat = combineData(
      taskEvent("tool_call", {
        toolName: "Agent",
        toolCallId: "tc-bg",
        messageId: "tc-bg",
        status: "running",
        input: {
          description: "后台探索",
          prompt: "scan",
          subagent_type: "Explore",
          run_in_background: true,
        },
      }),
      chat
    );
    handleTaskData(chat, true, chat.multiAgent);

    const agent = chat.multiAgent.tasks[0][0] as CHAT.Task;
    expect(isRunInBackgroundAgent(agent)).toBe(true);

    const dock = projectSubAgentItems(chat);
    expect(dock).toHaveLength(1);
    expect(dock[0].id).toBe("tc-bg");
    expect(dock[0].kind).toBe("subagent");
    expect(dock[0].runInBackground).toBe(true);
    // 运行中实时计时需要 startedAtMs：从 resultMap.messageTime 兜底解析
    expect(dock[0].startedAtMs).toBe(
      Date.parse("2026-01-01T00:00:00Z")
    );
  });

  it("前台（同步）agent 同样进名册，只是 runInBackground=false", () => {
    // 名册是「全部子 Agent」：面板 / 目录要能看到同步派发的子 Agent，
    // runInBackground 只作为条目属性（面板据此决定是否实时计时），不再是过滤条件。
    let chat = emptyChat();
    chat = combineData(
      taskEvent("tool_call", {
        toolName: "Agent",
        toolCallId: "tc-fg",
        messageId: "tc-fg",
        status: "running",
        input: {
          description: "前台探索",
          prompt: "scan",
          subagent_type: "Explore",
        },
      }),
      chat
    );
    handleTaskData(chat, true, chat.multiAgent);

    const roster = projectSubAgentItems(chat);
    expect(roster).toHaveLength(1);
    expect(roster[0].id).toBe("tc-fg");
    expect(roster[0].kind).toBe("subagent");
    expect(roster[0].runInBackground).toBe(false);

    // 主时间线仍然保留这张卡（名册是附加视图，不替代时间线）
    const blocks = projectTurnBlocks(chat);
    expect(blocks.some((b) => b.kind === "tool" && b.tool.id === "tc-fg")).toBe(
      true
    );
  });

  it("puts resumed SendMessage background children into dock", () => {
    const sendTask = {
      id: "tc-sm",
      messageId: "tc-sm",
      messageType: "tool_result",
      resultMap: {
        toolName: "SendMessage",
        toolCallId: "tc-sm",
        status: "running",
        resumed: true,
        run_in_background: true,
        agentId: "agent-done",
        task_id: "task-2",
      },
    } as unknown as CHAT.Task;

    expect(isRunInBackgroundAgent(sendTask)).toBe(true);

    const chat = emptyChat();
    chat.multiAgent = { tasks: [[sendTask as unknown as MESSAGE.Task]] };
    chat.tasks = [
      [
        {
          messageType: "task",
          children: [sendTask],
        } as CHAT.Task,
      ],
    ];

    const dock = projectSubAgentItems(chat);
    expect(dock).toHaveLength(1);
    expect(dock[0].id).toBe("tc-sm");
    expect(dock[0].kind).toBe("subagent");
    expect(dock[0].runInBackground).toBe(true);
  });

  it("keeps inject-only SendMessage out of dock", () => {
    let chat = emptyChat();
    chat = combineData(
      taskEvent("tool_result", {
        toolName: "SendMessage",
        toolCallId: "tc-inject",
        messageId: "tc-inject",
        status: "success",
        ok: true,
        queued: 1,
        agentId: "agent-live",
        input: {
          to: "agent-live",
          message: "先列目录",
        },
      }),
      chat
    );
    handleTaskData(chat, true, chat.multiAgent);

    expect(projectSubAgentItems(chat)).toHaveLength(0);
  });
});
