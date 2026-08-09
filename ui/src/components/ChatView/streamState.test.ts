import { describe, expect, it } from "vitest";

import {
  isStructuredDataOnlyTask,
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
            resultMap: { messageType: "html", isFinal: false },
          } as CHAT.Task,
        ],
      })
    ).toBe(true);
  });

  it("tool_call 不作为自动工作区注意力目标", () => {
    expect(
      isWorkspaceAttentionTask({
        messageType: "tool_call",
        resultMap: { messageType: "tool_call", status: "running" },
      } as CHAT.Task)
    ).toBe(false);
  });

  it("Structured data 工具入参/出参不展开工作区", () => {
    expect(
      isStructuredDataOnlyTask({
        messageType: "tool_call",
        resultMap: { messageType: "tool_call", toolName: "foo" },
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
          toolResult: "plain text result",
        },
      } as CHAT.Task)
    ).toBe(false);
  });

  it("子智能体卡片与 JSON 观察值不打开右侧空白面板", () => {
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

  it("ui_patch 事件会刷新工作区以反映已合并 tree", () => {
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
    ).toBe(true);
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
      resultMap: { messageType: "html", isFinal: false },
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
