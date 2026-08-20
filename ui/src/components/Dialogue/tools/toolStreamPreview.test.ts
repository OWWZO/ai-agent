import { describe, expect, it } from "vitest";
import {
  formatToolStreamChip,
  isToolArgStreaming,
  resolveToolStreamPreview,
  shouldShowToolArgStream,
} from "./toolStreamPreview";

describe("toolStreamPreview", () => {
  it("detects streaming via status or argsStreaming flag", () => {
    expect(
      isToolArgStreaming({
        messageType: "tool_call",
        resultMap: { status: "streaming" },
      } as CHAT.Task)
    ).toBe(true);
    expect(
      isToolArgStreaming({
        messageType: "tool_call",
        resultMap: { status: "running" },
      } as CHAT.Task)
    ).toBe(false);
    expect(
      isToolArgStreaming({
        messageType: "tool_call",
        resultMap: { status: "running", argsStreaming: true },
      } as CHAT.Task)
    ).toBe(true);
  });

  it("shows arg panel while running with args and no output", () => {
    expect(
      shouldShowToolArgStream({
        messageType: "tool_call",
        resultMap: {
          status: "running",
          argumentsRaw: '{"code":"print(1)"}',
        },
      } as CHAT.Task)
    ).toBe(true);
    expect(
      shouldShowToolArgStream({
        messageType: "tool_call",
        resultMap: {
          status: "success",
          argumentsRaw: '{"code":"print(1)"}',
          isFinal: true,
        },
      } as CHAT.Task)
    ).toBe(false);
  });

  it("streams write content from incomplete JSON", () => {
    const raw =
      '{"path":"demo.py","content":"print(1)\\nprint(2)\\nfor i in range(3):\\n    print(i';
    const preview = resolveToolStreamPreview("write", raw);
    expect(preview.header).toBe("demo.py");
    expect(preview.hasStructuredBody).toBe(true);
    expect(preview.body).toContain("print(1)");
    expect(preview.body).toContain("for i in range(3)");
  });

  it("streams edit new_string body", () => {
    const raw = '{"path":"a.ts","old_string":"x","new_string":"const a = 1;\\nconst b = 2';
    const preview = resolveToolStreamPreview("edit", raw);
    expect(preview.header).toBe("a.ts");
    expect(preview.body).toContain("const a = 1");
  });

  it("streams bash/code body", () => {
    const bash = resolveToolStreamPreview(
      "bash",
      '{"command":"npm run build -- --watch'
    );
    expect(bash.body).toContain("npm run build");

    const code = resolveToolStreamPreview(
      "code_execute",
      '{"code":"def f():\\n    return 1'
    );
    expect(code.body).toContain("def f()");
  });

  it("falls back to raw JSON when body field not started", () => {
    const preview = resolveToolStreamPreview("write", '{"path":"only.ts"');
    expect(preview.header).toBe("only.ts");
    expect(preview.hasStructuredBody).toBe(false);
    expect(preview.body).toContain('"path"');
  });

  it("formats stream chip size", () => {
    expect(formatToolStreamChip("", "")).toBe("生成参数…");
    expect(formatToolStreamChip("a".repeat(120), "")).toBe("120 chars");
    expect(formatToolStreamChip("a".repeat(2048), "")).toBe("2.0 KB");
  });
});
