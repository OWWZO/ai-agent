import { describe, expect, it } from "vitest";

import {
  buildAction,
  buildConversationTaskData,
  combineData,
  getStableTaskIdentity,
  handleTaskData,
} from "./chat";
import {
  buildDeepSearchPreviewModel,
  shouldRenderDeepSearchWorkspace,
} from "./deepSearch";
import { getPrimaryTaskFile } from "./taskArtifacts";

type DeepSearchStage = "extend" | "search" | "report";

function createDoc(link: string, title: string, content: string): MESSAGE.Doc {
  return {
    link,
    doc_type: "web",
    title,
    content,
  };
}

function createDeepSearchTask(
  stage: DeepSearchStage,
  options?: {
    snapshotMode?: boolean;
  }
): MESSAGE.Task {
  const snapshotMode = options?.snapshotMode ?? false;
  const docs =
    stage === "search"
      ? [[createDoc("https://example.com/a", "结果A", "内容A")], [createDoc("https://example.com/b", "结果B", "内容B")]]
      : [[], []];

  return {
    messageTime: "1714041600000",
    taskId: "task-1",
    messageType: "deep_search",
    requestId: "req-1",
    messageId: "msg-1",
    finish: stage === "report",
    isFinal: snapshotMode || stage === "report",
    id: "msg-1",
    resultMap: {
      messageType: stage,
      requestId: "req-1",
      isFinal: snapshotMode || stage === "report",
      searchFinish: stage === "search",
      query: stage === "report" ? "深度搜索原始问题" : undefined,
      answer: stage === "report" ? "总结内容" : "",
      searchResult: {
        query: ["子问题一", "子问题二"],
        docs,
      },
      fileInfo: [],
    },
  } as MESSAGE.Task;
}

function createChatItem(task: MESSAGE.Task): CHAT.ChatItem {
  return {
    sessionId: "session-1",
    requestId: "req-1",
    query: "原始问题",
    files: [],
    forceStop: false,
    loading: false,
    tasks: [],
    timeline: [],
    multiAgent: { tasks: [[task]] },
  } as CHAT.ChatItem;
}

function createDeepSearchEvent(stage: DeepSearchStage): MESSAGE.EventData {
  const docs =
    stage === "search"
      ? [[createDoc("https://example.com/a", "结果A", "内容A")], [createDoc("https://example.com/b", "结果B", "内容B")]]
      : [[], []];

  return {
    messageType: "task",
    messageId: "msg-1",
    taskId: "task-1",
    taskOrder: 1,
    messageOrder: 1,
    resultMap: {
      requestId: "req-1",
      messageId: "msg-1",
      messageType: "deep_search",
      messageTime: "1714041600000",
      finish: stage === "report",
      isFinal: stage === "report",
      resultMap: {
        messageType: stage,
        requestId: "req-1",
        isFinal: stage === "report",
        searchFinish: stage === "search",
        query: stage === "report" ? "深度搜索原始问题" : undefined,
        answer: stage === "report" ? "总结内容" : "",
        searchResult: {
          query: ["子问题一", "子问题二"],
          docs,
        },
        fileInfo: [],
      },
    },
  } as unknown as MESSAGE.EventData;
}

function createHtmlEvent(options?: {
  isFinal?: boolean;
  data?: string;
  artifactRefs?: Array<Record<string, any>>;
}): MESSAGE.EventData {
  return {
    messageType: "task",
    messageId: "html-msg-1",
    taskId: "task-html-1",
    taskOrder: 1,
    messageOrder: 1,
    artifactRefs: options?.artifactRefs,
    resultMap: {
      requestId: "req-html-1",
      messageId: "html-msg-1",
      messageType: "html",
      messageTime: "1714041600123",
      finish: Boolean(options?.isFinal),
      isFinal: Boolean(options?.isFinal),
      resultMap: {
        isFinal: Boolean(options?.isFinal),
        data: options?.data || "",
        codeOutput: options?.data || "",
        fileInfo: [],
      },
    },
  } as unknown as MESSAGE.EventData;
}

function createPlannerThoughtEvent(options: {
  plannerRoundId: string;
  planThought: string;
  isFinal?: boolean;
  messageId?: string;
}): MESSAGE.EventData {
  return {
    messageType: "plan_thought",
    messageId: options.messageId || `thought-${options.plannerRoundId}`,
    taskId: `planner-task-${options.plannerRoundId}`,
    taskOrder: 1,
    messageOrder: 1,
    resultMap: {
      requestId: "req-planner-1",
      messageId: options.messageId || `thought-${options.plannerRoundId}`,
      messageType: "plan_thought",
      messageTime: "1714041604000",
      isFinal: options.isFinal ?? true,
      finish: false,
      planThought: options.planThought,
      plannerRoundId: options.plannerRoundId,
    } as unknown as MESSAGE.Task,
  } as unknown as MESSAGE.EventData;
}

