import { describe, expect, it } from "vitest";
import {
  mergeHtmlPreviewIntoToolCall,
  resolveTaskResultMap,
  resolveTaskToolCallId,
  resolveTaskToolResult,
  resolveTaskToolResultText,
} from "./toolCalls";

describe("tool result resolution", () => {
  it("reads a tool result from nested resultMap layers", () => {
    const task = {
      messageType: "tool_result",
      resultMap: {
        messageType: "tool_result",
        resultMap: {
          toolResult: {
            toolName: "workspace_grep",
            toolCallId: "child-call-1",
            toolParam: { pattern: "Controller" },
            toolResult: "2 matches",
          },
        },
      },
    } as unknown as CHAT.Task;

    expect(resolveTaskToolResult(task)).toEqual({
      toolName: "workspace_grep",
      toolCallId: "child-call-1",
      toolParam: { pattern: "Controller" },
      toolResult: "2 matches",
    });
    expect(resolveTaskToolResultText(task)).toBe("2 matches");
    expect(resolveTaskToolCallId(task)).toBe("child-call-1");
    expect(resolveTaskResultMap(task).messageType).toBe("tool_result");
  });

  it("keeps structured result objects readable as JSON text", () => {
    const task = {
      messageType: "tool_result",
      resultMap: {
        toolResult: {
          toolName: "workspace_read",
          toolResult: {
            content: "hello",
            lines: 1,
          },
        },
      },
    } as unknown as CHAT.Task;

    expect(resolveTaskToolResultText(task)).toBe(
      JSON.stringify({
        content: "hello",
        lines: 1,
      })
    );
  });

  it("falls back to result fields when a tool result wrapper is absent", () => {
    const task = {
      messageType: "tool_result",
      resultMap: {
        toolName: "workspace_read",
        data: "file contents",
      },
    } as unknown as CHAT.Task;

    expect(resolveTaskToolResultText(task)).toBe("file contents");
  });
});

describe("mergeHtmlPreviewIntoToolCall", () => {
  it("copies html fileInfo onto the original canvas_publish tool card", () => {
    const toolTask = {
      messageType: "tool_call",
      messageId: "call-1",
      resultMap: {
        messageType: "tool_call",
        toolName: "canvas_publish",
        toolCallId: "call-1",
        status: "running",
      },
    } as unknown as MESSAGE.Task;
    const htmlTask = {
      messageType: "html",
      messageId: "call-1",
      resultMap: {
        messageType: "html",
        resultMap: {
          toolCallId: "call-1",
          fileInfo: [
            {
              fileName: "page.html",
              relativePath: "page.html",
              domainUrl: "http://x/preview/page.html",
              ossUrl: "http://x/download/page.html",
            },
          ],
        },
      },
    } as unknown as MESSAGE.Task;

    const merged = mergeHtmlPreviewIntoToolCall(toolTask, htmlTask);

    expect(merged.messageType).toBe("tool_call");
    expect(merged.resultMap?.toolName).toBe("canvas_publish");
    expect(merged.resultMap?.fileInfo?.[0]?.fileName).toBe("page.html");
    expect(merged.resultMap?.fileInfo?.[0]?.domainUrl).toBe(
      "http://x/preview/page.html"
    );
  });
});
