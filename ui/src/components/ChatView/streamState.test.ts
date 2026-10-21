import { describe, expect, it } from "vitest";

import {
  applyWaitingUserInputState,
  hasPendingAskUserQuestion,
  canOpenTaskWorkspacePanel,
  isChatItemFollowable,
  isChatItemRunning,
  isParentLoopLive,
  planSessionObservation,
  isHitlYieldEvent,
  isStructuredDataOnlyTask,
  isTimelineToolActive,
  isWorkspaceAttentionTask,
  resolveActionPanelVisibility,
  resolveRunPresence,
  isWorkspaceRenderableTask,
  resolveWorkspaceCaption,
  shouldRefreshWorkspaceTask,
  WAITING_USER_HELP_HINT,
} from "./streamState";

describe("streamState presence & attention", () => {
  it("主结论收口后 metrics=RUNNING 仍视为任务进行中", () => {
    expect(
      isChatItemRunning({
        loading: false,
        metrics: { status: "RUNNING" },
      } as CHAT.ChatItem)
    ).toBe(true);
  });

  it("WAITING_INPUT 不应自动建立观察流", () => {
    expect(
      isChatItemFollowable({
        loading: false,
        tip: "需要你的帮助",
        metrics: { status: "WAITING_INPUT" },
      } as CHAT.ChatItem)
    ).toBe(false);
    expect(
      isChatItemFollowable({
        loading: true,
        metrics: { status: "RUNNING" },
      } as CHAT.ChatItem)
    ).toBe(true);
    expect(
      isChatItemFollowable({
        loading: true,
        metrics: { status: "RUNNING" },
        multiAgent: {
          tasks: [[
            {
              messageType: "ask_user_question",
              resultMap: { status: "pending" },
            },
          ]],
        },
      } as CHAT.ChatItem)
    ).toBe(false);
  });

  it("主结论已出后父循环不再 live，第二句应 POST 而非 inject", () => {
    expect(
      isParentLoopLive({
        loading: false,
        conclusion: { messageType: "result" },
        metrics: { status: "RUNNING" },
      } as CHAT.ChatItem)
    ).toBe(false);
    expect(
      isParentLoopLive({
        loading: true,
        metrics: { status: "RUNNING" },
      } as CHAT.ChatItem)
    ).toBe(true);
  });

  it("SUCCESS 且无后台任务时不再占用会话", () => {
    expect(
      isChatItemRunning({
        loading: false,
        metrics: { status: "SUCCESS" },
      } as CHAT.ChatItem)
    ).toBe(false);
  });


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

  it("settling 与 working 阶段的注意力都指向时间线", () => {
    const workingTask = {
      messageType: "tool_call",
      resultMap: { isFinal: false },
    } as CHAT.Task;

    const working = resolveRunPresence({
      loading: true,
      taskList: [workingTask],
      chat: {
        loading: true,
        multiAgent: { tasks: [[workingTask]] },
        metrics: { status: "RUNNING" },
      } as unknown as CHAT.ChatItem,
    });
    expect(working.phase).toBe("working");
    expect(working.attention).toBe("timeline");

    const settling = resolveRunPresence({
      loading: true,
      chat: {
        loading: true,
        conclusion: { messageType: "agent_stream" },
        multiAgent: { tasks: [] },
        metrics: { status: "RUNNING" },
      } as unknown as CHAT.ChatItem,
    });
    expect(settling.phase).toBe("settling");
    expect(settling.attention).toBe("timeline");
  });

  it("深度研究首包前、无计划无思考时仍是 queued", () => {
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
  });
});

