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
});
