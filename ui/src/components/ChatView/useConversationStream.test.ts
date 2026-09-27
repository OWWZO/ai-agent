import { describe, expect, it } from "vitest";

import {
  applyGuardError,
  createConversationDraftController,
  isSessionControlPackage,
  resolveLatestContextUsage,
  resolveRunReplayState,
} from "./useConversationStream";
import { resolveActionPanelVisibility } from "./streamState";
import { parseAgentAnswer } from "@/utils/sseParsers";

describe("useConversationStream helpers", () => {
  it("会话级控制帧不依赖 requestId 路由", () => {
    expect(isSessionControlPackage("heartbeat")).toBe(true);
    expect(isSessionControlPackage("follow_pending")).toBe(true);
    expect(isSessionControlPackage("follow_idle")).toBe(true);
    expect(isSessionControlPackage("result")).toBe(false);
  });

  it("新任务沿用上一轮最近的真实上下文快照", () => {
    const usage = {
      max: 200000,
      promptTokens: 24000,
    };
    const result = resolveLatestContextUsage([
      { contextUsage: usage } as CHAT.ChatItem,
      {} as CHAT.ChatItem,
      { contextUsage: { max: 200000 } } as CHAT.ChatItem,
    ]);

    expect(result).toEqual(usage);
    expect(result).not.toBe(usage);
  });

  it("没有真实 prompt_tokens 时不生成上下文快照", () => {
    expect(
      resolveLatestContextUsage([
        { contextUsage: { max: 200000 } } as CHAT.ChatItem,
      ])
    ).toBeUndefined();
  });

  it("guard error 应将当前 chat 标记为 FAILED 并生成 conclusion", () => {
    const currentChat = {
      requestId: "req-1",
      loading: true,
      multiAgent: { tasks: [] },
      metrics: {},
    } as unknown as CHAT.ChatItem;

    const next = applyGuardError(currentChat, "当前请求处理失败，请稍后重试");

    expect(next.loading).toBe(false);
    expect(next.metrics?.status).toBe("FAILED");
    expect(next.conclusion?.messageType).toBe("task_summary");
  });

  it("存在 plan 但没有产物 task 时不自动打开右侧工作区", () => {
    expect(
      resolveActionPanelVisibility({
        plan: {
          stages: [{ title: "分析需求", status: "completed" }],
        } as unknown as CHAT.Plan,
        taskList: [],
      })
    ).toBe(false);
  });

  it("heartbeat 包在缺少 resultMap 时也应被正常解析", () => {
    const result = parseAgentAnswer({
      status: "success",
      packageType: "heartbeat",
      finished: false,
      response: "",
      responseAll: "",
      useTimes: 0,
      useTokens: 0,
      responseType: "text",
      encrypted: false,
      errorMsg: "",
    });

    expect(result.packageType).toBe("heartbeat");
    expect(result.resultMap).toEqual({});
  });

  it("result 包的 errorMsg 为 null 时也应被正常解析", () => {
    const result = parseAgentAnswer({
      status: "running",
      packageType: "result",
      finished: false,
      response: "",
      responseAll: "",
      useTimes: 0,
      useTokens: 0,
      responseType: "text",
      encrypted: false,
      errorMsg: null,
      resultMap: {
        eventData: {
          messageOrder: 1,
          messageType: "task",
          messageId: "msg-1",
          taskId: "task-1",
          taskOrder: 1,
          resultMap: {
            messageType: "agent_stream",
            result: "正在处理",
          },
        },
      },
    });

    expect(result.errorMsg).toBe("");
    expect(result.resultMap.eventData).toBeDefined();
  });

  it("旧 turn 的 replace 不得覆盖后追加的新消息", () => {
    let latest: CHAT.ConversationHistory = {
      id: "c1",
      sessionId: "s1",
      chatList: [
        { requestId: "req-old", query: "先跑后台" } as CHAT.ChatItem,
      ],
    } as CHAT.ConversationHistory;
    const controller = createConversationDraftController<CHAT.ChatItem>(
      "c1",
      latest,
      "chatList",
      (_id, next) => {
        latest = next;
      },
      () => latest
    );

    latest = {
      ...latest,
      chatList: [
        latest.chatList[0],
        { requestId: "req-new", query: "第二句" } as CHAT.ChatItem,
      ],
    };

    const next = controller.replaceLastItem({
      requestId: "req-old",
      query: "先跑后台",
      conclusion: { messageType: "result" },
    } as CHAT.ChatItem);

    expect(next.chatList.map((item) => item.requestId)).toEqual([
      "req-old",
      "req-new",
    ]);
    expect(next.chatList[0].conclusion?.messageType).toBe("result");
    expect(next.chatList[1].query).toBe("第二句");
  });

  it("replace 同一 requestId 不得追加出重复 turn", () => {
    let latest: CHAT.ConversationHistory = {
      id: "c1",
      sessionId: "s1",
      chatList: [
        { requestId: "req-old", query: "先跑后台" } as CHAT.ChatItem,
        { requestId: "req-old", query: "重复副本" } as CHAT.ChatItem,
        { requestId: "req-new", query: "第二句" } as CHAT.ChatItem,
      ],
    } as CHAT.ConversationHistory;
    const controller = createConversationDraftController<CHAT.ChatItem>(
      "c1",
      latest,
      "chatList",
      (_id, next) => {
        latest = next;
      },
      () => latest
    );

    const next = controller.replaceLastItem({
      requestId: "req-old",
      query: "先跑后台",
      conclusion: { messageType: "result" },
    } as CHAT.ChatItem);

    expect(next.chatList.map((item) => item.requestId)).toEqual([
      "req-old",
      "req-new",
    ]);
  });

  it("续跑首帧可按 Run A requestId 替换，并让后续事件按 Run B requestId 更新", () => {
    let latest: CHAT.ConversationHistory = {
      id: "c1",
      sessionId: "s1",
      chatList: [
        {
          requestId: "run-a",
          query: "需要确认",
          loading: false,
          metrics: { status: "WAITING_INPUT" },
        } as CHAT.ChatItem,
      ],
    } as CHAT.ConversationHistory;
    const controller = createConversationDraftController<CHAT.ChatItem>(
      "c1",
      latest,
      "chatList",
      (_id, next) => {
        latest = next;
      },
      () => latest
    );

    const resumed = controller.replaceItem(
      {
        requestId: "resume-b",
        query: "需要确认",
        loading: true,
        tip: "正在推进任务…",
        metrics: { status: "RUNNING" },
      } as CHAT.ChatItem,
      "run-a"
    );
    controller.commit(resumed);

    expect(latest.chatList.map((item) => item.requestId)).toEqual(["resume-b"]);
    expect(latest.chatList[0].metrics?.status).toBe("RUNNING");

    const next = controller.replaceLastItem({
      requestId: "resume-b",
      query: "需要确认",
      loading: false,
      conclusion: {
        messageType: "result",
        result: "已完成",
      },
      metrics: { status: "SUCCESS" },
    } as CHAT.ChatItem);

    expect(next.chatList).toHaveLength(1);
    expect(next.chatList[0].requestId).toBe("resume-b");
    expect(next.chatList[0].conclusion?.result).toBe("已完成");
  });

  it("Run B replay 会恢复 context_usage、工具过程和最终结果", () => {
    const event = (
      messageId: string,
      resultMap: Record<string, unknown>,
      finished = false
    ): MESSAGE.EventData => ({
      messageType: "task",
      taskId: "task-1",
      taskOrder: 1,
      messageOrder: 1,
      messageId,
      resultMap: resultMap as unknown as MESSAGE.Task,
      finish: finished,
      isFinal: finished,
    } as unknown as MESSAGE.EventData);
    const frame = (eventData: MESSAGE.EventData, finished = false) => ({
      reqId: "resume-b",
      status: "success",
      finished,
      resultMap: { eventData },
    });
    const conversation = {
      id: "c1",
      sessionId: "s1",
      deepThink: false,
      chatList: [
        {
          sessionId: "s1",
          requestId: "resume-b",
          query: "需要确认",
          loading: true,
          multiAgent: { tasks: [] },
          metrics: { status: "RUNNING" },
        } as unknown as CHAT.ChatItem,
      ],
    } as CHAT.ConversationHistory;

    const { chat, status } = resolveRunReplayState(conversation, {
      requestId: "resume-b",
      status: "SUCCESS",
      queryText: "需要确认",
      replayFrames: [
        frame(
          event("context-1", {
            messageType: "context_usage",
            max: 200000,
            promptTokens: 1234,
          })
        ),
        frame(
          event("thought-1", {
            messageType: "tool_thought",
            toolThought: "先读取资料",
          })
        ),
        frame(
          event("reasoning-1", {
            messageType: "llm_reasoning",
            reasoningContent: "正在判断下一步",
          })
        ),
        frame(
          event("call-1", {
            messageType: "tool_call",
            toolName: "read_file",
            toolCallId: "call-1",
            status: "running",
          })
        ),
        frame(
          event("result-1", {
            messageType: "tool_result",
            toolName: "read_file",
            toolCallId: "call-1",
            status: "success",
            toolResult: {
              toolName: "read_file",
              toolResult: "已读取",
            },
          })
        ),
        frame(
          event("answer-1", {
            messageType: "result",
            result: "已完成",
            taskSummary: "已完成",
            isFinal: true,
          }, true),
          true
        ),
      ],
    });

    expect(status).toBe("SUCCESS");
    expect(chat?.requestId).toBe("resume-b");
    expect(chat?.loading).toBe(false);
    expect(chat?.contextUsage).toEqual({
      max: 200000,
      promptTokens: 1234,
    });
    expect(chat?.conclusion?.result).toBe("已完成");
    expect(
      chat?.multiAgent.tasks.flat().map((task) => task.messageType)
    ).toEqual(
      expect.arrayContaining([
        "tool_thought",
        "llm_reasoning",
        "tool_result",
        "result",
      ])
    );
    expect(chat?.tip).not.toBe("需要你的帮助");
  });

  it("SUCCESS replay 会清除 HITL 等待状态", () => {
    const conversation = {
      id: "c1",
      sessionId: "s1",
      chatList: [
        {
          sessionId: "s1",
          requestId: "resume-b",
          query: "需要确认",
          loading: true,
          tip: "需要你的帮助",
          multiAgent: { tasks: [] },
          metrics: { status: "RUNNING" },
        } as unknown as CHAT.ChatItem,
      ],
    } as CHAT.ConversationHistory;

    const { chat, status } = resolveRunReplayState(conversation, {
      requestId: "resume-b",
      status: "SUCCESS",
      queryText: "需要确认",
      finalSummaryText: "完成",
      replayFrames: [],
    });

    expect(status).toBe("SUCCESS");
    expect(chat?.loading).toBe(false);
    expect(chat?.metrics?.status).toBe("SUCCESS");
    expect(chat?.tip).not.toBe("需要你的帮助");
  });
});
