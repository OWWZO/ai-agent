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
import { resolveTaskToolArg } from "@/components/Dialogue/tools/toolTaskAdapter";
import { getPrimaryTaskFile } from "./taskArtifacts";

type DeepSearchStage = "extend" | "search" | "report";

describe("流式工具参数", () => {
  it("优先展示尚未闭合的累计 argumentsText", () => {
    const task = {
      messageType: "tool_call",
      resultMap: {
        toolName: "workspace_grep",
        status: "streaming",
        argumentsText: '{"query":"spring',
      },
    } as CHAT.Task;

    expect(resolveTaskToolArg(task)).toBe('{"query":"spring');
  });

  it("tool_call_delta 多帧应合并为单卡且 argumentsRaw 递增，running 后仍保留 args", () => {
    const currentChat = {
      sessionId: "session-stream-args",
      requestId: "req-stream-args",
      query: "写一段代码",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    const frame = (opts: {
      messageId: string;
      toolCallId: string;
      streamToolKey: string;
      argumentsText: string;
      messageType?: string;
      status?: string;
      isFinal?: boolean;
      input?: Record<string, unknown>;
    }): MESSAGE.EventData => {
      const innerType = opts.messageType || "tool_call_delta";
      return {
        messageType: "task",
        messageId: opts.messageId,
        taskId: "task-stream-args",
        taskOrder: 1,
        messageOrder: 1,
        resultMap: {
          requestId: "req-stream-args",
          messageId: opts.messageId,
          messageType: innerType,
          messageTime: "1714041600999",
          finish: Boolean(opts.isFinal),
          isFinal: Boolean(opts.isFinal),
          resultMap: {
            messageType: innerType,
            isFinal: Boolean(opts.isFinal),
            status: opts.status || "streaming",
            toolName: "code_execute",
            toolCallId: opts.toolCallId,
            streamToolKey: opts.streamToolKey,
            argumentsText: opts.argumentsText,
            argumentsRaw: opts.argumentsText,
            summary: "正在生成 code_execute 参数…",
            ...(opts.input ? { input: opts.input } : {}),
          },
        },
      } as unknown as MESSAGE.EventData;
    };

    const streamKey = "stream-tool-abc";
    combineData(
      frame({
        messageId: streamKey,
        toolCallId: streamKey,
        streamToolKey: streamKey,
        argumentsText: '{"code":"print(1',
      }),
      currentChat
    );
    combineData(
      frame({
        messageId: streamKey,
        toolCallId: "call-real-1",
        streamToolKey: streamKey,
        argumentsText: '{"code":"print(123)"}',
      }),
      currentChat
    );
    combineData(
      frame({
        messageId: streamKey,
        toolCallId: "call-real-1",
        streamToolKey: streamKey,
        argumentsText: '{"code":"print(123)"}',
        messageType: "tool_call",
        status: "running",
        input: { code: "print(123)" },
      }),
      currentChat
    );

    expect(currentChat.multiAgent.tasks).toHaveLength(1);
    expect(currentChat.multiAgent.tasks[0]).toHaveLength(1);
    const card = currentChat.multiAgent.tasks[0][0];
    expect(card.messageType).toBe("tool_call");
    expect(card.messageId).toBe(streamKey);
    expect(card.resultMap?.status).toBe("running");
    expect(card.resultMap?.toolCallId).toBe("call-real-1");
    expect(card.resultMap?.argsStreaming).toBe(false);
    expect(card.resultMap?.argumentsRaw).toContain("print(123)");
    expect(resolveTaskToolArg(card as CHAT.Task)).toContain("print(123)");

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);
    expect(taskList).toHaveLength(1);
    expect(resolveTaskToolArg(taskList[0])).toContain("print(123)");
  });
});

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

