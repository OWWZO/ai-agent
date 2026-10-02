import { describe, expect, it } from "vitest";
import type {
  ConversationHistoryDetail,
  ConversationReplayFrame,
} from "@/services/agentConversation";

import {
  hydrateConversationFromSummaryPage,
  hydrateConversationFromReplayFrames,
  hydrateRunReplay,
  hydrateRunSummary,
  isHistoryDetailEmpty,
  mergeConversationHistoryPage,
  mergeRunReplayIntoConversation,
  toConversationHistoryTitle,
} from "./conversationHistory";
import { buildConversationTaskData } from "./chat";
import {
  pickFeaturedDeliveryFiles,
  resolveTaskSummaryText,
} from "@/components/Dialogue/contentHelpers";
import { collectChatArtifactFiles } from "./markdownArtifacts";

function createReplayFrame(eventData: MESSAGE.EventData): ConversationReplayFrame {
  return {
    reqId: "req-history-001",
    status: "success",
    finished: true,
    resultMap: {
      agentType: "history",
      multiAgent: {},
      eventData,
    },
  };
}

function createPlanEvent(): MESSAGE.EventData {
  return {
    taskId: "task-plan-1",
    taskOrder: 1,
    messageType: "plan",
    messageOrder: 1,
    messageId: "msg-plan-1",
    resultMap: {
      title: "项目执行计划",
      stages: ["信息收集", "总结输出"],
      steps: ["搜集资料", "整理结论"],
      stepStatus: ["in_progress", "not_started"],
      notes: ["", ""],
    } as unknown as MESSAGE.Task,
  };
}

function createRoundPlanEvent(roundId: string, title: string, steps: string[]): MESSAGE.EventData {
  return {
    taskId: `task-${roundId}`,
    taskOrder: 1,
    messageType: "plan",
    messageOrder: 1,
    messageId: `msg-plan-${roundId}`,
    resultMap: {
      title,
      stages: steps,
      steps,
      stepStatus: steps.map((_, index) =>
        index === steps.length - 1 ? "in_progress" : "completed"
      ),
      notes: steps.map(() => ""),
      plannerRoundId: roundId,
    } as unknown as MESSAGE.Task,
  };
}

function createToolThoughtEvent(toolThought: string): MESSAGE.EventData {
  return {
    taskId: "task-1",
    taskOrder: 1,
    messageType: "task",
    messageOrder: 1,
    messageId: "msg-tool-thought-1",
    resultMap: {
      requestId: "req-history-001",
      messageId: "msg-tool-thought-1",
      messageType: "tool_thought",
      messageTime: "1714620000000",
      isFinal: true,
      finish: false,
      toolThought,
    } as unknown as MESSAGE.Task,
  };
}

function createPlanThoughtEvent(planThought: string): MESSAGE.EventData {
  return {
    taskId: "task-plan-1",
    taskOrder: 1,
    messageType: "plan_thought",
    messageOrder: 2,
    messageId: "msg-plan-thought-1",
    resultMap: {
      requestId: "req-history-001",
      messageId: "msg-plan-thought-1",
      messageType: "plan_thought",
      messageTime: "1714620000500",
      isFinal: true,
      finish: false,
      planThought,
    } as unknown as MESSAGE.Task,
  };
}

function createRoundPlanThoughtEvent(roundId: string, planThought: string): MESSAGE.EventData {
  return {
    taskId: `task-${roundId}`,
    taskOrder: 1,
    messageType: "plan_thought",
    messageOrder: 2,
    messageId: `msg-plan-thought-${roundId}`,
    resultMap: {
      requestId: "req-history-001",
      messageId: `msg-plan-thought-${roundId}`,
      messageType: "plan_thought",
      messageTime: "1714620000500",
      isFinal: true,
      finish: false,
      planThought,
      plannerRoundId: roundId,
    } as unknown as MESSAGE.Task,
  };
}

function createToolResultEvent(summary: string): MESSAGE.EventData {
  return {
    taskId: "task-1",
    taskOrder: 2,
    messageType: "task",
    messageOrder: 2,
    messageId: "msg-tool-result-1",
    resultMap: {
      requestId: "req-history-001",
      messageId: "msg-tool-result-1",
      messageType: "tool_result",
      messageTime: "1714620001000",
      isFinal: true,
      finish: false,
      toolResult: {
        toolName: "read_tool",
        toolResult: summary,
      },
    } as unknown as MESSAGE.Task,
  };
}

