import { describe, expect, it } from "vitest";
import { buildToolPreviewSource } from "./toolPreviewSource";

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

describe("buildToolPreviewSource", () => {
  it("edit 有 patch → patch source，且 path 用真实路径", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "Edit",
          input: {
            path: "src/app/main.ts",
            old_string: "const a = 1;",
            new_string: "const a = 2;",
          },
        },
      })
    );

    expect(view).not.toBeNull();
    expect(view!.source.type).toBe("patch");
    expect(view!.source.path).toBe("src/app/main.ts");
    expect(view!.source.title).toBe("main.ts");
    expect(view!.path).toBe("src/app/main.ts");
  });

  it("write 无 patch → 展示入参里写入的正文（text source）", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "Write",
          input: {
            path: "src/new.ts",
            content: "hello"
          },
        },
      })
    );

    expect(view!.source.type).toBe("text");
    expect(view!.source.path).toBe("src/new.ts");
    expect(view!.source.title).toBe("new.ts");
    expect((view!.source as { content: string }).content).toBe("hello");
  });

  it("write 连 content 都没有 → 退化为 file source", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "Write",
          input: { path: "src/new.ts" }
        },
      })
    );

    expect(view!.source.type).toBe("file");
    expect(view!.source.path).toBe("src/new.ts");
  });

  it("read → file source（不是 patch），并带行号视图所需的 path", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "Read",
          input: { path: "src/app/main.ts" },
          toolResult: "1\tconst a = 1;",
        },
      })
    );

    expect(view!.source.type).toBe("file");
    expect(view!.source.path).toBe("src/app/main.ts");
    expect(view!.source.title).toBe("main.ts");
  });

  it("read 命中会话产物 → 给出可读 url，无降级原因", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "Read",
          input: { path: "/workspace/src/app/main.ts" },
        },
      }),
      undefined,
      [
        {
          name: "main.ts",
          relativePath: "src/app/main.ts",
          previewUrl: "https://files.test/preview/main.ts",
        },
      ]
    );

    expect(view!.fileUrl).toBe("https://files.test/preview/main.ts");
    expect(view!.unavailableReason).toBeUndefined();
  });

  it("read 未命中产物 → 给出降级原因（决策点 B：前端读不到任意绝对路径）", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "Read",
          input: { path: "D:/other/place/foo.ts" },
        },
      }),
      undefined,
      []
    );

    expect(view!.fileUrl).toBeUndefined();
    expect(view!.unavailableReason).toContain("该文件不在会话产物中，无法预览");
    expect(view!.unavailableReason).toContain("D:/other/place/foo.ts");
  });

  it("多文件 edit 按 fileIndex 各开各的 patch", () => {
    const task = toolTask({
      resultMap: {
        toolName: "MultiEdit",
        input: {
          files: [
            {
              path: "src/a.ts",
              old_string: "a",
              new_string: "a!"
            },
            {
              path: "src/b.ts",
              old_string: "b",
              new_string: "b!"
            },
          ],
        },
      },
    });

    const first = buildToolPreviewSource(task, 0);
    const second = buildToolPreviewSource(task, 1);

    expect(first!.source.type).toBe("patch");
    expect(second!.source.type).toBe("patch");
    expect(first!.source.path).toBe("src/a.ts");
    expect(second!.source.path).toBe("src/b.ts");
    expect(first!.source.title).toBe("a.ts");
    expect(second!.source.title).toBe("b.ts");
  });

  it("保留工具输出与状态，供面板做 patch 之外的兜底", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "Edit",
          status: "failed",
          input: { path: "src/a.ts" },
          errorMsg: "boom",
        },
      })
    );

    expect(view!.status).toBe("error");
    expect(view!.output?.join("\n")).toContain("boom");
  });
});

describe("workspace_read 结果自带正文（绕开产物 catalog）", () => {
  /** 后端 WorkspaceReadTool 的真实返回：{type:"text", path, startLine, content, ...} */
  const readResult = (path: string, content: string) =>
    JSON.stringify({
      type: "text",
      path,
      startLine: 1,
      endLine: 3,
      numLines: 3,
      totalLines: 3,
      content
    });

  it("结果里有 content → 产出 text source，不依赖产物清单", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "workspace_read",
          input: { path: "src/a.ts" },
          toolResult: readResult("src/a.ts", "const a = 1;\nconst b = 2;")
        },
      }),
      undefined,
      [] // 关键：产物清单为空也照样能预览
    );

    expect(view!.source.type).toBe("text");
    expect(view!.source.path).toBe("src/a.ts");
    expect(view!.source.title).toBe("a.ts");
    expect((view!.source as { content: string }).content).toContain("const a = 1;");
    expect(view!.unavailableReason).toBeUndefined();
  });

  it("结果路径优先于入参路径（后端回 agent 可见路径）", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "workspace_read",
          input: { path: "/abs/weird/a.ts" },
          toolResult: readResult("skills/demo/a.ts", "x")
        },
      })
    );
    expect(view!.source.path).toBe("skills/demo/a.ts");
  });

  it("file_unchanged 占位结果 → 回退到产物 URL 链路并给降级原因", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "workspace_read",
          input: { path: "src/a.ts" },
          toolResult: JSON.stringify({
            type: "file_unchanged",
            unchanged: true,
            path: "src/a.ts",
            startLine: 1,
            lineCount: 100
          })
        },
      }),
      undefined,
      []
    );

    expect(view!.source.type).toBe("file");
    expect(view!.unavailableReason).toContain("该文件不在会话产物中");
  });

  it("结果里带 content 时语言按扩展名推断", () => {
    const view = buildToolPreviewSource(
      toolTask({
        resultMap: {
          toolName: "workspace_read",
          input: { path: "a.py" },
          toolResult: readResult("a.py", "print(1)")
        },
      })
    );
    expect((view!.source as { language: string }).language).toBe("python");
  });
});
