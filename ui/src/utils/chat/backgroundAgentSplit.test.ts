import { describe, expect, it } from "vitest";
import { combineData, handleTaskData } from "../chat";
import { projectDockTasks, projectTurnBlocks } from "./agentRuntimeProjector";
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
  it("puts run_in_background agents into dock projection", () => {
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

    const dock = projectDockTasks(chat);
    expect(dock).toHaveLength(1);
    expect(dock[0].id).toBe("tc-bg");
    expect(dock[0].kind).toBe("subagent");
    expect(dock[0].runInBackground).toBe(true);
  });

  it("keeps foreground agents out of dock", () => {
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

    expect(projectDockTasks(chat)).toHaveLength(0);
    const blocks = projectTurnBlocks(chat);
    expect(blocks.some((b) => b.kind === "tool" && b.tool.id === "tc-fg")).toBe(
      true
    );
  });
});
