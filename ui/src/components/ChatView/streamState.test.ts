import { describe, expect, it } from "vitest";

import {
  isStructuredDataOnlyTask,
  isTimelineToolActive,
  isWorkspaceAttentionTask,
  resolveActionPanelVisibility,
  resolveRunPresence,
  resolveWorkspaceCaption,
  shouldRefreshWorkspaceTask,
} from "./streamState";

describe("streamState presence & attention", () => {
  it("plan 单独存在时不自动打开右侧工作区", () => {
    expect(
      resolveActionPanelVisibility({
        plan: {
          stages: [{ title: "分析需求", status: "completed" }],
        } as unknown as CHAT.Plan,
        taskList: [],
      })
    ).toBe(false);
  });

  it("html / file 等产物任务会打开工作区", () => {
    expect(
      resolveActionPanelVisibility({
        taskList: [
          {
            messageType: "html",
            resultMap: {
              messageType: "html",
              isFinal: false,
              fileInfo: [
                {
                  fileName: "page.html",
                  domainUrl: "http://x/preview/s/page.html",
                },
              ],
            },
          } as CHAT.Task,
        ],
      })
    ).toBe(true);
  });

  it("没有文件产物的 html / tool_result 不打开空工作区", () => {
    expect(
      resolveActionPanelVisibility({
        taskList: [
          {
            messageType: "html",
            resultMap: { messageType: "html", isFinal: false },
          } as CHAT.Task,
        ],
      })
    ).toBe(false);
    expect(
      isWorkspaceAttentionTask({
        messageType: "tool_result",
        resultMap: { messageType: "tool_result", toolName: "Read" },
      } as unknown as CHAT.Task)
    ).toBe(false);
  });

  it("fileListOnly 文件保留文件列表但不抢工作区焦点", () => {
    const task = {
      messageType: "file",
      resultMap: {
        messageType: "file",
        fileListOnly: true,
        fileInfo: [
          {
            fileName: "out/report.md",
            relativePath: "out/report.md",
            domainUrl: "http://x/preview/s/out/report.md",
          },
        ],
      },
    } as unknown as CHAT.Task;

    expect(isWorkspaceAttentionTask(task)).toBe(false);
    expect(
      resolveActionPanelVisibility({
        taskList: [task],
      })
    ).toBe(false);
    expect(
      shouldRefreshWorkspaceTask({
        messageType: "task",
        messageId: "file-only",
        taskId: "t1",
        messageOrder: 1,
        taskOrder: 1,
        resultMap: task.resultMap,
      } as unknown as MESSAGE.EventData)
    ).toBe(false);
  });

  it("ask_user_question 不抢右侧工作区（交互在底部 Dock）", () => {
    expect(
      isWorkspaceAttentionTask({
        messageType: "ask_user_question",
        resultMap: { messageType: "ask_user_question", status: "pending" },
      } as unknown as CHAT.Task)
    ).toBe(false);
    expect(
      resolveActionPanelVisibility({
        taskList: [
          {
            messageType: "ask_user_question",
            resultMap: { messageType: "ask_user_question", status: "pending" },
          } as CHAT.Task,
        ],
      })
    ).toBe(false);
  });

  it("tool_call 不作为自动工作区注意力目标", () => {
    expect(
      isWorkspaceAttentionTask({
        messageType: "tool_call",
        resultMap: { messageType: "tool_call", status: "running" },
      } as unknown as CHAT.Task)
    ).toBe(false);
  });

  it("canvas_publish 带 html 产物时会打开工作区，点击也不再当纯结构化数据", () => {
    const task = {
      messageType: "tool_call",
      resultMap: {
        messageType: "tool_call",
        toolName: "canvas_publish",
        status: "success",
        fileInfo: [
          {
            fileName: "index.html",
            relativePath: "pages/index.html",
            domainUrl: "http://x/preview/s/pages/index.html",
          },
        ],
      },
    } as unknown as CHAT.Task;

    expect(isWorkspaceAttentionTask(task)).toBe(true);
    expect(isStructuredDataOnlyTask(task)).toBe(false);
    expect(resolveActionPanelVisibility({ taskList: [task] })).toBe(true);
  });

  it("深度思考任务使用任务级完成标志停止流式状态", () => {
    expect(
      isTimelineToolActive({
        messageType: "llm_reasoning",
        isFinal: true,
        resultMap: {},
      } as CHAT.Task)
    ).toBe(false);
  });

  it("tool_call 与纯 JSON/空结果不进工作区，纯文本结果可进", () => {
    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_call",
        resultMap: {
          messageType: "tool_call",
          toolName: "foo",
        },
      } as CHAT.Task)
    ).toBe(true);

    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_result",
        toolResult: {
          toolName: "foo",
          toolResult: '{"ok":true}',
        },
      } as CHAT.Task)
    ).toBe(true);
    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_result",
        toolResult: {
          toolName: "foo",
          toolResult: "",
        },
      } as CHAT.Task)
    ).toBe(true);

    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_result",
        toolResult: {
          toolName: "foo",
          toolResult: "plain text result",
        },
      } as CHAT.Task)
    ).toBe(false);
  });

  it("子智能体与空工具结果不打开空白 Structured data 面板", () => {
    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_call",
        resultMap: { messageType: "tool_call", toolName: "Agent" },
      } as CHAT.Task)
    ).toBe(true);

    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_result",
        toolResult: {
          toolName: "Agent",
          toolResult: 'status=completed\nagentType=Explore\n\n报告正文',
        },
      } as CHAT.Task)
    ).toBe(true);

    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_result",
        toolResult: {
          toolName: "workspace_grep",
          toolResult: "",
        },
      } as CHAT.Task)
    ).toBe(true);
  });

  it("嵌套纯文本 tool_result 允许点击打开工作区", () => {
    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_result",
        resultMap: {
          resultMap: {
            toolResult: {
              toolName: "workspace_grep",
              toolResult: "matches",
            },
          },
        },
      } as unknown as CHAT.Task)
    ).toBe(false);
  });

  it("tool_call 事件不刷新工作区跟随", () => {
    expect(
      shouldRefreshWorkspaceTask({
        messageType: "task",
        messageId: "m1",
        taskId: "t1",
        messageOrder: 1,
        taskOrder: 1,
        resultMap: {
          messageType: "tool_call",
          status: "running",
        },
      } as unknown as MESSAGE.EventData)
    ).toBe(false);
  });

  it("markdown 事件会刷新工作区跟随", () => {
    expect(
      shouldRefreshWorkspaceTask({
        messageType: "task",
        messageId: "m2",
        taskId: "t1",
        messageOrder: 2,
        taskOrder: 1,
        resultMap: {
          messageType: "markdown",
          isFinal: false,
        },
      } as unknown as MESSAGE.EventData)
    ).toBe(true);
  });

  it("ui_patch 不抢工作区（GenUI 在对话主区展示）", () => {
    expect(
      shouldRefreshWorkspaceTask({
        messageType: "task",
        messageId: "m3",
        taskId: "t1",
        messageOrder: 3,
        taskOrder: 1,
        resultMap: {
          messageType: "ui_patch",
          isFinal: true,
          patches: [{ op: "replace", path: "/root/props/title", value: "X" }],
        },
      } as unknown as MESSAGE.EventData)
    ).toBe(false);
  });

  it("发送后首包前应为 queued/thinking 存在感", () => {
    const presence = resolveRunPresence({
      loading: true,
      deepThink: true,
      chat: {
        loading: true,
        multiAgent: { tasks: [] },
        metrics: { status: "RUNNING" },
      } as unknown as CHAT.ChatItem,
    });

    expect(presence.phase).toBe("queued");
    expect(presence.hint).toContain("计划");
  });

  it("有进行中产物时应进入 crafting 并指向工作区", () => {
    const task = {
      messageType: "html",
      resultMap: {
        messageType: "html",
        isFinal: false,
        fileInfo: [
          {
            fileName: "page.html",
            domainUrl: "http://x/preview/s/page.html",
          },
        ],
      },
    } as CHAT.Task;

    const presence = resolveRunPresence({
      loading: true,
      taskList: [task],
      chat: {
        loading: true,
        multiAgent: { tasks: [[task]] },
        tasks: [[{ children: [task] }]],
        metrics: { status: "RUNNING" },
      } as unknown as CHAT.ChatItem,
    });

    expect(presence.phase).toBe("crafting");
    expect(presence.attention).toBe("workspace");
    expect(resolveWorkspaceCaption(task, true)).toContain("正在产出");
  });
});
