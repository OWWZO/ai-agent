import { describe, expect, it } from "vitest";
import {
  findBestAgentTask,
  identityKeys,
  identityRank,
  isDistinctToolCallId,
  pickBestTaskByKey,
  readTaskIdentity,
} from "./taskIdentity";

function agentTask(overrides: Partial<CHAT.Task> & { resultMap?: MESSAGE.ResultMap }): CHAT.Task {
  return {
    messageType: "tool_call",
    ...overrides,
    resultMap: {
      toolName: "Agent",
      ...(overrides.resultMap || {}),
    },
  } as CHAT.Task;
}

describe("taskIdentity", () => {
  it("ranks toolCallId above streamToolKey, messageId and id", () => {
    const identity = readTaskIdentity({
      id: "id-1",
      messageId: "msg-1",
      resultMap: {
        toolCallId: "call-1",
        streamToolKey: "stream-1",
        messageId: "nested-msg",
      },
    });
    expect(identityKeys(identity)).toEqual([
      "call-1",
      "stream-1",
      "msg-1",
      "id-1",
    ]);
    expect(identityRank(identity, "call-1")).toBe(4);
    expect(identityRank(identity, "stream-1")).toBe(3);
    expect(identityRank(identity, "msg-1")).toBe(2);
    expect(identityRank(identity, "id-1")).toBe(1);
    expect(identityRank(identity, "missing")).toBe(0);
  });

  it("does not treat fallback streamToolKey as a parent alias", () => {
    const identity = readTaskIdentity({
      messageId: "msg-1",
      resultMap: { toolCallId: "call-1" },
    });
    expect(identity.streamToolKey).toBe("");
    expect(identityRank(identity, "call-1")).toBe(4);
    expect(identityRank(identity, "msg-1")).toBe(2);
  });

  it("weak messageId alias does not steal a stronger toolCallId match", () => {
    const agentA = agentTask({
      messageId: "agent-b",
      resultMap: { toolName: "Agent", toolCallId: "agent-a" },
    });
    const agentB = agentTask({
      messageId: "agent-b-real",
      resultMap: { toolName: "Agent", toolCallId: "agent-b" },
    });
    const winner = pickBestTaskByKey([agentA, agentB], "agent-b");
    expect(winner?.resultMap?.toolCallId).toBe("agent-b");
  });

  it("findBestAgentTask prefers toolCallId over another Agent's messageId", () => {
    const chat = {
      sessionId: "s",
      requestId: "r",
      query: "",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: {
        tasks: [
          [
            agentTask({
              messageId: "agent-b",
              resultMap: { toolName: "Agent", toolCallId: "agent-a" },
            }),
            agentTask({
              messageId: "agent-b-real",
              resultMap: { toolName: "Agent", toolCallId: "agent-b" },
            }),
          ],
        ],
      },
    } as CHAT.ChatItem;
    expect(findBestAgentTask(chat, "agent-b")?.resultMap?.toolCallId).toBe(
      "agent-b"
    );
  });

  it("allows stream placeholder upgrade but not merging two real toolCallIds", () => {
    expect(
      isDistinctToolCallId("stream-1", "real-1", "stream-1", "stream-1")
    ).toBe(false);
    expect(
      isDistinctToolCallId("real-a", "real-b", "stream-1", "stream-1")
    ).toBe(true);
    expect(isDistinctToolCallId("real-a", "real-a", "s1", "s2")).toBe(false);
  });
});