function createResultEvent(result: string): MESSAGE.EventData {
  return {
    taskId: "task-1",
    taskOrder: 3,
    messageType: "task",
    messageOrder: 3,
    messageId: "msg-result-1",
    resultMap: {
      requestId: "req-history-001",
      messageId: "msg-result-1",
      messageType: "result",
      messageTime: "1714620002000",
      isFinal: true,
      finish: true,
      result,
      taskSummary: result,
    } as unknown as MESSAGE.Task,
  };
}

describe("conversationHistory hydrate", () => {
  it("summary hydrate keeps a lightweight shell", () => {
    const history = hydrateConversationFromSummaryPage({
      sessionId: "session-summary-001",
      title: "摘要会话",
      status: "SUCCESS",
      deepThink: false,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      runs: [
        {
          requestId: "req-summary-001",
          status: "SUCCESS",
          queryPreview: "查看摘要",
          finalSummaryPreview: "这是摘要结论",
          hasReplay: true,
        },
      ],
      nextCursor: "cursor-001",
      hasMore: true,
    });

    expect(history.historyNextCursor).toBe("cursor-001");
    expect(history.historyHasMore).toBe(true);
    expect(history.chatList[0].replayLoaded).toBe(false);
    expect(history.chatList[0].multiAgent.plan).toBeUndefined();
    expect(history.chatList[0].conclusion?.result).toBe("这是摘要结论");
  });

  it("hydrates the complete final answer from the summary page", () => {
    const fullAnswer = `${"中秋最好的团圆是回家（约".repeat(25)}全文末尾`;
    const history = hydrateConversationFromSummaryPage({
      sessionId: "session-full-summary-001",
      title: "完整终答",
      status: "SUCCESS",
      deepThink: false,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      runs: [
        {
          requestId: "req-full-summary-001",
          status: "SUCCESS",
          queryPreview: "读取完整终答",
          finalSummaryPreview: `${fullAnswer.slice(0, 239)}…`,
          finalSummaryText: fullAnswer,
          hasReplay: true,
        },
      ],
    });

    expect(history.chatList[0].replayLoaded).toBe(false);
    expect(resolveTaskSummaryText(history.chatList[0].conclusion)).toBe(fullAnswer);
  });

  it("hydrates ledger run duration for summary and replay shells", () => {
    const detail = {
      sessionId: "session-timing-001",
      title: "时间信息",
      status: "SUCCESS",
      deepThink: false,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      runs: [],
    };
    const summaryChat = hydrateRunSummary(detail, {
      requestId: "req-timing-summary-001",
      status: "SUCCESS",
      queryPreview: "摘要",
      hasReplay: true,
      durationMs: 4200,
    });
    expect(summaryChat.runDurationMs).toBe(4200);
    expect(summaryChat.runTimingSource).toBe("ledger");

    const replayEvent = createResultEvent("回放结论");
    replayEvent.timing = {
      startedAt: "2026-09-26T10:00:00Z",
      finishedAt: "2026-09-26T10:00:02Z",
      durationMs: 2000,
      source: "ledger",
    };
    const replayChat = hydrateRunReplay(detail, {
      requestId: "req-timing-replay-001",
      status: "SUCCESS",
      queryText: "回放",
      durationMs: 6200,
      replayFrames: [createReplayFrame(replayEvent)],
    });
    expect(replayChat.runDurationMs).toBe(6200);
    expect(replayChat.runTimingSource).toBe("ledger");
    expect(replayChat.multiAgent.tasks[0]?.[0]?.timing).toEqual(
      replayEvent.timing
    );
  });

  it("merges summary pages by requestId without replacing loaded runs", () => {
    const initial = hydrateConversationFromReplayFrames({
      sessionId: "session-page-001",
      title: "分页会话",
      status: "SUCCESS",
      deepThink: false,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      runs: [
        {
          requestId: "req-page-001",
          status: "SUCCESS",
          queryText: "第一轮",
          finalSummaryText: "第一轮结论",
          replayFrames: [createReplayFrame(createResultEvent("第一轮结论"))],
        },
      ],
    });
    const loadedRun = initial.chatList[0];

    const merged = mergeConversationHistoryPage(initial, {
      sessionId: "session-page-001",
      title: "分页会话",
      status: "SUCCESS",
      deepThink: false,
      runCount: 2,
      finishedRunCount: 2,
      failedRunCount: 0,
      runs: [
        {
          requestId: "req-page-001",
          status: "SUCCESS",
          queryPreview: "第一轮旧摘要",
          finalSummaryPreview: "不应覆盖 rich replay",
          hasReplay: true,
        },
        {
          requestId: "req-page-002",
          status: "SUCCESS",
          queryPreview: "第二轮",
          finalSummaryPreview: "第二轮结论",
          hasReplay: true,
        },
      ],
      nextCursor: null,
      hasMore: false,
    });

    expect(merged.chatList.map((item) => item.requestId)).toEqual([
      "req-page-001",
      "req-page-002",
    ]);
    expect(merged.chatList[0]).toBe(loadedRun);
    expect(merged.chatList[1].replayLoaded).toBe(false);
    expect(merged.historyHasMore).toBe(false);
  });

  it("replaces only the target run and appends a replay missing from summaries", () => {
    const conversation = hydrateConversationFromSummaryPage({
      sessionId: "session-merge-001",
      title: "单 run 回放",
      status: "SUCCESS",
      deepThink: false,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      runs: [
        {
          requestId: "req-merge-001",
          status: "SUCCESS",
          queryPreview: "已有摘要",
          finalSummaryPreview: "旧摘要",
          hasReplay: true,
        },
      ],
    });

    const replay = {
      requestId: "req-merge-002",
      status: "SUCCESS",
      queryText: "漏掉的 run",
      finalSummaryText: "完整结论",
      replayFrames: [createReplayFrame(createResultEvent("完整结论"))],
    };
    const merged = mergeRunReplayIntoConversation(conversation, replay);

    expect(merged.chatList.map((item) => item.requestId)).toEqual([
      "req-merge-001",
      "req-merge-002",
    ]);
    expect(merged.chatList[0].conclusion?.result).toBe("旧摘要");
    expect(merged.chatList[1].replayLoaded).toBe(true);
    expect(merged.chatList[1].conclusion?.result).toBe("完整结论");
  });

  it("rebuilds chat list from replay frames and restores conclusion", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-history-001",
      title: "项目风险分析",
      status: "FAILED",
      deepThink: false,
      runCount: 2,
      finishedRunCount: 1,
      failedRunCount: 1,
      startedAt: "2026-05-02T10:00:00",
      lastActiveAt: "2026-05-02T10:06:00",
      runs: [
        {
          requestId: "req-history-001",
          status: "FAILED",
          queryText: "先分析项目风险",
          finalSummaryText: "建议先收敛风险清单",
          startedAt: "2026-05-02T10:00:00",
          finishedAt: "2026-05-02T10:01:00",
          replayFrames: [
            createReplayFrame(createPlanEvent()),
            createReplayFrame(createToolThoughtEvent("先读取已有资料")),
            createReplayFrame(createToolResultEvent("已整理现有资料")),
            createReplayFrame(createResultEvent("建议先收敛风险清单")),
          ],
        },
      ],
    });

    expect(history.sessionId).toBe("session-history-001");
    expect(history.title).toBe("项目风险分析");
    expect(history.productType).toBe("task");
    expect(history.chatList).toHaveLength(1);
    expect(history.chatList[0].query).toBe("先分析项目风险");
    expect(history.chatList[0].requestId).toBe("req-history-001");
    expect(history.chatList[0].multiAgent.plan?.title).toBe("项目执行计划");
    expect(history.chatList[0].conclusion?.result).toBe("建议先收敛风险清单");
    expect(history.chatList[0].multiAgent.tasks[0][0].messageType).toBe(
      "tool_thought"
    );

    const taskData = buildConversationTaskData(history.chatList[0], history.deepThink);
    expect(taskData.taskList).toHaveLength(2);
    expect(taskData.taskList[0].messageType).toBe("tool_result");
    expect(taskData.taskList[1].messageType).toBe("result");
  });

  it("hydrates derived thought and timeline fields for deep think history", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-history-deep-001",
      title: "深度研究历史",
      status: "SUCCESS",
      deepThink: true,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      startedAt: "2026-05-02T10:10:00",
      lastActiveAt: "2026-05-02T10:16:00",
      runs: [
        {
          requestId: "req-history-001",
          status: "SUCCESS",
          queryText: "帮我做深度研究",
          finalSummaryText: "这是最终结论",
          startedAt: "2026-05-02T10:10:00",
          finishedAt: "2026-05-02T10:16:00",
          replayFrames: [
            createReplayFrame(createPlanEvent()),
            createReplayFrame(createPlanThoughtEvent("先规划研究路径")),
            createReplayFrame(createToolThoughtEvent("先读取已有资料")),
            createReplayFrame(createToolResultEvent("已整理现有资料")),
            createReplayFrame(createResultEvent("这是最终结论")),
          ],
        },
      ],
    });

    expect(history.chatList).toHaveLength(1);
    expect(history.chatList[0].thought).toBe("先规划研究路径");
    expect(history.chatList[0].plan?.title).toBe("项目执行计划");
    expect(history.chatList[0].tasks).toHaveLength(1);
    expect(history.chatList[0].tasks[0]).toHaveLength(1);
    expect(history.chatList[0].tasks[0][0].children?.map((item) => item.messageType)).toEqual([
      "tool_thought",
      "tool_result",
    ]);
    expect(history.chatList[0].conclusion?.result).toBe("这是最终结论");
  });

  it("replay 多轮 planner history 会复用 combineData 并恢复 latest alias", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-history-rounds-001",
      title: "多轮规划历史",
      status: "SUCCESS",
      deepThink: true,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      startedAt: "2026-05-02T14:10:00",
      lastActiveAt: "2026-05-02T14:16:00",
      runs: [
        {
          requestId: "req-history-rounds-001",
          status: "SUCCESS",
          queryText: "多轮 replan",
          finalSummaryText: "最终按第二轮执行",
          startedAt: "2026-05-02T14:10:00",
          finishedAt: "2026-05-02T14:16:00",
          replayFrames: [
            createReplayFrame(createRoundPlanThoughtEvent("planner-round-1", "先输出第一轮思路")),
            createReplayFrame(createRoundPlanEvent("planner-round-1", "第一轮计划", ["收集资料"])),
            createReplayFrame(createRoundPlanThoughtEvent("planner-round-2", "再输出第二轮思路")),
            createReplayFrame(createRoundPlanEvent("planner-round-2", "第二轮计划", ["收集资料", "形成结论"])),
            createReplayFrame(createResultEvent("最终按第二轮执行")),
          ],
        },
      ],
    });

    expect(history.chatList).toHaveLength(1);
    expect(history.chatList[0].multiAgent.plannerRounds).toHaveLength(2);
    expect(history.chatList[0].multiAgent.plannerRounds?.map((item) => item.plannerRoundId)).toEqual([
      "planner-round-1",
      "planner-round-2",
    ]);
    expect(history.chatList[0].multiAgent.plan?.title).toBe("第二轮计划");
    expect(history.chatList[0].multiAgent.plan_thought).toBe("再输出第二轮思路");
    expect(history.chatList[0].thought).toBe("再输出第二轮思路");
    expect(history.chatList[0].plan?.title).toBe("第二轮计划");
  });

  it("keeps empty history as blank state input", () => {
    const detail: ConversationHistoryDetail = {
      sessionId: "session-empty-001",
      title: "新对话",
      status: "RUNNING",
      deepThink: false,
      runCount: 0,
      finishedRunCount: 0,
      failedRunCount: 0,
      startedAt: "2026-05-02T11:00:00",
      lastActiveAt: "2026-05-02T11:00:00",
      runs: [],
    };

    expect(isHistoryDetailEmpty(detail)).toBe(true);

    const history = hydrateConversationFromReplayFrames(detail);
    expect(history.chatList).toHaveLength(0);
    expect(history.dataChatList).toHaveLength(0);
    expect(toConversationHistoryTitle(detail)).toBe("新对话");
  });

  it("recovers a title from the first non-empty run query", () => {
    const detail: ConversationHistoryDetail = {
      sessionId: "session-plan-resume-001",
      title: "新对话",
      status: "SUCCESS",
      deepThink: true,
      runCount: 2,
      finishedRunCount: 2,
      failedRunCount: 0,
      runs: [
        {
          requestId: "req-plan-001",
          status: "WAITING_INPUT",
          queryText: "请先制定一份项目调研计划",
          replayFrames: [],
        },
        {
          requestId: "resume-plan-001",
          status: "SUCCESS",
          queryText: "",
          replayFrames: [],
        },
      ],
    };

    expect(toConversationHistoryTitle(detail)).toBe("请先制定一份项目调研计划");
    expect(hydrateConversationFromReplayFrames(detail).chatTitle).toBe(
      "请先制定一份项目调研计划"
    );
  });

  it("treats missing outputStyle history as generic task conversation", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-generic-001",
      title: "通用任务历史",
      status: "SUCCESS",
      deepThink: false,
      runCount: 0,
      finishedRunCount: 0,
      failedRunCount: 0,
      startedAt: "2026-05-02T11:00:00",
      lastActiveAt: "2026-05-02T11:05:00",
      runs: [],
    } as ConversationHistoryDetail);

    expect(history.productType).toBe("task");
  });

  it("marks stopped history run as force stop and preserves missing artifact state", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-stop-001",
      title: "停止中的会话",
      status: "STOPPED",
      deepThink: true,
      runCount: 1,
      finishedRunCount: 0,
      failedRunCount: 0,
      startedAt: "2026-05-02T12:00:00",
      lastActiveAt: "2026-05-02T12:05:00",
      runs: [
        {
          requestId: "req-stop-001",
          status: "STOPPED",
          queryText: "生成报告后停止",
          finalSummaryText: "已停止，但保留当前结果",
          startedAt: "2026-05-02T12:00:00",
          finishedAt: "2026-05-02T12:05:00",
          replayFrames: [
            createReplayFrame({
              taskId: "task-stop-1",
              taskOrder: 1,
              messageType: "task",
              messageOrder: 1,
              messageId: "msg-stop-file-1",
              artifactRefs: [
                {
                  displayName: "stopped-report.md",
                  resourceKey: "artifact-stopped-report",
                  missing: true,
                  missingReason: "artifact_not_found",
                },
              ],
              resultMap: {
                requestId: "req-stop-001",
                messageId: "msg-stop-file-1",
                messageType: "markdown",
                messageTime: "1714620300000",
                isFinal: true,
                finish: false,
                resultMap: {
                  isFinal: true,
                  data: "# 草稿",
                  codeOutput: "# 草稿",
                  fileInfo: [],
                },
              } as unknown as MESSAGE.Task,
            }),
          ],
        },
      ],
    });

    expect(history.deepThink).toBe(true);
    expect(history.chatList).toHaveLength(1);
    expect(history.chatList[0].forceStop).toBe(true);

    const taskData = buildConversationTaskData(history.chatList[0], history.deepThink);
    expect(taskData.taskList).toHaveLength(2);
    expect(taskData.taskList[0].artifactRefs?.[0]?.missing).toBe(true);
    expect(taskData.taskList[0].artifactRefs?.[0]?.missingReason).toBe(
      "artifact_not_found"
    );
    expect(taskData.taskList[1].messageType).toBe("result");
  });

  it("keeps code interpreter history task when replay frame only contains codeOutput", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-code-history-001",
      title: "代码解释器历史",
      status: "SUCCESS",
      deepThink: true,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      startedAt: "2026-05-02T15:00:00",
      lastActiveAt: "2026-05-02T15:02:00",
      runs: [
        {
          requestId: "req-code-history-001",
          status: "SUCCESS",
          queryText: "执行一段 Python 代码",
          finalSummaryText: "代码已执行完成",
          startedAt: "2026-05-02T15:00:00",
          finishedAt: "2026-05-02T15:02:00",
          replayFrames: [
            createReplayFrame({
              taskId: "task-code-1",
              taskOrder: 1,
              messageType: "task",
              messageOrder: 1,
              messageId: "msg-code-1",
              resultMap: {
                requestId: "req-code-history-001",
                messageId: "msg-code-1",
                messageType: "code",
                messageTime: "1714620400000",
                isFinal: true,
                finish: false,
                resultMap: {
                  isFinal: true,
                  data: "执行结果：42",
                  codeOutput: "执行结果：42",
                  fileInfo: [],
                },
              } as unknown as MESSAGE.Task,
            }),
            createReplayFrame(createResultEvent("代码已执行完成")),
          ],
        },
      ],
    });

    const taskData = buildConversationTaskData(history.chatList[0], history.deepThink);

    expect(taskData.taskList).toHaveLength(2);
    expect(taskData.taskList[0].messageType).toBe("code");
    expect(taskData.taskList[0].resultMap.codeOutput).toBe("执行结果：42");
    expect(taskData.currentChat.tasks[0]?.[0]?.children?.[0]?.messageType).toBe("code");
  });

  it("keeps $$$ in history fallback so frontend can map session files", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-summary-fallback-001",
      title: "总结兜底会话",
      status: "SUCCESS",
      deepThink: true,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      startedAt: "2026-05-02T13:00:00",
      lastActiveAt: "2026-05-02T13:05:00",
      runs: [
        {
          requestId: "req-summary-fallback-001",
          status: "SUCCESS",
          queryText: "请整理总结",
          finalSummaryText:
            "请查看最终报告。$$$ final-report.html、checklist.md",
          startedAt: "2026-05-02T13:00:00",
          finishedAt: "2026-05-02T13:05:00",
          replayFrames: [],
        },
      ],
    });

    expect(history.chatList).toHaveLength(1);
    expect(history.chatList[0].conclusion?.result).toContain("$$$");
    expect((history.chatList[0].conclusion as any)?.taskSummary).toContain(
      "final-report.html"
    );
    expect((history.chatList[0].conclusion as any)?.artifactKeys).toEqual([
      "final-report.html",
      "checklist.md",
    ]);
    expect(resolveTaskSummaryText(history.chatList[0].conclusion)).toBe(
      "请查看最终报告。"
    );
    expect(
      pickFeaturedDeliveryFiles(history.chatList[0].conclusion).map(
        (item) => item.relativePath
      )
    ).toEqual(["final-report.html", "checklist.md"]);
  });

  it("maps history $$$ names onto replayed workspace files", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-summary-files-001",
      title: "历史文件映射",
      status: "SUCCESS",
      deepThink: false,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      startedAt: "2026-05-02T13:00:00",
      lastActiveAt: "2026-05-02T13:05:00",
      runs: [
        {
          requestId: "req-summary-files-001",
          status: "SUCCESS",
          queryText: "做个页面",
          finalSummaryText: "页面已生成。$$$ simple-showcase.html",
          startedAt: "2026-05-02T13:00:00",
          finishedAt: "2026-05-02T13:05:00",
          replayFrames: [
            createReplayFrame({
              taskId: "task-1",
              taskOrder: 1,
              messageType: "task",
              messageOrder: 1,
              messageId: "msg-file-1",
              resultMap: {
                requestId: "req-summary-files-001",
                messageId: "msg-file-1",
                messageType: "file",
                messageTime: "1714620001000",
                finish: true,
                isFinal: true,
                resultMap: {
                  fileListOnly: true,
                  fileInfo: [
                    {
                      fileName: "simple-showcase.html",
                      relativePath: "simple-showcase.html",
                      domainUrl: "https://example.com/preview/simple-showcase.html",
                      ossUrl: "https://example.com/download/simple-showcase.html",
                    },
                  ],
                },
              } as unknown as MESSAGE.Task,
            }),
            createReplayFrame({
              taskId: "task-1",
              taskOrder: 2,
              messageType: "task",
              messageOrder: 2,
              messageId: "msg-result-1",
              resultMap: {
                requestId: "req-summary-files-001",
                messageId: "msg-result-1",
                messageType: "result",
                messageTime: "1714620002000",
                finish: true,
                isFinal: true,
                result: "页面已生成。$$$ simple-showcase.html",
                taskSummary: "页面已生成。$$$ simple-showcase.html",
                artifactKeys: ["simple-showcase.html"],
              } as unknown as MESSAGE.Task,
            }),
          ],
        },
      ],
    });

    const chat = history.chatList[0];
    expect(resolveTaskSummaryText(chat.conclusion)).toBe("页面已生成。");
    expect(
      pickFeaturedDeliveryFiles(
        chat.conclusion,
        collectChatArtifactFiles(chat)
      ).map((item) => item.name)
    ).toEqual(["simple-showcase.html"]);
  });

  it("restores the latest context usage from the session detail", () => {
    const history = hydrateConversationFromReplayFrames({
      sessionId: "session-context-usage-001",
      title: "上下文恢复",
      status: "SUCCESS",
      deepThink: false,
      runCount: 1,
      finishedRunCount: 1,
      failedRunCount: 0,
      runs: [
        {
          requestId: "req-context-usage-001",
          status: "SUCCESS",
          queryText: "查看上下文",
          contextUsage: {
            max: 128000,
            promptTokens: 13100,
          },
          replayFrames: [],
        },
      ],
    });

    expect(history.chatList[0].contextUsage).toEqual({
      max: 128000,
      promptTokens: 13100,
    });
  });
});
