import { describe, expect, it } from "vitest";
import {
  buildSubAgentAction,
  buildSubAgentMarkdown,
  formatSubAgentDuration,
  isAgentDispatchTask,
  parseAgentObservation,
  resolveSubAgentDisplay,
} from "./subagent";

describe("subagent display", () => {
  it("parses observation header and body", () => {
    const parsed = parseAgentObservation(
      [
        "status=completed",
        "agentType=Explore",
        "agentId=abc123",
        "totalToolUseCount=5",
        "totalDurationMs=1200",
        "",
        "找到 3 个 REST 端点。",
      ].join("\n")
    );

    expect(parsed.status).toBe("completed");
    expect(parsed.agentType).toBe("Explore");
    expect(parsed.agentId).toBe("abc123");
    expect(parsed.totalToolUseCount).toBe(5);
    expect(parsed.totalDurationMs).toBe(1200);
    expect(parsed.content).toContain("REST");
  });

  it("detects Agent tool tasks", () => {
    const task = {
      messageType: "tool_result",
      toolResult: {
        toolName: "Agent",
        toolParam: {
          description: "搜索 API",
          prompt: "find endpoints",
          subagent_type: "Explore",
        },
        toolResult: "status=completed\nagentType=Explore\n\nok",
      },
    } as unknown as CHAT.Task;

    expect(isAgentDispatchTask(task)).toBe(true);
    const display = resolveSubAgentDisplay(task);
    expect(display.subagentType).toBe("Explore");
    expect(display.description).toBe("搜索 API");
    expect(display.status).toBe("completed");
  });

  it("buildAction text for running and completed", () => {
    const running = {
      messageType: "tool_call",
      resultMap: {
        toolName: "Agent",
        status: "running",
        isFinal: false,
        input: {
          description: "探索前端",
          subagent_type: "Explore",
          prompt: "scan ui/",
        },
      },
    } as unknown as CHAT.Task;

    expect(buildSubAgentAction(running)).toMatchObject({
      action: "派发子智能体",
      tool: "Agent",
      name: "Explore · 探索前端",
    });

    const completed = {
      messageType: "tool_result",
      toolResult: {
        toolName: "Agent",
        toolParam: {
          description: "探索前端",
          subagent_type: "Explore",
          prompt: "scan ui/",
        },
        toolResult:
          "status=completed\nagentType=Explore\ntotalToolUseCount=3\ntotalDurationMs=2500\n\ndone",
      },
      resultMap: { status: "success", isFinal: true },
    } as unknown as CHAT.Task;

    expect(buildSubAgentAction(completed).action).toBe("子智能体完成");
    expect(buildSubAgentAction(completed).name).toContain("3 tools");
    expect(buildSubAgentMarkdown(completed)).toContain("done");
  });

  it("formats duration", () => {
    expect(formatSubAgentDuration(800)).toBe("800ms");
    expect(formatSubAgentDuration(1500)).toBe("1.5s");
    expect(formatSubAgentDuration(65_000)).toMatch(/1m/);
  });

  it("resolves parentToolUseId from nested resultMap", async () => {
    const { resolveParentToolUseId } = await import("./subagent");
    expect(
      resolveParentToolUseId({
        messageType: "tool_call",
        resultMap: {
          parentToolUseId: "parent-1",
          toolName: "workspace_grep",
        },
      } as unknown as CHAT.Task)
    ).toBe("parent-1");
    expect(
      resolveParentToolUseId({
        messageType: "tool_call",
        resultMap: {
          resultMap: { parentToolUseId: "parent-2", toolName: "deep_search" },
        },
      } as unknown as CHAT.Task)
    ).toBe("parent-2");
  });
});