function createPlannerPlanEvent(options: {
  plannerRoundId: string;
  taskId?: string;
  title: string;
  stages?: string[];
  steps: string[];
  stepStatus: MESSAGE.PlanStatus[];
  notes?: string[];
  messageId?: string;
}): MESSAGE.EventData {
  return {
    messageType: "plan",
    messageId: options.messageId || `plan-${options.plannerRoundId}`,
    taskId: options.taskId || `planner-task-${options.plannerRoundId}`,
    taskOrder: 1,
    messageOrder: 1,
    resultMap: {
      requestId: "req-planner-1",
      messageId: options.messageId || `plan-${options.plannerRoundId}`,
      messageType: "plan",
      messageTime: "1714041605000",
      isFinal: true,
      finish: false,
      plannerRoundId: options.plannerRoundId,
      title: options.title,
      stages: options.stages || options.steps,
      steps: options.steps,
      stepStatus: options.stepStatus,
      notes: options.notes || options.steps.map(() => ""),
    } as unknown as MESSAGE.Task,
  } as unknown as MESSAGE.EventData;
}

function createTaskWrappedPlanEvent(options: {
  plannerRoundId: string;
  taskId?: string;
  title: string;
  steps: string[];
  stepStatus: MESSAGE.PlanStatus[];
  stages?: string[];
  notes?: string[];
  messageId?: string;
}): MESSAGE.EventData {
  return {
    messageType: "task",
    messageId: options.messageId || `task-plan-${options.plannerRoundId}`,
    taskId: options.taskId || `planner-task-${options.plannerRoundId}`,
    taskOrder: 1,
    messageOrder: 1,
    resultMap: {
      requestId: "req-planner-1",
      messageId: options.messageId || `task-plan-${options.plannerRoundId}`,
      messageType: "plan",
      messageTime: "1714041606000",
      isFinal: true,
      finish: false,
      plannerRoundId: options.plannerRoundId,
      title: options.title,
      stages: options.stages || options.steps,
      steps: options.steps,
      stepStatus: options.stepStatus,
      notes: options.notes || options.steps.map(() => ""),
    } as unknown as MESSAGE.Task,
  } as unknown as MESSAGE.EventData;
}

