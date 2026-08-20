import { describe, expect, it } from "vitest";
import { combineData, handleTaskData } from "../chat";
import {
  projectAgentMember,
  projectDockTasks,
  projectTurnBlocks,
  turnBlockSignature,
} from "./agentRuntimeProjector";
import { isRunInBackgroundAgent } from "./subagent";

function emptyChat(partial?: Partial<CHAT.ChatItem>): CHAT.ChatItem {
  return {
    sessionId: "s1",
    requestId: "r1",
    query: "q",
    files: [],
    forceStop: false,
    loading: false,
    multiAgent: { tasks: [] },
    tasks: [],
    ...partial,
  } as CHAT.ChatItem;
}

function taskEvent(
  messageType: string,
  resultMap: Record<string, unknown>,
  extras?: Partial<MESSAGE.EventData>
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
    ...extras,
  } as MESSAGE.EventData;
}

describe("agentRuntimeProjector", () => {
  it("keeps think → tool → text order from timeline projection", () => {
    const chat = emptyChat({
      tasks: [
        [
          {
            messageType: "task",
            task: "step",
            children: [
              {
                id: "think-1",
                messageId: "think-1",
                messageType: "llm_reasoning",
                toolThought: "先分析需求",
                resultMap: { isFinal: true },
              } as CHAT.Task,
              {
                id: "tool-1",
                messageId: "tool-1",
                messageType: "tool_result",
                toolResult: {
                  toolName: "Bash",
                  toolResult: "ok\nline2",
                  toolCallId: "tc-bash",
                  toolParam: { command: "ls" },
                },
                resultMap: {
                  toolName: "Bash",
                  toolCallId: "tc-bash",
                  status: "success",
                  isFinal: true,
                  input: { command: "ls" },
                },
              } as CHAT.Task,
              {
                id: "text-1",
                messageId: "text-1",
                messageType: "tool_thought",
                toolThought: "目录如下",
                resultMap: { isFinal: true },
              } as CHAT.Task,
            ],
          } as CHAT.Task,
        ],
      ],
    });

    const blocks = projectTurnBlocks(chat);
    expect(blocks.map((b) => b.kind)).toEqual(["thinking", "tool", "text"]);
    expect(turnBlockSignature(blocks)).toEqual([
      "thinking:5",
      "tool:Bash:tc-bash:ok",
      "text:4",
    ]);
  });

  it("projects Agent member with progress fields and background flag", () => {
    const agentTask = {
      id: "agent-1",
      messageId: "agent-1",
      messageType: "tool_call",
      resultMap: {
        toolName: "Agent",
        toolCallId: "tc-agent",
        status: "running",
        isFinal: false,
        input: {
          description: "探索 API",
          prompt: "find endpoints",
          subagent_type: "Explore",
          run_in_background: true,
        },
        subAgentLiveText: "scanning…",
        subAgentProgressLines: ["running · Explore · 探索 API · 3s"],
        subAgentElapsedMs: 3000,
        subAgentPhase: "working",
      },
    } as unknown as CHAT.Task;

    expect(isRunInBackgroundAgent(agentTask)).toBe(true);
    const member = projectAgentMember(agentTask);
    expect(member).toMatchObject({
      toolCallId: "tc-agent",
      subagentType: "Explore",
      status: "running",
      phase: "working",
      text: "scanning…",
      runInBackground: true,
      elapsedMs: 3000,
    });
    expect(member?.outputLines?.[0]).toContain("Explore");

    const chat = emptyChat({
      multiAgent: { tasks: [[agentTask]] },
      tasks: [
        [
          {
            messageType: "task",
            children: [agentTask],
          } as CHAT.Task,
        ],
      ],
    });
    const dock = projectDockTasks(chat);
    expect(dock).toHaveLength(1);
    expect(dock[0].kind).toBe("subagent");
    expect(dock[0].state).toBe("run");
  });

  it("combineData folds subagent_progress onto parent Agent card", () => {
    let chat = emptyChat();
    chat = combineData(
      taskEvent("tool_call", {
        toolName: "Agent",
        toolCallId: "tc-parent",
        messageId: "tc-parent",
        status: "running",
        input: {
          description: "探前端",
          prompt: "scan ui",
          subagent_type: "Explore",
        },
      }),
      chat
    );

    chat = combineData(
      taskEvent("subagent_progress", {
        messageId: "prog-1",
        kind: "heartbeat",
        phase: "working",
        status: "running",
        agentId: "sub-1",
        agentType: "Explore",
        description: "探前端",
        elapsedMs: 5000,
        parentToolUseId: "tc-parent",
      }),
      chat
    );

    chat = combineData(
      taskEvent("subagent_progress", {
        messageId: "prog-2",
        kind: "line",
        line: "Calling Read · src/App.tsx",
        parentToolUseId: "tc-parent",
      }),
      chat
    );

    const parent = chat.multiAgent.tasks[0].find((t) => {
      const id =
        t.resultMap?.toolCallId ||
        (t as { toolCallId?: string }).toolCallId ||
        t.messageId;
      return id === "tc-parent";
    });
    expect(parent?.resultMap?.subAgentElapsedMs).toBe(5000);
    expect(parent?.resultMap?.subAgentProgressLines).toEqual(
      expect.arrayContaining([
        expect.stringContaining("running ·"),
        "Calling Read · src/App.tsx",
      ])
    );

    // 不进事实时间线新条目：仍只有一条 Agent tool_call
    expect(chat.multiAgent.tasks[0]).toHaveLength(1);

    handleTaskData(chat, true, chat.multiAgent);
    const rendered = (chat.tasks[0][0].children || []).find((t) => {
      const id =
        t.resultMap?.toolCallId ||
        (t as { toolCallId?: string }).toolCallId ||
        t.messageId;
      return id === "tc-parent";
    }) as CHAT.Task;
    const member = projectAgentMember(rendered);
    expect(member?.outputLines?.some((l) => l.includes("Calling Read"))).toBe(
      true
    );
  });

  it("live and history share the same turn block signature", () => {
    const events = [
      taskEvent("llm_reasoning", {
        messageId: "r1",
        reasoningContent: "思考中",
        isFinal: true,
        finish: true,
      }),
      taskEvent("tool_call", {
        toolName: "Read",
        toolCallId: "tc-read",
        messageId: "tc-read",
        status: "running",
        input: { path: "a.ts" },
      }),
      taskEvent(
        "tool_result",
        {
          messageId: "tc-read-result",
          isFinal: true,
          finish: true,
        },
        {
          resultMap: {
            messageType: "tool_result",
            messageId: "tc-read",
            messageTime: "2026-01-01T00:00:01Z",
            requestId: "r1",
            finish: true,
            isFinal: true,
            id: "tc-read",
            toolResult: {
              toolName: "Read",
              toolCallId: "tc-read",
              toolResult: "export const a = 1",
              toolParam: { path: "a.ts" },
            },
          } as unknown as MESSAGE.Task,
        }
      ),
      taskEvent("tool_thought", {
        messageId: "tt1",
        toolThought: "已读取",
        isFinal: true,
        finish: true,
      }),
    ];

    let live = emptyChat();
    for (const event of events) {
      live = combineData(event, live);
    }
    handleTaskData(live, true, live.multiAgent);
    const liveSig = turnBlockSignature(projectTurnBlocks(live));

    let history = emptyChat();
    for (const event of events) {
      history = combineData(event, history);
    }
    handleTaskData(history, true, history.multiAgent);
    const historySig = turnBlockSignature(projectTurnBlocks(history));

    expect(historySig).toEqual(liveSig);
    expect(liveSig[0]?.startsWith("thinking:")).toBe(true);
    expect(liveSig.some((s) => s.startsWith("tool:"))).toBe(true);
    expect(liveSig[liveSig.length - 1]?.startsWith("text:")).toBe(true);
  });
});