function createToolCallEvent(options?: {
  messageId?: string;
  taskId?: string;
  status?: string;
  toolName?: string;
  toolCallId?: string;
  toolInvocationId?: string;
  streamToolKey?: string;
  summary?: string;
  input?: Record<string, unknown>;
  isFinal?: boolean;
  parentToolUseId?: string;
}): MESSAGE.EventData {
  return {
    messageType: "task",
    messageId: options?.messageId || "tool-call-msg-1",
    taskId: options?.taskId || "task-tool-call-1",
    taskOrder: 1,
    messageOrder: 1,
    resultMap: {
      requestId: "req-tool-call-1",
      messageId: options?.messageId || "tool-call-msg-1",
      messageType: "tool_call",
      messageTime: "1714041600555",
      finish: Boolean(options?.isFinal),
      isFinal: Boolean(options?.isFinal),
      resultMap: {
        messageType: "tool_call",
        isFinal: Boolean(options?.isFinal),
        status: options?.status || "running",
        toolName: options?.toolName || "file_tool",
        toolCallId: options?.toolCallId || "tool-call-file-001",
        toolInvocationId: options?.toolInvocationId || "1001",
        ...(options?.streamToolKey
          ? { streamToolKey: options.streamToolKey }
          : {}),
        summary: options?.summary || "正在调用 file_tool",
        input: options?.input || {
          command: "get",
          fileName: "风险日报.md"
        },
        ...(options?.parentToolUseId
          ? { parentToolUseId: options.parentToolUseId }
          : {}),
      },
    } as unknown as MESSAGE.Task,
  } as unknown as MESSAGE.EventData;
}

function createFileEvent(options?: {
  messageId?: string;
  taskId?: string;
  toolCallId?: string | null;
  command?: string;
  fileName?: string;
  isFinal?: boolean;
}): MESSAGE.EventData {
  const messageId = options?.messageId || "file-msg-1";
  const taskId = options?.taskId || "task-tool-call-1";
  const fileName = options?.fileName || "风险日报.md";
  const isFinal = options?.isFinal ?? true;

  return {
    messageType: "task",
    messageId,
    taskId,
    taskOrder: 1,
    messageOrder: 2,
    resultMap: {
      requestId: "req-tool-call-1",
      messageId,
      messageType: "file",
      messageTime: "1714041600666",
      finish: isFinal,
      isFinal,
      resultMap: {
        isFinal,
        command: options?.command || "读取文件",
        ...(options?.toolCallId === null
          ? {}
          : { toolCallId: options?.toolCallId || "tool-call-file-001" }),
        fileInfo: [
          {
            fileName,
            ossUrl: "https://example.com/download/risk.md",
            domainUrl: "https://example.com/preview/risk.md",
            fileSize: 128,
          },
        ],
      },
    } as unknown as MESSAGE.Task,
  } as unknown as MESSAGE.EventData;
}

function createToolResultEvent(options?: {
  messageId?: string;
  taskId?: string;
  toolCallId?: string | null;
  toolName?: string;
  query?: string;
}): MESSAGE.EventData {
  const messageId = options?.messageId || "tool-result-msg-1";
  const taskId = options?.taskId || "task-tool-call-1";

  return {
    messageType: "task",
    messageId,
    taskId,
    taskOrder: 1,
    messageOrder: 2,
    resultMap: {
      requestId: "req-tool-call-1",
      messageId,
      messageType: "tool_result",
      messageTime: "1714041600777",
      finish: true,
      isFinal: true,
      toolResult: {
        toolName: options?.toolName || "web_search",
        toolResult: "{\"data\":[]}",
        toolParam: {query: options?.query || "风险日报",},
        ...(options?.toolCallId === null
          ? {}
          : { toolCallId: options?.toolCallId || "tool-call-file-001" }),
      },
    } as unknown as MESSAGE.Task,
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
    expect(currentChat.multiAgent.plannerRounds?.[0]?.planThoughtFinal).toBe(true);
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

  it("plan_thought 非 final 时应追加到同一 plannerRound", () => {
    const chat = createChatItem(createDeepSearchTask("search"));
    chat.multiAgent.plannerRounds = [];

    combineData({
      messageType: "plan_thought",
      messageId: "thought-msg-1",
      taskId: "planner-task-1",
      resultMap: {
        plannerRoundId: "round-1",
        planThought: "第一段",
        isFinal: false,
      },
    } as unknown as MESSAGE.EventData, chat);

    combineData({
      messageType: "plan_thought",
      messageId: "thought-msg-1",
      taskId: "planner-task-1",
      resultMap: {
        plannerRoundId: "round-1",
        planThought: "第二段",
        isFinal: false,
      },
    } as unknown as MESSAGE.EventData, chat);

    expect(chat.multiAgent.plannerRounds?.[0]?.planThought).toBe("第一段第二段");
    expect(chat.multiAgent.plannerRounds?.[0]?.planThoughtFinal).toBe(false);
  });
});

