import { describe, expect, it } from "vitest";

import {
  AGENT_DIRECTORY_TAB_KEY,
  agentTabKey,
  buildSessionFileManifestTask,
  buildWorkspaceTree,
  collectSessionFileTasks,
  collectWorkspaceFiles,
  flattenTasksWithFiles,
  isAgentDirectoryTab,
  isAgentTab,
  isSourceTab,
  isTaskTab,
  resolveWorkspacePaneMode,
  tabPreviewFileKey,
  taskTabKey,
  taskTabPrimaryFileKey,
  upsertWorkspaceTab,
  workspaceTabKey,
  type WorkspaceSourceTab,
  type WorkspaceTab,
} from "./workspaceFiles";
import { isFileListOnlyTask, normalizeSessionArtifactFiles } from "@/utils/taskArtifacts";

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

  it("converts a session manifest into a fileListOnly task with stable links", () => {
    const files = normalizeSessionArtifactFiles([
      {
        artifactType: "text/markdown",
        displayName: "report.md",
        relativePath: "reports/report.md",
        resourceKey: "workspace/report.md",
        previewUrl: "https://files.test/preview/report.md",
        downloadUrl: "https://files.test/download/report.md",
        fileSize: 1234,
        mimeType: "text/markdown",
        missing: false,
      },
    ], "session-001");
    const task = buildSessionFileManifestTask(files);

    expect(task).not.toBeNull();
    expect(isFileListOnlyTask(task)).toBe(true);
    const collected = collectWorkspaceFiles(task ? [task] : []);
    expect(collected).toHaveLength(1);
    expect(collected[0]).toMatchObject({
      name: "report.md",
      relativePath: "reports/report.md",
      previewUrl: "https://files.test/preview/report.md",
      downloadUrl: "https://files.test/download/report.md",
      resourceKey: "workspace/report.md",
    });
  });

  it("deduplicates manifest files with the same replay artifact", () => {
    const manifestFiles = normalizeSessionArtifactFiles([
      {
        artifactType: "text/markdown",
        displayName: "report.md",
        relativePath: "reports/report.md",
        resourceKey: "workspace/report.md",
        previewUrl: "https://files.test/preview/report.md",
        downloadUrl: "https://files.test/download/report.md",
      },
    ]);
    const manifestTask = buildSessionFileManifestTask(manifestFiles);
    const replayTask = {
      messageType: "tool_result",
      artifactRefs: [
        {
          fileName: "report.md",
          relativePath: "reports/report.md",
          resourceKey: "workspace/report.md",
          previewUrl: "https://files.test/preview/report.md",
          downloadUrl: "https://files.test/download/report.md",
        },
      ],
    } as any;

    const files = collectWorkspaceFiles([
      replayTask,
      ...(manifestTask ? [manifestTask] : []),
    ]);
    expect(files).toHaveLength(1);
    expect(files[0]?.resourceKey).toBe("workspace/report.md");
  });
});

describe("workspace tab model (Stage 4)", () => {
  const patchTab = (patch: string): WorkspaceSourceTab => ({
    kind: "source",
    key: `patch:src/a.ts:${patch}`,
    source: {
      type: "patch",
      title: "a.ts",
      path: "src/a.ts",
      patch
    },
  });

  it("resolves a file tab key from its artifact identity", () => {
    const tab = {
      name: "a.ts",
      resourceKey: "workspace/a.ts",
      task: {}
    } as any;
    expect(isSourceTab(tab)).toBe(false);
    expect(workspaceTabKey(tab)).toBe("workspace/a.ts");
  });

  it("resolves a source tab key from its own key, not from the missing url", () => {
    const tab = patchTab("@@ -1 +1 @@");
    expect(isSourceTab(tab)).toBe(true);
    expect(workspaceTabKey(tab)).toBe(tab.key);
    // 关键回归：source tab 没有 url / resourceKey，不能被当成空 key 而互相覆盖
    expect(workspaceTabKey(tab)).not.toBe("");
  });

  it("keeps distinct source tabs apart even when they share a title", () => {
    const first = patchTab("@@ -1 +1 @@\n-a\n+b");
    const second = patchTab("@@ -9 +9 @@\n-c\n+d");
    expect(workspaceTabKey(first)).not.toBe(workspaceTabKey(second));
  });

  it("never mixes source tabs with file tabs", () => {
    const fileTab = {
      name: "a.ts",
      resourceKey: "workspace/a.ts",
      task: {}
    } as any;
    const source = patchTab("@@ -1 +1 @@");
    expect(workspaceTabKey(fileTab)).not.toBe(workspaceTabKey(source));
  });
});

