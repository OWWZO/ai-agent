import { describe, expect, it } from "vitest";
import { resolveToolRendererKind } from "./toolRegistry";
import { editDiffStats, formatEditDiffChip } from "./toolDiff";

function toolTask(
  partial: Partial<CHAT.Task> & { resultMap?: Record<string, unknown> }
): CHAT.Task {
  return {
    id: "t1",
    messageId: "t1",
    messageType: "tool_result",
    messageTime: "1",
    requestId: "r1",
    finish: true,
    isFinal: true,
    resultMap: {
      status: "success",
      isFinal: true,
      ...(partial.resultMap || {}),
    },
    ...partial,
  } as CHAT.Task;
}

describe("toolRegistry", () => {
  it("routes edit/write/multi_edit to edit renderer", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: { toolName: "Edit", input: { path: "a.ts" } },
        })
      )
    ).toBe("edit");
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: { toolName: "Write", input: { path: "b.ts" } },
        })
      )
    ).toBe("edit");
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: { toolName: "MultiEdit", input: { path: "c.ts" } },
        })
      )
    ).toBe("edit");
  });

  it("routes Agent / task aliases to agent renderer", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: {
            toolName: "Agent",
            input: { prompt: "x", subagent_type: "Explore" },
          },
        })
      )
    ).toBe("agent");
    expect(
      resolveToolRendererKind(
        toolTask({
          toolResult: {
            toolName: "Agent",
            toolResult: "status=completed\n\nok",
            toolParam: { prompt: "x" },
          },
        })
      )
    ).toBe("agent");
  });

  it("routes ask_user_question to askuser renderer", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          messageType: "ask_user_question",
          resultMap: { toolName: "AskUserQuestion" },
        })
      )
    ).toBe("askuser");
  });

  it("defaults to generic", () => {
    expect(
      resolveToolRendererKind(
        toolTask({
          resultMap: { toolName: "Bash", input: { command: "ls" } },
        })
      )
    ).toBe("generic");
  });
});

describe("editDiffStats chip", () => {
  it("computes +A −B from old_string/new_string", () => {
    const arg = JSON.stringify({
      path: "a.ts",
      old_string: "a\nb\nc",
      new_string: "a\nx\nc\nd",
    });
    const stats = editDiffStats("Edit", arg);
    expect(stats).toEqual({ added: 2, removed: 1 });
    expect(formatEditDiffChip("Edit", arg)).toBe("+2 −1");
  });

  it("counts write content as added lines", () => {
    const arg = JSON.stringify({
      path: "new.ts",
      content: "one\ntwo\nthree",
    });
    expect(formatEditDiffChip("Write", arg)).toBe("+3 −0");
  });
});