describe("chat reasoning progress", () => {
  const createReasoningEvent = (
    content: string,
    isFinal: boolean,
    options?: { messageId?: string; taskId?: string }
  ) =>
    ({
      messageType: "task",
      messageId: options?.messageId || "reasoning-message-1",
      taskId: options?.taskId || "reasoning-task-1",
      taskOrder: 1,
      messageOrder: 1,
      resultMap: {
        requestId: "req-reasoning-1",
        messageId: options?.messageId || "reasoning-message-1",
        messageType: "llm_reasoning",
        messageTime: "1714041607000",
        isFinal,
        finish: isFinal,
        reasoningContent: content,
      },
    }) as unknown as MESSAGE.EventData;

  const createToolThoughtEvent = (
    content: string,
    isFinal: boolean,
    options?: { messageId?: string; taskId?: string }
  ) =>
    ({
      messageType: "task",
      messageId: options?.messageId || "thought-message-1",
      taskId: options?.taskId || "reasoning-task-1",
      taskOrder: 1,
      messageOrder: 2,
      resultMap: {
        requestId: "req-reasoning-1",
        messageId: options?.messageId || "thought-message-1",
        messageType: "tool_thought",
        messageTime: "1714041608000",
        isFinal,
        finish: isFinal,
        toolThought: content,
      },
    }) as unknown as MESSAGE.EventData;

  it("llm_reasoning 最终增量会同步任务终态，停止深度思考闪光", () => {
    const chat = {
      sessionId: "session-reasoning-1",
      requestId: "req-reasoning-1",
      query: "整理数据",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createReasoningEvent("先清理字段", false), chat);
    combineData(createReasoningEvent("再输出报告", true), chat);

    const reasoning = chat.multiAgent.tasks[0]?.[0];
    expect(reasoning?.toolThought).toBe("再输出报告");
    expect(reasoning?.finish).toBe(true);
    expect(reasoning?.isFinal).toBe(true);
    expect(reasoning?.resultMap.isFinal).toBe(true);
  });

  it("llm_retry 只更新 tip，不写入任务列表", () => {
    const chat = {
      sessionId: "session-retry-1",
      requestId: "req-retry-1",
      query: "测试重试",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(
      {
        messageId: "msg-retry-1",
        taskId: "task-retry-1",
        messageType: "task",
        messageTime: "1",
        resultMap: {
          messageType: "llm_retry",
          isFinal: false,
          resultMap: {
            attempt: 2,
            maxAttempts: 6,
            message: "模型请求失败，正在重试（第 2/6 次）…",
          },
        },
      } as unknown as MESSAGE.EventData,
      chat
    );

    expect(chat.tip).toBe("模型请求失败，正在重试（第 2/6 次）…");
    expect(chat.multiAgent.tasks).toEqual([]);
  });

  it("助手过程文一开始就收口深度思考，迟到 reasoning 只补字不重开流式", () => {
    const chat = {
      sessionId: "session-reasoning-2",
      requestId: "req-reasoning-2",
      query: "整理数据",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createReasoningEvent("先分析需求", false), chat);
    combineData(createReasoningEvent("再拆步骤", false), chat);

    let reasoning = chat.multiAgent.tasks[0]?.[0];
    expect(reasoning?.toolThought).toBe("先分析需求再拆步骤");
    expect(reasoning?.isFinal).toBe(false);

    combineData(createToolThoughtEvent("开始调用工具", false), chat);

    reasoning = chat.multiAgent.tasks[0]?.[0];
    expect(reasoning?.messageType).toBe("llm_reasoning");
    expect(reasoning?.isFinal).toBe(true);
    expect(reasoning?.resultMap.isFinal).toBe(true);
    expect(reasoning?.toolThought).toBe("先分析需求再拆步骤");

    combineData(createReasoningEvent("并核对边界", false), chat);
    reasoning = chat.multiAgent.tasks[0]?.[0];
    expect(reasoning?.toolThought).toBe("先分析需求再拆步骤并核对边界");
    expect(reasoning?.isFinal).toBe(true);

    combineData(createReasoningEvent("先分析需求再拆步骤并核对边界与风险", true), chat);
    reasoning = chat.multiAgent.tasks[0]?.[0];
    expect(reasoning?.toolThought).toBe("先分析需求再拆步骤并核对边界与风险");
    expect(reasoning?.isFinal).toBe(true);
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

  it("子 Agent 工具事件应嵌套到父 Agent 卡片 children 下", () => {
    const currentChat = {
      sessionId: "session-subagent-nest-1",
      requestId: "req-subagent-nest-1",
      query: "探索代码",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "agent-call-1",
      taskId: "task-nest-1",
      toolCallId: "parent-agent-call",
      toolName: "Agent",
      input: {
        description: "搜索 API",
        prompt: "find endpoints",
        subagent_type: "Explore",
      },
      status: "running",
    }), currentChat);

    combineData(createToolCallEvent({
      messageId: "child-grep-1",
      taskId: "task-nest-1",
      toolCallId: "child-grep-call",
      toolName: "workspace_grep",
      input: { pattern: "Controller" },
      status: "running",
      parentToolUseId: "parent-agent-call",
    }), currentChat);

    const { taskList, currentChat: renderedChat } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );

    // 顶层只应有 Agent，子工具不进 taskList
    expect(taskList).toHaveLength(1);
    expect(taskList[0].resultMap?.toolName || taskList[0].toolResult?.toolName).toBe("Agent");

    const timelineChildren = renderedChat.tasks[0]?.[0]?.children || [];
    expect(timelineChildren).toHaveLength(1);
    expect(timelineChildren[0].resultMap?.toolName).toBe("Agent");
    expect(timelineChildren[0].children?.length).toBe(1);
    expect(timelineChildren[0].children?.[0].resultMap?.toolName).toBe("workspace_grep");
  });

  it("子事件先于父 Agent 时仍应嵌套，不进主时间线", () => {
    const currentChat = {
      sessionId: "session-subagent-order-1",
      requestId: "req-subagent-order-1",
      query: "探索代码",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "child-grep-first",
      taskId: "task-order-1",
      toolCallId: "child-grep-call",
      toolName: "workspace_grep",
      input: { pattern: "Controller" },
      status: "running",
      parentToolUseId: "parent-agent-call",
    }), currentChat);

    combineData(createToolCallEvent({
      messageId: "agent-call-later",
      taskId: "task-order-1",
      toolCallId: "parent-agent-call",
      toolName: "Agent",
      input: {
        description: "搜索 API",
        prompt: "find endpoints",
        subagent_type: "Explore",
      },
      status: "running",
    }), currentChat);

    const { taskList, currentChat: renderedChat } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );

    expect(taskList).toHaveLength(1);
    expect(taskList[0].resultMap?.toolName || taskList[0].toolResult?.toolName).toBe("Agent");

    const timelineChildren = renderedChat.tasks[0]?.[0]?.children || [];
    expect(timelineChildren).toHaveLength(1);
    expect(timelineChildren[0].resultMap?.toolName).toBe("Agent");
    expect(timelineChildren[0].children?.length).toBe(1);
    expect(timelineChildren[0].children?.[0].resultMap?.toolName).toBe("workspace_grep");
  });

  it("同步 Agent 终态缺少 toolCallId 时仍替换原卡片并保留子工具", () => {
    const currentChat = {
      sessionId: "session-sync-agent-final-1",
      requestId: "req-sync-agent-final-1",
      query: "探索代码",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "sync-agent-message",
      taskId: "task-sync-agent",
      toolCallId: "sync-agent-call",
      toolName: "Agent",
      input: {
        description: "搜索 API",
        prompt: "find endpoints",
        subagent_type: "Explore",
      },
      status: "running",
    }), currentChat);
    combineData(createToolCallEvent({
      messageId: "sync-child-message",
      taskId: "task-sync-agent",
      toolCallId: "sync-child-call",
      toolName: "workspace_grep",
      input: { pattern: "Controller" },
      status: "running",
      parentToolUseId: "sync-agent-call",
    }), currentChat);

    combineData({
      messageType: "task",
      messageId: "sync-agent-message",
      taskId: "task-sync-agent",
      taskOrder: 3,
      messageOrder: 3,
      resultMap: {
        messageType: "tool_result",
        messageId: "sync-agent-message",
        messageTime: "1714041600999",
        isFinal: true,
        finish: true,
        toolResult: {
          toolName: "Agent",
          toolResult: "status=completed\\nagentType=Explore\\n\\nok",
        },
      },
    } as unknown as MESSAGE.EventData, currentChat);

    const { taskList, currentChat: renderedChat } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );

    expect(taskList).toHaveLength(1);
    expect(taskList[0].toolResult?.toolName).toBe("Agent");
    const agent = renderedChat.tasks[0]?.[0]?.children?.[0];
    expect(agent?.toolResult?.toolName).toBe("Agent");
    expect(agent?.children?.[0].resultMap?.toolName).toBe("workspace_grep");
  });

  it("父卡 toolCallId 与子事件 parentToolUseId 别名不一致时仍应挂载", () => {
    const currentChat = {
      sessionId: "session-subagent-alias-1",
      requestId: "req-subagent-alias-1",
      query: "探索代码",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: {
        tasks: [
          [
            {
              messageType: "tool_call",
              messageId: "stream-agent-key",
              messageTime: "1714041600555",
              resultMap: {
                messageType: "tool_call",
                status: "running",
                toolName: "Agent",
                toolCallId: "real-agent-call",
                streamToolKey: "stream-agent-key",
                input: {
                  description: "搜索 API",
                  prompt: "find endpoints",
                  subagent_type: "Explore",
                },
              },
            } as unknown as MESSAGE.Task,
            {
              messageType: "tool_call",
              messageId: "child-grep-alias",
              messageTime: "1714041600556",
              resultMap: {
                messageType: "tool_call",
                status: "running",
                toolName: "workspace_grep",
                toolCallId: "child-grep-call",
                parentToolUseId: "stream-agent-key",
                input: { pattern: "Controller" },
              },
            } as unknown as MESSAGE.Task,
          ],
        ],
      },
    } as CHAT.ChatItem;

    const { taskList, currentChat: renderedChat } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );

    expect(taskList).toHaveLength(1);
    const timelineChildren = renderedChat.tasks[0]?.[0]?.children || [];
    expect(timelineChildren).toHaveLength(1);
    expect(timelineChildren[0].resultMap?.toolName).toBe("Agent");
    expect(timelineChildren[0].children?.length).toBe(1);
    expect(timelineChildren[0].children?.[0].resultMap?.toolName).toBe("workspace_grep");
  });

  it("有 parentToolUseId 但父 Agent 永不出现时，子工具不得进入主时间线", () => {
    const currentChat = {
      sessionId: "session-subagent-orphan-1",
      requestId: "req-subagent-orphan-1",
      query: "探索代码",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "orphan-grep-1",
      taskId: "task-orphan-1",
      toolCallId: "orphan-grep-call",
      toolName: "workspace_grep",
      input: { pattern: "Controller" },
      status: "running",
      parentToolUseId: "missing-parent-agent",
    }), currentChat);

    const { taskList, currentChat: renderedChat } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );

    expect(taskList).toHaveLength(0);
    const timelineChildren = renderedChat.tasks[0]?.[0]?.children || [];
    expect(timelineChildren).toHaveLength(0);
  });

  it("subagent_progress 后父 Agent 仍留在 taskList，且不写入自身 parentToolUseId", () => {
    const currentChat = {
      sessionId: "session-progress-parent-1",
      requestId: "req-progress-parent-1",
      query: "探索代码",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "parent-agent-call",
      taskId: "task-progress-1",
      toolCallId: "parent-agent-call",
      toolName: "Agent",
      input: {
        description: "搜索 API",
        prompt: "find endpoints",
        subagent_type: "Explore",
      },
      status: "running",
    }), currentChat);

    combineData({
      messageType: "task",
      messageId: "prog-1",
      taskId: "task-progress-1",
      taskOrder: 1,
      messageOrder: 2,
      resultMap: {
        messageType: "subagent_progress",
        messageId: "prog-1",
        messageTime: "1714041600500",
        kind: "heartbeat",
        phase: "working",
        status: "running",
        parentToolUseId: "parent-agent-call",
        agentId: "sub-1",
        agentType: "Explore",
        description: "搜索 API",
        elapsedMs: 3000,
      } as unknown as MESSAGE.Task,
    } as unknown as MESSAGE.EventData, currentChat);

    combineData(createToolCallEvent({
      messageId: "child-grep-1",
      taskId: "task-progress-1",
      toolCallId: "child-grep-call",
      toolName: "workspace_grep",
      input: { pattern: "Controller" },
      status: "running",
      parentToolUseId: "parent-agent-call",
    }), currentChat);

    const parentFact = currentChat.multiAgent.tasks[0]?.find(
      (task) =>
        (task.resultMap?.toolCallId || task.messageId) === "parent-agent-call"
    );
    expect(parentFact?.resultMap?.parentToolUseId).toBeUndefined();
    expect(parentFact?.resultMap?.subAgentProgressLines?.length).toBeGreaterThan(0);

    const { taskList, currentChat: renderedChat } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );

    expect(taskList).toHaveLength(1);
    expect(taskList[0].resultMap?.toolName || taskList[0].toolResult?.toolName).toBe("Agent");
    expect(taskList[0].children?.some((child) => child.resultMap?.toolName === "workspace_grep")).toBe(true);
    expect(renderedChat.tasks[0]?.[0]?.children?.[0]?.children?.length).toBe(1);
  });

  it("带 parentToolUseId 的 result 挂到子 Agent 工作区，不覆盖主对话终答", () => {
    const currentChat = {
      sessionId: "session-subagent-result-1",
      requestId: "req-subagent-result-1",
      query: "做个页面",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "agent-call-1",
      taskId: "task-sub-result-1",
      toolCallId: "parent-agent-call",
      toolName: "Agent",
      input: {
        description: "制作交互式 HTML",
        prompt: "make html",
        subagent_type: "general-purpose",
      },
      status: "success",
      isFinal: true,
    }), currentChat);

    combineData({
      messageType: "task",
      messageId: "sub-final-1",
      taskId: "task-sub-result-1",
      taskOrder: 2,
      messageOrder: 1,
      resultMap: {
        requestId: "req-subagent-result-1",
        messageId: "sub-final-1",
        messageType: "result",
        messageTime: "1714041600999",
        finish: true,
        isFinal: true,
        result: "页面已经生成完毕。",
        taskSummary: "页面已经生成完毕。",
        parentToolUseId: "parent-agent-call",
        resultMap: { parentToolUseId: "parent-agent-call" },
      } as unknown as MESSAGE.Task,
    } as unknown as MESSAGE.EventData, currentChat);

    const { currentChat: renderedChat } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );

    expect(renderedChat.conclusion).toBeUndefined();
    const agentCard = renderedChat.tasks[0]?.[0]?.children?.[0];
    expect(agentCard?.resultMap?.toolName || agentCard?.toolResult?.toolName).toBe("Agent");
    expect(agentCard?.children?.some((child) => child.messageType === "result")).toBe(true);
    expect(
      agentCard?.children?.find((child) => child.messageType === "result")?.result
    ).toContain("页面已经生成完毕");
  });

  it("tool_call 阶段会立即生成可见的工具调用占位卡片", () => {
    const currentChat = {
      sessionId: "session-tool-call-1",
      requestId: "req-tool-call-1",
      query: "读取风险日报",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent(), currentChat);

    const { taskList, currentChat: renderedChat } = handleTaskData(
      currentChat,
      false,
      currentChat.multiAgent
    );

    expect(taskList).toHaveLength(1);
    expect(taskList[0].messageType).toBe("tool_call");
    expect(buildAction(taskList[0])).toMatchObject({
      action: "正在调用工具",
      tool: "file_tool",
      name: "风险日报.md",
    });
    expect(renderedChat.tasks[0]?.[0]?.children?.[0]?.messageType).toBe("tool_call");
  });

  it("相同 tool_call messageId 的终态更新应覆盖状态而不是追加新卡片", () => {
    const currentChat = {
      sessionId: "session-tool-call-2",
      requestId: "req-tool-call-2",
      query: "读取风险日报",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "tool-call-msg-2",
      taskId: "task-tool-call-2",
      toolCallId: "tool-call-file-002",
      status: "running",
    }), currentChat);
    combineData(createToolCallEvent({
      messageId: "tool-call-msg-2",
      taskId: "task-tool-call-2",
      toolCallId: "tool-call-file-002",
      status: "success",
      isFinal: true,
      summary: "file_tool 调用完成",
    }), currentChat);

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);

    expect(taskList).toHaveLength(1);
    expect(taskList[0].resultMap.status).toBe("success");
    expect(taskList[0].resultMap.isFinal).toBe(true);
    expect(taskList[0].resultMap.summary).toBe("file_tool 调用完成");
  });

  it("工具结果到达后应替换对应的 tool_call 占位卡片", () => {
    const currentChat = {
      sessionId: "session-tool-call-3",
      requestId: "req-tool-call-3",
      query: "读取风险日报",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "tool-call-msg-3",
      taskId: "task-tool-call-3",
      toolCallId: "tool-call-file-003",
    }), currentChat);
    combineData(createFileEvent({
      messageId: "file-msg-3",
      taskId: "task-tool-call-3",
      toolCallId: "tool-call-file-003",
    }), currentChat);

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);

    expect(taskList).toHaveLength(1);
    expect(taskList[0].messageType).toBe("file");
    expect(buildAction(taskList[0])).toMatchObject({
      action: "读取文件",
      name: "风险日报.md",
    });
  });

  it("WebSearch 和 Webfetch 的完成结果不应继续显示正在调用", () => {
    const webSearchTask = createToolResultEvent({ toolName: "web_search" })
      .resultMap as unknown as CHAT.Task;
    const webfetchTask = createToolResultEvent({ toolName: "webfetch" })
      .resultMap as unknown as CHAT.Task;

    expect(buildAction(webSearchTask)).toMatchObject({
      action: "工具调用完成",
      tool: "web_search",
    });
    expect(buildAction(webfetchTask)).toMatchObject({
      action: "工具调用完成",
      tool: "webfetch",
    });
  });

  it("已有工具结果卡片后，tool_call 终态回包不应再次追加新卡片", () => {
    const currentChat = {
      sessionId: "session-tool-call-4",
      requestId: "req-tool-call-4",
      query: "读取风险日报",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolCallEvent({
      messageId: "tool-call-msg-4",
      taskId: "task-tool-call-4",
      toolCallId: "tool-call-file-004",
      status: "running",
    }), currentChat);
    combineData(createToolResultEvent({
      messageId: "tool-result-msg-4",
      taskId: "task-tool-call-4",
      toolCallId: "tool-call-file-004",
      toolName: "web_search",
      query: "风险日报",
    }), currentChat);
    combineData(createToolCallEvent({
      messageId: "tool-call-msg-4",
      taskId: "task-tool-call-4",
      toolCallId: "tool-call-file-004",
      status: "success",
      isFinal: true,
      summary: "web_search 调用完成",
    }), currentChat);

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);

    expect(taskList).toHaveLength(1);
    expect(taskList[0].messageType).toBe("tool_result");
    expect(taskList[0].toolResult?.toolCallId).toBe("tool-call-file-004");
  });

  it("image_generation 缺少 toolCallId 时不应错误合并到其他结果卡片", () => {
    const currentChat = {
      sessionId: "session-image-tool-1",
      requestId: "req-image-tool-1",
      query: "生成两张不同图片",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    } as CHAT.ChatItem;

    combineData(createToolResultEvent({
      messageId: "image-tool-result-1",
      taskId: "task-image-tool-1",
      toolCallId: "tool-call-image-001",
      toolName: "image_generation_tool",
      query: "第一张图",
    }), currentChat);
    combineData(createFileEvent({
      messageId: "image-file-1",
      taskId: "task-image-tool-1",
      toolCallId: "tool-call-image-001",
      command: "生成图片",
      fileName: "first-image.png",
    }), currentChat);

    combineData(createToolResultEvent({
      messageId: "image-tool-result-2",
      taskId: "task-image-tool-1",
      toolCallId: "tool-call-image-002",
      toolName: "image_generation_tool",
      query: "第二张图",
    }), currentChat);
    combineData(createFileEvent({
      messageId: "image-file-2",
      taskId: "task-image-tool-1",
      toolCallId: null,
      command: "生成图片",
      fileName: "second-image.png",
    }), currentChat);

    const { taskList } = handleTaskData(currentChat, false, currentChat.multiAgent);

    expect(taskList).toHaveLength(3);
    expect(taskList[0].messageType).toBe("tool_result");
    expect(getPrimaryTaskFile(taskList[0])?.name).toBe("first-image.png");
    expect(taskList[1].messageType).toBe("tool_result");
    expect(getPrimaryTaskFile(taskList[1])).toBeUndefined();
    expect(taskList[2].messageType).toBe("file");
    expect(getPrimaryTaskFile(taskList[2])?.name).toBe("second-image.png");
  });
});