describe("resolveWorkspacePaneMode", () => {
  it("没有选中的 tab 时是 empty —— 宿主据此收起整个工作区", () => {
    // 关键回归：旧的「动态」是工作区的一个模式（`panelMode: "follow"`），
    // 没有 tab 时也占着右侧面板显示一张空态卡。现在没有 tab 就是 empty。
    expect(resolveWorkspacePaneMode(undefined)).toBe("empty");
  });

  it("按选中 tab 的类型分派：source / file / task / agent / agent-directory", () => {
    const source = {
      kind: "source",
      key: "patch:src/a.ts:h1",
      source: {
        type: "patch",
        title: "a.ts",
        patch: "@@ -1 +1 @@"
      },
    } as unknown as WorkspaceTab;
    const file = {
      name: "report.md",
      type: "md",
      url: "https://x/report.md",
    } as unknown as WorkspaceTab;
    const task = {
      kind: "task",
      key: taskTabKey("tk-1"),
      taskKey: "tk-1",
      title: "任务",
      fallbackTask: {},
    } as unknown as WorkspaceTab;
    const agent = {
      kind: "agent",
      key: agentTabKey("tc-1"),
      toolCallId: "tc-1",
      title: "子智能体",
      fallbackTool: {},
      fallbackChat: {},
    } as unknown as WorkspaceTab;
    const directory = {
      kind: "agent-directory",
      key: AGENT_DIRECTORY_TAB_KEY,
    } as unknown as WorkspaceTab;

    expect(resolveWorkspacePaneMode(source)).toBe("source");
    expect(resolveWorkspacePaneMode(file)).toBe("file");
    expect(resolveWorkspacePaneMode(task)).toBe("task");
    expect(resolveWorkspacePaneMode(agent)).toBe("agent");
    expect(resolveWorkspacePaneMode(directory)).toBe("agent-directory");
  });
});

describe("子智能体 tab 身份", () => {
  it("agentTabKey 按 toolCallId 幂等，且与文件 / source tab 不撞键", () => {
    expect(agentTabKey("tc-1")).toBe(agentTabKey("tc-1"));
    expect(agentTabKey("tc-1")).not.toBe(agentTabKey("tc-2"));

    const agentTab = {
      kind: "agent",
      key: agentTabKey("tc-1"),
      toolCallId: "tc-1",
      title: "继续完善 HTML 页面",
      fallbackTool: {},
      fallbackChat: {},
    } as unknown as WorkspaceTab;
    const directoryTab = {
      kind: "agent-directory",
      key: AGENT_DIRECTORY_TAB_KEY,
    } as unknown as WorkspaceTab;

    expect(isAgentTab(agentTab)).toBe(true);
    expect(isAgentDirectoryTab(agentTab)).toBe(false);
    expect(isAgentDirectoryTab(directoryTab)).toBe(true);
    expect(workspaceTabKey(agentTab)).toBe("agent:tc-1");
    expect(workspaceTabKey(directoryTab)).toBe("agent-directory");
  });
});

describe("任务预览 tab 身份（原「动态」跟随）", () => {
  const taskTab = (taskKey: string): WorkspaceTab =>
    ({
      kind: "task",
      key: taskTabKey(taskKey),
      taskKey,
      title: "任务预览",
      fallbackTask: {},
    }) as unknown as WorkspaceTab;

  it("taskTabKey 按任务稳定身份幂等：同一个任务只有一个 tab", () => {
    expect(taskTabKey("t-1")).toBe(taskTabKey("t-1"));
    expect(taskTabKey("t-1")).not.toBe(taskTabKey("t-2"));
    expect(workspaceTabKey(taskTab("t-1"))).toBe("task:t-1");
    expect(isTaskTab(taskTab("t-1"))).toBe(true);
  });

  it("任务 tab 与文件 / source / agent / 目录 tab 互不撞键", () => {
    const fileTab = {
      name: "a.ts",
      resourceKey: "workspace/a.ts",
      task: {},
    } as unknown as WorkspaceTab;
    const sourceTab = {
      kind: "source",
      key: "patch:src/a.ts:h1",
      source: {
        type: "patch",
        title: "a.ts",
        patch: "@@ -1 +1 @@"
      },
    } as unknown as WorkspaceTab;
    const agentTab = {
      kind: "agent",
      key: agentTabKey("t-1"),
      toolCallId: "t-1",
      title: "子智能体",
      fallbackTool: {},
      fallbackChat: {},
    } as unknown as WorkspaceTab;

    const keys = [
      workspaceTabKey(fileTab),
      workspaceTabKey(sourceTab),
      workspaceTabKey(agentTab),
      workspaceTabKey(taskTab("t-1")),
      AGENT_DIRECTORY_TAB_KEY,
    ];
    expect(new Set(keys).size).toBe(keys.length);
    expect(isTaskTab(fileTab)).toBe(false);
    expect(isTaskTab(sourceTab)).toBe(false);
    expect(isTaskTab(agentTab)).toBe(false);
  });
});

