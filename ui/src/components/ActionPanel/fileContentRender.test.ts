import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { getPrimaryTaskFile, getPrimaryTaskFileName } from "@/utils/taskArtifacts";
import { useMsgTypes } from "./useMsgTypes";
import { resolvePanelView } from "./panelResolver";

describe("ActionPanel file content rendering", () => {
  const buildFileTask = (overrides?: Partial<MESSAGE.Task>): MESSAGE.Task => ({
    taskId: "task-file-001",
    messageTime: "1710000000000",
    messageType: "file",
    requestId: "req-file-001",
    messageId: "msg-file-001",
    finish: true,
    isFinal: true,
    id: "file-task-001",
    resultMap: {
      command: "读取文件",
      primaryFileName: "风险日报.md",
      previewUrl: "https://example.com/risk.md",
      downloadUrl: "https://example.com/risk-download.md",
      fileInfo: [
        {
          fileName: "风险日报.md",
          ossUrl: "https://example.com/risk.md",
          domainUrl: "https://example.com/risk-preview.md",
          fileSize: 128,
        },
      ],
    },
    ...overrides,
  });

  it("should resolve file get task from preview url", () => {
    const task = buildFileTask();
    const primaryFile = getPrimaryTaskFile(task as unknown as any);

    expect(primaryFile?.name).toBe("风险日报.md");
    expect(primaryFile?.url).toBe("https://example.com/risk-preview.md");
    expect(primaryFile?.downloadUrl).toBe("https://example.com/risk-download.md");
  });

  it("should synthesize file when replay payload only keeps preview url and file name", () => {
    const task = buildFileTask({
      resultMap: {
        command: "读取文件",
        primaryFileName: "日报汇总.md",
        previewUrl: "https://example.com/preview-only.md",
        downloadUrl: "https://example.com/download-only.md",
      },
    });
    const primaryFile = getPrimaryTaskFile(task as unknown as any);

    expect(primaryFile?.name).toBe("日报汇总.md");
    expect(primaryFile?.url).toBe("https://example.com/preview-only.md");
    expect(primaryFile?.downloadUrl).toBe("https://example.com/download-only.md");
    expect(getPrimaryTaskFileName(task as unknown as any)).toBe("日报汇总.md");
  });

  it("should classify file get task as file renderer", () => {
    const task = buildFileTask();
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };

    renderToStaticMarkup(createElement(HookProbe));

    expect(msgTypes?.useFile).toBe(true);
  });

  it("should resolve file get task to file panel view", () => {
    const task = buildFileTask();
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };

    renderToStaticMarkup(createElement(HookProbe));

    const panelView = resolvePanelView({
      taskItem: task as unknown as any,
      msgTypes,
      markDownContent: "",
      primaryFile: getPrimaryTaskFile(task as unknown as any),
    });

    expect(panelView.type).toBe("file");
    if (panelView.type === "file") {
      expect(panelView.fileName).toBe("风险日报.md");
    }
  });

  it("should resolve the final deep search report to a markdown panel", () => {
    const task = {
      messageType: "deep_search",
      resultMap: {
        messageType: "report",
        answer: "最终章节合并总结",
        searchResult: {
          query: [],
          docs: [],
        },
      },
    } as unknown as MESSAGE.Task;

    const panelView = resolvePanelView({
      taskItem: task as unknown as any,
      markDownContent: "最终章节合并总结",
    });

    expect(panelView).toMatchObject({
      type: "markdown",
      content: "最终章节合并总结",
    });
  });

  it("should hide nested tool result JSON instead of Structured data panel", () => {
    const task = {
      messageType: "tool_result",
      resultMap: {
        messageType: "tool_result",
        resultMap: {
          toolResult: {
            toolName: "workspace_grep",
            toolResult: JSON.stringify({ matches: ["a.ts", "b.ts"] }),
          },
        },
      },
    } as unknown as MESSAGE.Task;
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };
    renderToStaticMarkup(createElement(HookProbe));

    expect(msgTypes?.useJSON).toBe(true);
    expect(
      resolvePanelView({
        taskItem: task as unknown as any,
        msgTypes,
        markDownContent: "",
      })
    ).toMatchObject({
      type: "empty",
    });
  });

  it("should map WebSearch hit payloads to the workspace search panel", () => {
    const task = {
      messageType: "tool_result",
      isFinal: true,
      toolResult: {
        toolName: "WebSearch",
        toolResult: JSON.stringify({
          hits: [
            {
              title: "来源 A",
              url: "https://example.com/a",
              snippet: "摘要 A",
            },
          ],
        }),
      },
      resultMap: {},
    } as unknown as MESSAGE.Task;
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };
    renderToStaticMarkup(createElement(HookProbe));

    expect(msgTypes?.searchList).toEqual([
      {
        name: "来源 A",
        pageContent: "摘要 A",
        url: "https://example.com/a",
      },
    ]);
    expect(
      resolvePanelView({
        taskItem: task as unknown as any,
        msgTypes,
        markDownContent: "",
      }).type
    ).toBe("search");
  });

  it("should hide empty tool results instead of Structured data panel", () => {
    const task = {
      messageType: "tool_result",
      resultMap: {
        toolResult: {
          toolName: "workspace_grep",
          toolResult: "",
        },
      },
    } as unknown as MESSAGE.Task;
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };
    renderToStaticMarkup(createElement(HookProbe));

    expect(
      resolvePanelView({
        taskItem: task as unknown as any,
        msgTypes,
        markDownContent: "",
      })
    ).toMatchObject({
      type: "empty",
    });
  });

  it("should use the task-level final flag for replayed plain-text results", () => {
    const task = {
      messageType: "tool_result",
      isFinal: true,
      finish: true,
      toolResult: {
        toolName: "workspace_read",
        toolResult: "file contents",
      },
      resultMap: { parentToolUseId: "parent-1" },
    } as unknown as MESSAGE.Task;

    const panelView = resolvePanelView({
      taskItem: task as unknown as any,
      msgTypes: {},
      markDownContent: "file contents",
      isFinal: true,
    });

    expect(panelView).toEqual({
      type: "markdown",
      content: "file contents",
      isStreaming: false,
    });
  });

  it("should preserve an empty deep search result panel", () => {
    const task = {
      messageType: "deep_search",
      resultMap: {
        messageType: "search",
        searchResult: {
          query: ["empty query"],
          docs: [],
        },
      },
    } as unknown as MESSAGE.Task;

    expect(
      resolvePanelView({
        taskItem: task as unknown as any,
        msgTypes: { searchList: [] },
        markDownContent: "",
      })
    ).toEqual({
      type: "search",
      searchList: [],
    });
  });

  const buildBinaryFileTask = (
    fileName: string,
    overrides?: Partial<MESSAGE.Task>
  ): MESSAGE.Task =>
    buildFileTask({
      resultMap: {
        command: "读取文件",
        primaryFileName: fileName,
        previewUrl: `https://example.com/${encodeURIComponent(fileName)}`,
        downloadUrl: `https://example.com/dl/${encodeURIComponent(fileName)}`,
        fileInfo: [
          {
            fileName,
            ossUrl: `https://example.com/${encodeURIComponent(fileName)}`,
            domainUrl: `https://example.com/${encodeURIComponent(fileName)}`,
            fileSize: 1024,
          },
        ],
      },
      ...overrides,
    });

  it("should classify pdf as pdf renderer not text file", () => {
    const task = buildBinaryFileTask("报告.pdf");
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };
    renderToStaticMarkup(createElement(HookProbe));

    expect(msgTypes?.usePdf).toBe(true);
    expect(msgTypes?.useFile).toBe(false);

    const panelView = resolvePanelView({
      taskItem: task as unknown as any,
      msgTypes,
      markDownContent: "",
      primaryFile: getPrimaryTaskFile(task as unknown as any),
    });
    expect(panelView.type).toBe("pdf");
  });

  it("should classify docx as docx renderer", () => {
    const task = buildBinaryFileTask("方案.docx");
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };
    renderToStaticMarkup(createElement(HookProbe));

    expect(msgTypes?.useDocx).toBe(true);
    expect(msgTypes?.useWord).toBe(true);
    expect(msgTypes?.useFile).toBe(false);

    const panelView = resolvePanelView({
      taskItem: task as unknown as any,
      msgTypes,
      markDownContent: "",
      primaryFile: getPrimaryTaskFile(task as unknown as any),
    });
    expect(panelView.type).toBe("docx");
  });

  it("should classify legacy doc as download-only panel", () => {
    const task = buildBinaryFileTask("旧稿.doc");
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };
    renderToStaticMarkup(createElement(HookProbe));

    expect(msgTypes?.useLegacyDoc).toBe(true);
    expect(msgTypes?.useDocx).toBe(false);
    expect(msgTypes?.useFile).toBe(false);

    const panelView = resolvePanelView({
      taskItem: task as unknown as any,
      msgTypes,
      markDownContent: "",
      primaryFile: getPrimaryTaskFile(task as unknown as any),
    });
    expect(panelView.type).toBe("legacy-doc");
  });

  it("should classify ppt as download-only not html", () => {
    const task = buildBinaryFileTask("路演.pptx");
    let msgTypes: ReturnType<typeof useMsgTypes> | undefined;

    const HookProbe = () => {
      msgTypes = useMsgTypes(task as unknown as any);
      return null;
    };
    renderToStaticMarkup(createElement(HookProbe));

    expect(msgTypes?.usePpt).toBe(true);
    expect(msgTypes?.useHtml).toBe(false);
    expect(msgTypes?.useFile).toBe(false);

    const panelView = resolvePanelView({
      taskItem: task as unknown as any,
      msgTypes,
      markDownContent: "",
      primaryFile: getPrimaryTaskFile(task as unknown as any),
    });
    expect(panelView.type).toBe("download-only");
  });
});
