import { describe, expect, it } from "vitest";
import {
  buildAgentProgressGroups,
  groupProgressLines,
  resolveAgentPhaseLabel,
  shouldFoldGroup,
} from "./agentProgressGroups";

describe("groupProgressLines", () => {
  it("groups Calling lines with following output", () => {
    const groups = groupProgressLines([
      "Calling Read · src/App.tsx",
      "export default function App()",
      "Calling Bash · ls",
      "a.ts",
      "b.ts",
    ]);
    expect(groups).toHaveLength(2);
    expect(groups[0]).toMatchObject({
      call: "Calling Read · src/App.tsx",
      output: ["export default function App()"],
    });
    expect(groups[1].call).toBe("Calling Bash · ls");
    expect(groups[1].output).toEqual(["a.ts", "b.ts"]);
  });

  it("keeps heartbeat lines without Calling as bare output groups", () => {
    const groups = groupProgressLines([
      "running · Explore · 探前端 · 3s",
      "Calling Grep · TODO",
      "hit",
    ]);
    expect(groups[0].call).toBe("");
    expect(groups[0].output[0]).toContain("running ·");
    expect(groups[1].call).toBe("Calling Grep · TODO");
  });
});

describe("buildAgentProgressGroups", () => {
  it("synthesizes Calling groups from nested children when no Calling lines", () => {
    const tool = {
      id: "agent-1",
      messageId: "agent-1",
      messageType: "tool_call",
      resultMap: {
        toolName: "Agent",
        toolCallId: "tc-agent",
        status: "running",
        input: {
          description: "探前端",
          prompt: "scan",
          subagent_type: "Explore",
        },
        subAgentProgressLines: ["running · Explore · 探前端 · 2s"],
      },
      children: [
        {
          id: "c1",
          messageId: "c1",
          messageType: "tool_result",
          toolResult: {
            toolName: "Read",
            toolCallId: "tc-read",
            toolResult: "line1\nline2",
            toolParam: { path: "ui/App.tsx" },
          },
          resultMap: {
            toolName: "Read",
            toolCallId: "tc-read",
            status: "success",
            isFinal: true,
            input: { path: "ui/App.tsx" },
          },
        },
      ],
    } as unknown as CHAT.Task;

    const groups = buildAgentProgressGroups(tool);
    expect(groups.some((g) => g.call.startsWith("Calling Read"))).toBe(true);
    const readGroup = groups.find((g) => g.call.startsWith("Calling Read"));
    expect(readGroup?.output).toEqual(["line1", "line2"]);
  });

  it("prefers explicit Calling progress lines over children synthesis", () => {
    const tool = {
      id: "agent-1",
      messageType: "tool_result",
      resultMap: {
        toolName: "Agent",
        toolCallId: "tc-agent",
        input: { prompt: "x", subagent_type: "Explore" },
        subAgentProgressLines: [
          "Calling Bash · echo hi",
          "hi",
        ],
      },
      children: [
        {
          messageType: "tool_result",
          resultMap: {
            toolName: "Read",
            input: { path: "a.ts" },
            status: "success",
            isFinal: true,
          },
          toolResult: {
            toolName: "Read",
            toolResult: "code",
            toolParam: { path: "a.ts" },
          },
        },
      ],
    } as unknown as CHAT.Task;

    const groups = buildAgentProgressGroups(tool);
    expect(groups).toHaveLength(1);
    expect(groups[0].call).toBe("Calling Bash · echo hi");
  });
});

describe("phase + fold helpers", () => {
  it("maps phase labels", () => {
    expect(resolveAgentPhaseLabel("working", "running")).toBe("Working");
    expect(resolveAgentPhaseLabel(undefined, "failed")).toBe("Failed");
    expect(resolveAgentPhaseLabel("completed", "completed")).toBe("Completed");
  });

  it("folds long output groups", () => {
    expect(
      shouldFoldGroup({
        key: "g",
        call: "Calling X",
        output: Array.from({ length: 9 }, (_, i) => `l${i}`),
      })
    ).toBe(true);
    expect(
      shouldFoldGroup({
        key: "g",
        call: "Calling X",
        output: ["a", "b"],
      })
    ).toBe(false);
  });
});