describe("chat deep_search progress", () => {
  it("extend 阶段会立即进入任务列表并展示查询分解", () => {
    const currentChat = createChatItem(createDeepSearchTask("extend"));

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);

    expect(taskList).toHaveLength(2);
    expect(taskList.map((task) => buildAction(task).action)).toEqual([
      "正在搜索",
      "正在搜索",
    ]);
    expect(taskList.map((task) => buildAction(task).name)).toEqual([
      "子问题一",
      "子问题二",
    ]);
  });

  it("search 阶段会复用同一组子查询并切换为搜索完成", () => {
    const currentChat = createChatItem(createDeepSearchTask("search"));

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);

    expect(taskList).toHaveLength(2);
    expect(taskList.map((task) => buildAction(task).action)).toEqual([
      "搜索完成",
      "搜索完成",
    ]);
    expect(taskList[0].resultMap.searchResult?.docs).toHaveLength(1);
  });

  it("会话快照重建后的 extend 阶段仍然保持正在搜索状态", () => {
    const snapshotChat = createChatItem(createDeepSearchTask("extend", { snapshotMode: true }));

    const { taskList } = buildConversationTaskData(snapshotChat, false);

    expect(taskList).toHaveLength(2);
    expect(taskList.map((task) => buildAction(task).action)).toEqual([
      "正在搜索",
      "正在搜索",
    ]);
    expect(taskList.map((task) => buildAction(task).name)).toEqual([
      "子问题一",
      "子问题二",
    ]);
  });

  it("实时流中的 extend -> search -> report 会切换为左侧预览与右侧详情分层", () => {
    const extendChat = createChatItem(createDeepSearchTask("extend"));
    const extendResult = handleTaskData(extendChat, false, extendChat.multiAgent);
    const extendChildren = extendResult.currentChat.tasks[0]?.[0]?.children || [];
    const extendModels = extendChildren.map((item) =>
      buildDeepSearchPreviewModel(item)
    );

    expect(extendModels).toHaveLength(2);
    expect(extendModels[0]?.stage).toBe("extend");
    expect(extendModels[0]?.interactive).toBe(false);
    expect(
      extendChildren.every((item) =>
        shouldRenderDeepSearchWorkspace(item.resultMap?.messageType)
      )
    ).toBe(false);

    const searchChat = createChatItem(createDeepSearchTask("search"));
    const searchResult = handleTaskData(searchChat, false, searchChat.multiAgent);
    const searchChildren = searchResult.currentChat.tasks[0]?.[0]?.children || [];
    const searchModels = searchChildren.map((item) =>
      buildDeepSearchPreviewModel(item)
    );

    expect(searchModels).toHaveLength(2);
    expect(searchModels[0]?.stage).toBe("search");
    expect(searchModels[0]?.interactive).toBe(true);
    expect(searchModels[0]?.resultCount).toBe(1);
    expect(
      searchChildren.every((item) =>
        shouldRenderDeepSearchWorkspace(item.resultMap?.messageType)
      )
    ).toBe(true);

    const reportChat = createChatItem(createDeepSearchTask("report"));
    const reportResult = handleTaskData(reportChat, false, reportChat.multiAgent);
    const reportChildren = reportResult.currentChat.tasks[0]?.[0]?.children || [];
    const reportModels = reportChildren.map((item) =>
      buildDeepSearchPreviewModel(item)
    );

    expect(reportModels.every((item) => item === undefined)).toBe(true);
    expect(
      reportChildren.every((item) =>
        shouldRenderDeepSearchWorkspace(item.resultMap?.messageType)
      )
    ).toBe(true);
  });

  it("会话快照重建会恢复左侧预览与右侧详情的阶段分工", () => {
    const extendSnapshot = createChatItem(
      createDeepSearchTask("extend", { snapshotMode: true })
    );
    const extendSnapshotResult = buildConversationTaskData(extendSnapshot, false);
    const extendSnapshotChildren =
      extendSnapshotResult.currentChat.tasks[0]?.[0]?.children || [];
    const extendSnapshotModels = extendSnapshotChildren.map((item) =>
      buildDeepSearchPreviewModel(item)
    );

    expect(extendSnapshotModels).toHaveLength(2);
    expect(extendSnapshotModels[0]?.stage).toBe("extend");
    expect(
      extendSnapshotChildren.every((item) =>
        shouldRenderDeepSearchWorkspace(item.resultMap?.messageType)
      )
    ).toBe(false);

    const searchSnapshot = createChatItem(
      createDeepSearchTask("search", { snapshotMode: true })
    );
    const searchSnapshotResult = buildConversationTaskData(searchSnapshot, false);
    const searchSnapshotChildren =
      searchSnapshotResult.currentChat.tasks[0]?.[0]?.children || [];
    const searchSnapshotModels = searchSnapshotChildren.map((item) =>
      buildDeepSearchPreviewModel(item)
    );

    expect(searchSnapshotModels).toHaveLength(2);
    expect(searchSnapshotModels[0]?.stage).toBe("search");
    expect(searchSnapshotModels[0]?.resultCount).toBe(1);
    expect(
      searchSnapshotChildren.every((item) =>
        shouldRenderDeepSearchWorkspace(item.resultMap?.messageType)
      )
    ).toBe(true);
  });

  it("report 阶段不会覆盖已有的搜索完成卡片", () => {
    const currentChat = {
      sessionId: "session-1",
      requestId: "req-1",
      query: "原始问题",
      files: [],
      forceStop: false,
      loading: false,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createDeepSearchEvent("search"), currentChat);
    combineData(createDeepSearchEvent("report"), currentChat);

    const { currentChat: renderedChat, taskList } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );
    const children = renderedChat.tasks[0]?.[0]?.children || [];
    const previewModels = children.map((item) => buildDeepSearchPreviewModel(item));

    expect(taskList).toHaveLength(3);
    expect(previewModels[0]?.stage).toBe("search");
    expect(previewModels[1]?.stage).toBe("search");
    expect(previewModels[2]).toBeUndefined();
  });

  it("deep_search 的稳定标识会区分 search 卡片和 report 卡片", () => {
    const currentChat = {
      sessionId: "session-1",
      requestId: "req-1",
      query: "原始问题",
      files: [],
      forceStop: false,
      loading: false,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createDeepSearchEvent("search"), currentChat);
    combineData(createDeepSearchEvent("report"), currentChat);

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);

    expect(taskList[0]?.messageId).toBe(taskList[2]?.messageId);
    expect(getStableTaskIdentity(taskList[0])).not.toBe(
      getStableTaskIdentity(taskList[2])
    );
  });

  it("html 最终包会把 artifact 引用合并回现有任务，供右侧直接预览", () => {
    const currentChat = {
      sessionId: "session-html-1",
      requestId: "req-html-1",
      query: "生成网页",
      files: [],
      forceStop: false,
      loading: false,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createHtmlEvent({ data: "<html><body>draft</body></html>" }), currentChat);
    combineData(createHtmlEvent({
      isFinal: true,
      data: "<html><body>final</body></html>",
      artifactRefs: [{
        displayName: "preview.html",
        previewUrl: "https://example.com/preview.html",
        downloadUrl: "https://example.com/download/preview.html",
        resourceKey: "preview-html",
        artifactType: "html",
      }],
    }), currentChat);

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);
    const primaryFile = getPrimaryTaskFile(taskList[0]);

    expect(taskList).toHaveLength(1);
    expect(taskList[0].messageType).toBe("html");
    expect(primaryFile?.url).toBe("https://example.com/preview.html");
    expect(primaryFile?.downloadUrl).toBe("https://example.com/download/preview.html");
  });

  it("缺失 artifact 会继续保留在任务结果里，供历史工作区展示失效态", () => {
    const currentChat = {
      sessionId: "session-missing-1",
      requestId: "req-missing-1",
      query: "查看失效文件",
      files: [],
      forceStop: true,
      loading: false,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData({
      messageType: "task",
      messageId: "missing-msg-1",
      taskId: "task-missing-1",
      taskOrder: 1,
      messageOrder: 1,
      artifactRefs: [
        {
          displayName: "missing-report.md",
          resourceKey: "missing-report",
          missing: true,
          missingReason: "artifact_not_found",
        },
      ],
      resultMap: {
        requestId: "req-missing-1",
        messageId: "missing-msg-1",
        messageType: "markdown",
        messageTime: "1714041600999",
        finish: true,
        isFinal: true,
        resultMap: {
          isFinal: true,
          data: "# 历史报告",
          codeOutput: "# 历史报告",
          fileInfo: [],
        },
      } as unknown as MESSAGE.Task,
    } as unknown as MESSAGE.EventData, currentChat);

    const { taskList } = handleTaskData(currentChat, true, currentChat.multiAgent);
    const primaryFile = getPrimaryTaskFile(taskList[0]);

    expect(taskList).toHaveLength(1);
    expect(primaryFile?.missing).toBe(true);
    expect(primaryFile?.missingReason).toBe("artifact_not_found");
  });

  it("普通历史会话在存在多个任务组时也能重建时间线", () => {
    const snapshotChat = {
      sessionId: "session-history-2",
      requestId: "req-history-2",
      query: "回放普通会话",
      files: [],
      forceStop: false,
      loading: false,
      tasks: [],
      timeline: [],
      multiAgent: {
        tasks: [
          [
            {
              taskId: "task-group-1",
              messageId: "msg-group-1",
              messageTime: "1714041602001",
              messageType: "markdown",
              requestId: "req-history-2",
              finish: true,
              isFinal: true,
              resultMap: {
                isFinal: true,
                data: "# 第一组",
                codeOutput: "# 第一组",
                fileInfo: [],
              },
            } as unknown as MESSAGE.Task,
          ],
          [
            {
              taskId: "task-group-2",
              messageId: "msg-group-2",
              messageTime: "1714041602002",
              messageType: "markdown",
              requestId: "req-history-2",
              finish: true,
              isFinal: true,
              resultMap: {
                isFinal: true,
                data: "# 第二组",
                codeOutput: "# 第二组",
                fileInfo: [],
              },
            } as unknown as MESSAGE.Task,
          ],
        ],
      },
    } as CHAT.ChatItem;

    const result = buildConversationTaskData(snapshotChat, false);

    expect(result.taskList).toHaveLength(2);
    expect(result.currentChat.tasks).toHaveLength(2);
    expect(result.currentChat.tasks[0]?.[0]?.children).toHaveLength(1);
    expect(result.currentChat.tasks[1]?.[0]?.children).toHaveLength(1);
  });

  it("实时普通 replan 在第二个任务开始后仍保留总任务 plan", () => {
    const currentChat = {
      sessionId: "session-plan-1",
      requestId: "req-plan-1",
      query: "执行普通 replan",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData({
      messageType: "plan",
      messageId: "plan-msg-1",
      taskId: "task-plan-1",
      taskOrder: 1,
      messageOrder: 1,
      resultMap: {
        requestId: "req-plan-1",
        messageId: "plan-msg-1",
        messageType: "plan",
        messageTime: "1714041603000",
        finish: false,
        isFinal: true,
        title: "普通 replan",
        stages: ["阶段一", "阶段二"],
        steps: ["步骤一", "步骤二"],
        stepStatus: ["completed", "in_progress"],
        notes: ["已完成", ""],
      } as unknown as MESSAGE.Task,
    } as unknown as MESSAGE.EventData, currentChat);

    combineData({
      messageType: "task",
      messageId: "task-msg-2",
      taskId: "task-group-2",
      taskOrder: 2,
      messageOrder: 1,
      resultMap: {
        requestId: "req-plan-1",
        messageId: "task-msg-2",
        messageType: "task",
        messageTime: "1714041603001",
        finish: false,
        isFinal: true,
        task: "步骤二",
      } as unknown as MESSAGE.Task,
    } as unknown as MESSAGE.EventData, currentChat);

    const result = handleTaskData(currentChat, false, currentChat.multiAgent);

    expect(result.plan?.title).toBe("普通 replan");
    expect(result.currentChat.plan?.title).toBe("普通 replan");
    expect(result.currentChat.planList).toHaveLength(2);
  });

  it("多轮 replan 会按 plannerRoundId 保留完整 planner history，并同步 latest alias", () => {
    const currentChat = {
      sessionId: "session-planner-1",
      requestId: "req-planner-1",
      query: "执行多轮 replan",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createPlannerThoughtEvent({
      plannerRoundId: "planner-round-1",
      planThought: "先收集背景信息",
    }), currentChat);
    combineData(createPlannerPlanEvent({
      plannerRoundId: "planner-round-1",
      title: "第一轮计划",
      steps: ["收集资料"],
      stepStatus: ["in_progress"],
    }), currentChat);
    combineData(createPlannerThoughtEvent({
      plannerRoundId: "planner-round-2",
      planThought: "根据新信息重排计划",
    }), currentChat);
    combineData(createPlannerPlanEvent({
      plannerRoundId: "planner-round-2",
      title: "第二轮计划",
      steps: ["收集资料", "输出总结"],
      stepStatus: ["completed", "in_progress"],
    }), currentChat);

    expect(currentChat.multiAgent.plannerRounds).toHaveLength(2);
    expect(currentChat.multiAgent.plannerRounds?.map((item) => item.plannerRoundId)).toEqual([
      "planner-round-1",
      "planner-round-2",
    ]);
    expect(currentChat.multiAgent.plannerRounds?.[0]?.planThought).toBe("先收集背景信息");
    expect(currentChat.multiAgent.plannerRounds?.[0]?.plan?.title).toBe("第一轮计划");
    expect(currentChat.multiAgent.plannerRounds?.[1]?.planThought).toBe("根据新信息重排计划");
    expect(currentChat.multiAgent.plan_thought).toBe("根据新信息重排计划");
    expect(currentChat.multiAgent.plan?.title).toBe("第二轮计划");
    expect(currentChat.thought).toBe("根据新信息重排计划");
  });

  it("task-wrapped plan 会写回同一 planner round，而不是覆盖旧轮次", () => {
    const currentChat = {
      sessionId: "session-planner-2",
      requestId: "req-planner-1",
      query: "恢复 task-wrapped plan",
      files: [],
      forceStop: false,
      loading: false,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createPlannerThoughtEvent({
      plannerRoundId: "planner-round-legacy",
      planThought: "先规划主流程",
    }), currentChat);
    combineData(createTaskWrappedPlanEvent({
      plannerRoundId: "planner-round-legacy",
      title: "主流程计划",
      steps: ["步骤一", "步骤二"],
      stepStatus: ["completed", "in_progress"],
    }), currentChat);

    expect(currentChat.multiAgent.plannerRounds).toHaveLength(1);
    expect(currentChat.multiAgent.plannerRounds?.[0]?.plannerRoundId).toBe(
      "planner-round-legacy"
    );
    expect(currentChat.multiAgent.plannerRounds?.[0]?.planThought).toBe("先规划主流程");
    expect(currentChat.multiAgent.plannerRounds?.[0]?.plan?.title).toBe("主流程计划");
    expect(currentChat.multiAgent.plan?.title).toBe("主流程计划");
    expect(currentChat.multiAgent.plan_thought).toBe("先规划主流程");
  });
});

describe("chat file task title", () => {
  it("uses primary file name fallback for file get history task", () => {
    const task = {
      messageType: "file",
      resultMap: {
        command: "读取文件",
        primaryFileName: "风险日报.md",
      },
    } as unknown as CHAT.Task;

    expect(buildAction(task)).toMatchObject({
      action: "读取文件",
      name: "风险日报.md",
    });
  });
});