describe("upsertWorkspaceTab：同一个文件只留一个预览 tab", () => {
  /** 产出 `out/page.html` 的工具任务（自动跟随会为它开一个任务 tab） */
  const htmlTask = (id: string) =>
    ({
      id,
      messageType: "tool_result",
      resultMap: {
        toolName: "Write",
        fileInfo: [
          {
            fileName: "page.html",
            relativePath: "out/page.html",
            resourceKey: "workspace/out/page.html",
            domainUrl: "http://x/preview/s/out/page.html",
            ossUrl: "http://x/download/s/out/page.html",
          },
        ],
      },
    }) as unknown as CHAT.Task;

  const taskTab = (id: string): WorkspaceTab =>
    ({
      kind: "task",
      key: taskTabKey(id),
      taskKey: id,
      title: "page.html",
      fallbackTask: htmlTask(id),
    }) as unknown as WorkspaceTab;

  const fileTab = (openedAt: number): WorkspaceTab =>
    ({
      name: "page.html",
      type: "html",
      url: "http://x/preview/s/out/page.html",
      resourceKey: "workspace/out/page.html",
      relativePath: "out/page.html",
      openedAt,
    }) as unknown as WorkspaceTab;

  it("任务 tab 正预览同一文件时，文件 tab 就地顶替它（不新增、不跳位）", () => {
    // 关键回归：打开会话时流式自动跟随开了任务 tab 展示刚产出的文件，用户再点
    // 「打开文件」不该出现两个内容相同的 tab。
    const other = {
      name: "a.md",
      resourceKey: "workspace/a.md",
    } as unknown as WorkspaceTab;
    const next = upsertWorkspaceTab([taskTab("t-1"), other], fileTab(1));

    expect(next).toHaveLength(2);
    expect(next.map((tab) => workspaceTabKey(tab))).toEqual([
      "workspace/out/page.html",
      "workspace/a.md",
    ]);
    expect(isTaskTab(next[0])).toBe(false);
  });

  it("反过来也成立：文件 tab 已开着时，任务 tab 顶替它（同样只剩一个预览）", () => {
    const next = upsertWorkspaceTab([fileTab(1)], taskTab("t-1"));
    expect(next).toHaveLength(1);
    expect(isTaskTab(next[0])).toBe(true);
    expect(workspaceTabKey(next[0])).toBe("task:t-1");
  });

  it("预览别的文件的任务 tab 与文件 tab 并存", () => {
    const otherTask = {
      kind: "task",
      key: taskTabKey("t-2"),
      taskKey: "t-2",
      title: "other",
      fallbackTask: {
        id: "t-2",
        messageType: "tool_result"
      },
    } as unknown as WorkspaceTab;
    expect(upsertWorkspaceTab([otherTask], fileTab(1))).toHaveLength(2);
  });

  it("同一个文件重复打开仍然只有一个 tab（幂等）", () => {
    const once = upsertWorkspaceTab([], fileTab(1));
    const twice = upsertWorkspaceTab(once, fileTab(2));
    expect(twice).toHaveLength(1);
    expect(workspaceTabKey(twice[0])).toBe("workspace/out/page.html");
  });

  it("同一个任务重复打开仍然只有一个 tab（幂等）", () => {
    const first = upsertWorkspaceTab([], {
      ...taskTab("t-1"),
      openedAt: 111,
    } as WorkspaceTab);
    const second = upsertWorkspaceTab(first, {
      ...taskTab("t-1"),
      openedAt: 222,
    } as WorkspaceTab);
    expect(second).toHaveLength(1);
    expect(workspaceTabKey(second[0])).toBe("task:t-1");
    // 本函数只做「位置合并」：tab 对象整体用最新的那个。
    // 「重开不刷新 openedAt」由 ActionView 的 `openedAtFor` 负责。
    expect((second[0] as { openedAt?: number }).openedAt).toBe(222);
  });

  it("source tab 不受影响：补丁是另一种视图，允许与文件 tab 并存", () => {
    const patch = {
      kind: "source",
      key: "patch:out/page.html:h1",
      source: {
        type: "patch",
        title: "page.html",
        patch: "@@ -1 +1 @@"
      },
    } as unknown as WorkspaceTab;
    const next = upsertWorkspaceTab([patch], fileTab(1));
    expect(next).toHaveLength(2);
    expect(isSourceTab(next[0])).toBe(true);
  });

  it("tabPreviewFileKey：文件 tab 是它自己，任务 tab 是主产物，其余为空串", () => {
    expect(tabPreviewFileKey(fileTab(1))).toBe("workspace/out/page.html");
    expect(tabPreviewFileKey(taskTab("t-1"))).toBe("workspace/out/page.html");
    expect(tabPreviewFileKey({
      kind: "agent-directory",
      key: "agent-directory"
    } as WorkspaceTab)).toBe("");
  });

  it("taskTabPrimaryFileKey 只对任务 tab 生效，拿不到文件时是空串", () => {
    expect(taskTabPrimaryFileKey(taskTab("t-1"))).toBe("workspace/out/page.html");
    expect(taskTabPrimaryFileKey(fileTab(1))).toBe("");
    expect(
      taskTabPrimaryFileKey({
        kind: "task",
        key: taskTabKey("t-9"),
        taskKey: "t-9",
        title: "无产物任务",
        fallbackTask: {
          id: "t-9",
          messageType: "tool_result"
        },
      } as unknown as WorkspaceTab)
    ).toBe("");
  });
});