describe("chat context_usage", () => {
  const createChat = () =>
    ({
      sessionId: "session-ctx-1",
      requestId: "req-ctx-1",
      query: "你好",
      files: [],
      forceStop: false,
      loading: true,
      tasks: [],
      timeline: [],
      multiAgent: { tasks: [] },
    }) as CHAT.ChatItem;

  it("解析嵌套 resultMap 中的分段占用（实时 SSE）", () => {
    const chat = createChat();
    combineData(
      {
        messageOrder: 1,
        messageType: "task",
        messageId: "msg-ctx-1",
        taskId: "task-ctx-1",
        taskOrder: 1,
        resultMap: {
          messageType: "context_usage",
          requestId: "req-ctx-1",
          messageId: "msg-ctx-1",
          resultMap: {
            sys: 1200,
            tools: 3400,
            history: 18000,
            files: 0,
            max: 200000,
            used: 22600,
            source: "estimate",
            messageType: "context_usage",
          },
        },
      } as unknown as MESSAGE.EventData,
      chat
    );

    expect(chat.contextUsage).toEqual({
      sys: 1200,
      tools: 3400,
      history: 18000,
      files: 0,
      max: 200000,
      used: 22600,
      promptTokens: undefined,
      completionTokens: undefined,
      source: "estimate",
    });
  });

  it("兼容扁平 resultMap（无嵌套）", () => {
    const chat = createChat();
    combineData(
      {
        messageOrder: 1,
        messageType: "task",
        messageId: "msg-ctx-2",
        taskId: "task-ctx-1",
        taskOrder: 2,
        resultMap: {
          messageType: "context_usage",
          sys: 800,
          tools: 1000,
          history: 5000,
          files: 100,
          max: 128000,
          used: 6900,
          promptTokens: 6900,
          source: "measured",
        },
      } as unknown as MESSAGE.EventData,
      chat
    );

    expect(chat.contextUsage?.sys).toBe(800);
    expect(chat.contextUsage?.used).toBe(6900);
    expect(chat.contextUsage?.source).toBe("measured");
    expect(chat.contextUsage?.promptTokens).toBe(6900);
  });
});