describe("HITL yield parking", () => {
  it("识别 plan_approval / ask_user_question 让步帧", () => {
    expect(
      isHitlYieldEvent({
        messageType: "plan_approval",
      } as MESSAGE.EventData)
    ).toBe(true);
    expect(
      isHitlYieldEvent({
        messageType: "task",
        resultMap: { messageType: "plan_approval" },
      } as MESSAGE.EventData)
    ).toBe(true);
    expect(
      isHitlYieldEvent({
        messageType: "task",
        resultMap: { messageType: "ask_user_question" },
      } as MESSAGE.EventData)
    ).toBe(true);
    expect(
      isHitlYieldEvent({
        messageType: "task",
        resultMap: { messageType: "tool_result" },
      } as MESSAGE.EventData)
    ).toBe(false);
  });

  it("pending plan_approval 视为等待用户输入", () => {
    const chat = {
      loading: true,
      metrics: { status: "RUNNING" },
      multiAgent: {
        tasks: [
          [
            {
              messageType: "plan_approval",
              resultMap: {
                messageType: "plan_approval",
                resultMap: { status: "pending", approvalId: "pa_1" },
              },
            },
          ],
        ],
      },
    } as unknown as CHAT.ChatItem;

    expect(hasPendingAskUserQuestion(chat)).toBe(true);
    const parked = applyWaitingUserInputState(chat);
    expect(parked.loading).toBe(false);
    expect(parked.metrics?.status).toBe("WAITING_INPUT");
    expect(parked.tip).toBe(WAITING_USER_HELP_HINT);
  });
});

const DISCONNECT_TIP = "连接暂时断开，任务仍在后台执行";

describe("planSessionObservation", () => {
  it("跨会话 abort，当前健康连接不 follow", () => {
    const plan = planSessionObservation({
      conversationId: "b",
      chats: [
        {
          requestId: "req-b",
          loading: true,
          metrics: { status: "RUNNING" },
        } as CHAT.ChatItem,
      ],
      live: [
        { conversationId: "a", aborted: false },
        { conversationId: "b", aborted: false },
      ],
      disconnectTip: DISCONNECT_TIP,
    });

    expect(plan.abortConversationIds).toEqual(["a"]);
    expect(plan.followRequestId).toBeNull();
    expect(plan.clearDisconnectTip).toBe(false);
  });

  it("同会话死连接（aborted 仍为 false 且 tip 为断开语）会 abort 并 follow", () => {
    const plan = planSessionObservation({
      conversationId: "a",
      chats: [
        {
          requestId: "req-a",
          loading: true,
          tip: DISCONNECT_TIP,
          metrics: { status: "RUNNING" },
        } as CHAT.ChatItem,
      ],
      live: [
        { conversationId: "b", aborted: false },
        { conversationId: "a", aborted: false },
      ],
      disconnectTip: DISCONNECT_TIP,
    });

    expect(plan.abortConversationIds).toEqual(["b", "a"]);
    expect(plan.followRequestId).toBe("req-a");
    expect(plan.clearDisconnectTip).toBe(true);
  });

  it("健康连接不 follow", () => {
    const plan = planSessionObservation({
      conversationId: "a",
      chats: [
        {
          requestId: "req-a",
          loading: true,
          tip: "正在推进任务…",
          metrics: { status: "RUNNING" },
        } as CHAT.ChatItem,
      ],
      live: [{ conversationId: "a", aborted: false }],
      disconnectTip: DISCONNECT_TIP,
    });

    expect(plan.followRequestId).toBeNull();
    expect(plan.clearDisconnectTip).toBe(false);
    expect(plan.abortConversationIds).not.toContain("a");
  });

  it("WAITING_INPUT 不 follow", () => {
    const plan = planSessionObservation({
      conversationId: "a",
      chats: [
        {
          requestId: "req-a",
          loading: false,
          tip: "需要你的帮助",
          metrics: { status: "WAITING_INPUT" },
        } as CHAT.ChatItem,
      ],
      live: [{ conversationId: "a", aborted: false }],
      disconnectTip: DISCONNECT_TIP,
    });

    expect(plan.followRequestId).toBeNull();
    expect(plan.clearDisconnectTip).toBe(false);
    expect(plan.abortConversationIds).not.toContain("a");
  });

  it("pending AskUser 不 follow", () => {
    const plan = planSessionObservation({
      conversationId: "a",
      chats: [
        {
          requestId: "req-a",
          loading: true,
          metrics: { status: "RUNNING" },
          multiAgent: {
            tasks: [[
              {
                messageType: "ask_user_question",
                resultMap: { status: "pending" },
              },
            ]],
          },
        } as CHAT.ChatItem,
      ],
      live: [{ conversationId: "a", aborted: false }],
      disconnectTip: DISCONNECT_TIP,
    });

    expect(plan.followRequestId).toBeNull();
    expect(plan.clearDisconnectTip).toBe(false);
    expect(plan.abortConversationIds).not.toContain("a");
  });

  it("仅 RUNNING 且没有活流时 follow", () => {
    const plan = planSessionObservation({
      conversationId: "a",
      chats: [
        {
          requestId: "req-a",
          loading: true,
          metrics: { status: "RUNNING" },
        } as CHAT.ChatItem,
      ],
      live: [{ conversationId: "b", aborted: false }],
      disconnectTip: DISCONNECT_TIP,
    });

    expect(plan.abortConversationIds).toEqual(["b"]);
    expect(plan.followRequestId).toBe("req-a");
    expect(plan.clearDisconnectTip).toBe(false);
  });

  it("没有目标 chat 时不 abort 当前流", () => {
    const plan = planSessionObservation({
      conversationId: "a",
      chats: [
        {
          requestId: "req-a",
          loading: false,
          metrics: { status: "SUCCESS" },
        } as CHAT.ChatItem,
      ],
      live: [
        { conversationId: "a", aborted: false },
        { conversationId: "b", aborted: false },
      ],
      disconnectTip: DISCONNECT_TIP,
    });

    expect(plan.followRequestId).toBeNull();
    expect(plan.clearDisconnectTip).toBe(false);
    expect(plan.abortConversationIds).toEqual(["b"]);
  });
});

