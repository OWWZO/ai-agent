import { describe, expect, it } from "vitest";

import {
  buildWorkspaceTree,
  collectSessionFileTasks,
  collectWorkspaceFiles,
  flattenTasksWithFiles,
} from "./workspaceFiles";

describe("workspaceFiles session aggregation", () => {
  it("flattens nested children file tasks", () => {
    const tasks = [
      {
        messageType: "task",
        children: [
          {
            messageType: "file",
            messageTime: "1",
            fileInfo: [
              {
                fileName: "report.md",
                domainUrl: "http://x/preview/s/report.md",
                ossUrl: "http://x/download/s/report.md",
              },
            ],
          },
        ],
      },
    ] as any;

    const flat = flattenTasksWithFiles(tasks);
    expect(flat).toHaveLength(1);
    expect(flat[0].messageType).toBe("file");
  });

  it("excludes file-like tasks that have no actual files", () => {
    const tasks = [
      {
        messageType: "tool_result",
        resultMap: { toolName: "Read" },
      },
      {
        messageType: "html",
        resultMap: { messageType: "html" },
      },
    ] as any;

    expect(flattenTasksWithFiles(tasks)).toEqual([]);
  });

  it("excludes canvas publish preview references from the workspace file tree", () => {
    const tasks = [
      {
        messageType: "html",
        resultMap: {
          messageType: "html",
          toolName: "canvas_publish",
          primaryFileName: "page.html",
          previewUrl: "http://x/preview/s/page.html",
          downloadUrl: "http://x/download/s/page.html",
        },
      },
    ] as any;

    expect(collectWorkspaceFiles(tasks)).toEqual([]);
  });

  it("aggregates files across chat turns and live taskList", () => {
    const chatList = [
      {
        tasks: [
          [
            {
              messageType: "file",
              messageTime: "1",
              fileInfo: [
                {
                  fileName: "old.md",
                  domainUrl: "http://x/preview/s/old.md",
                  ossUrl: "http://x/download/s/old.md",
                },
              ],
            },
          ],
        ],
        multiAgent: { tasks: [] },
      },
    ] as unknown as CHAT.ChatItem[];

    const live = [
      {
        messageType: "file",
        messageTime: "2",
        fileInfo: [
          {
            fileName: "new.md",
            domainUrl: "http://x/preview/s/new.md",
            ossUrl: "http://x/download/s/new.md",
          },
        ],
      },
    ] as any;

    const sessionTasks = collectSessionFileTasks(chatList, live);
    const files = collectWorkspaceFiles(sessionTasks);
    expect(files.map((file) => file.name).sort()).toEqual(["new.md", "old.md"]);
  });

  it("keeps fileListOnly events in the all-files collection", () => {
    const tasks = [
      {
        messageType: "file",
        resultMap: {
          messageType: "file",
          fileListOnly: true,
          fileInfo: [
            {
              fileName: "out/report.md",
              relativePath: "out/report.md",
              domainUrl: "http://x/preview/s/out/report.md",
              ossUrl: "http://x/download/s/out/report.md",
            },
          ],
        },
      },
    ] as any;

    const files = collectWorkspaceFiles(tasks);
    expect(files.map((file) => file.relativePath)).toEqual(["out/report.md"]);
  });

  it("includes user-uploaded chat.files in session file list", () => {
    const chatList = [
      {
        files: [
          {
            name: "photo.png",
            url: "http://x/preview/s/photo.png",
            downloadUrl: "http://x/download/s/photo.png",
            type: "png",
            size: 12,
          },
        ],
        tasks: [],
        multiAgent: { tasks: [] },
      },
    ] as unknown as CHAT.ChatItem[];

    const sessionTasks = collectSessionFileTasks(chatList, []);
    const files = collectWorkspaceFiles(sessionTasks);
    expect(files.map((file) => file.name)).toEqual(["photo.png"]);
    expect(files[0]?.url).toBe("http://x/preview/s/photo.png");
  });

  it("builds a directory tree for same-name files in different folders", () => {
    const files = collectWorkspaceFiles([
      {
        messageType: "file",
        fileInfo: [
          {
            fileName: "index.html",
            relativePath: "site/index.html",
            domainUrl: "http://x/preview/s/site/index.html",
            ossUrl: "http://x/download/s/site/index.html",
            fileSize: 12,
          },
          {
            fileName: "style.css",
            relativePath: "site/css/style.css",
            domainUrl: "http://x/preview/s/site/css/style.css",
            ossUrl: "http://x/download/s/site/css/style.css",
            fileSize: 8,
          },
          {
            fileName: "style.css",
            relativePath: "site/theme/style.css",
            domainUrl: "http://x/preview/s/site/theme/style.css",
            ossUrl: "http://x/download/s/site/theme/style.css",
            fileSize: 9,
          },
        ],
      },
    ] as any);

    expect(files).toHaveLength(3);
    const tree = buildWorkspaceTree(files);
    expect(tree).toHaveLength(1);
    expect(tree[0]?.kind).toBe("dir");
    if (tree[0]?.kind !== "dir") {
      return;
    }
    expect(tree[0].name).toBe("site");
    const childNames = tree[0].children.map((child) => child.name).sort();
    expect(childNames).toEqual(["css", "index.html", "theme"]);
  });

  it("builds a directory tree from history artifactRefs", () => {
    const files = collectWorkspaceFiles([
      {
        messageType: "tool_result",
        artifactRefs: [
          {
            fileName: "index.html",
            relativePath: "site/index.html",
            previewUrl: "http://x/preview/s/site/index.html",
            downloadUrl: "http://x/download/s/site/index.html",
          },
          {
            fileName: "style.css",
            relativePath: "site/css/style.css",
            previewUrl: "http://x/preview/s/site/css/style.css",
            downloadUrl: "http://x/download/s/site/css/style.css",
          },
        ],
      },
    ] as any);

    const tree = buildWorkspaceTree(files);
    expect(tree).toHaveLength(1);
    expect(tree[0]?.kind).toBe("dir");
    if (tree[0]?.kind !== "dir") {
      return;
    }
    expect(tree[0].name).toBe("site");
    expect(tree[0].children.map((child) => child.name).sort()).toEqual([
      "css",
      "index.html",
    ]);
  });
});