describe("工作区可见性判定链", () => {
  const task = (
    messageType: string,
    resultMap: Record<string, unknown> = {}
  ) =>
    ({
      messageType,
      resultMap,
    }) as unknown as CHAT.Task;

  it("isWorkspaceRenderableTask 排除隐藏类型与空任务", () => {
    expect(isWorkspaceRenderableTask(undefined)).toBe(false);
    expect(isWorkspaceRenderableTask(task("tool_thought"))).toBe(false);
    expect(isWorkspaceRenderableTask(task("llm_reasoning"))).toBe(false);
    expect(isWorkspaceRenderableTask(task("ui_tree"))).toBe(false);
    expect(isWorkspaceRenderableTask(task("html"))).toBe(true);
    expect(isWorkspaceRenderableTask(task("file"))).toBe(true);
  });

  it("deep_search 在 search / chapter_summary / report 三阶段都进工作区", () => {
    for (const stage of ["search", "chapter_summary", "report"]) {
      expect(
        isWorkspaceRenderableTask(task("deep_search", { messageType: stage }))
      ).toBe(true);
    }
    // 未知 stage 被 resolveDeepSearchStage 归一成 search，所以仍进工作区
    // （归一的设计意图就是不让 UI 卡在空状态）
    expect(
      isWorkspaceRenderableTask(task("deep_search", { messageType: "unknown" }))
    ).toBe(true);
    // resultMap 里完全没有 messageType 时同理
    expect(isWorkspaceRenderableTask(task("deep_search"))).toBe(true);
  });

  it("isStructuredDataOnlyTask：tool_call 一律算纯结构化", () => {
    expect(isStructuredDataOnlyTask(task("tool_call"))).toBe(true);
    expect(isStructuredDataOnlyTask(task("html"))).toBe(false);
  });

  it("isStructuredDataOnlyTask：tool_result 有产物就放行", () => {
    const withArtifact = task("tool_result", {
      toolName: "code_interpreter",
      fileInfo: [
        {
          fileName: "a.png",
          domainUrl: "http://x/a.png",
        },
      ],
    });
    expect(isStructuredDataOnlyTask(withArtifact)).toBe(false);
  });

  it("isStructuredDataOnlyTask：搜索类工具即使无产物也放行", () => {
    expect(isStructuredDataOnlyTask(task("tool_result", { toolName: "web_search" }))).toBe(
      false
    );
    expect(
      isStructuredDataOnlyTask(task("tool_result", { toolName: "internal_search" }))
    ).toBe(false);
  });

  it("canOpenTaskWorkspacePanel 同时要求可渲染且非纯结构化", () => {
    expect(canOpenTaskWorkspacePanel(undefined)).toBe(false);
    // 可渲染但纯结构化 → 不能打开
    expect(canOpenTaskWorkspacePanel(task("tool_call"))).toBe(false);
    // 可渲染且非纯结构化 → 可以
    expect(canOpenTaskWorkspacePanel(task("html"))).toBe(true);
    expect(canOpenTaskWorkspacePanel(task("file"))).toBe(true);
    // 隐藏类型 → 不能
    expect(canOpenTaskWorkspacePanel(task("plan_approval"))).toBe(false);
  });
});
