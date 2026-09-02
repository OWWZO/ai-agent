import { describe, expect, it } from "vitest";

import {
  applyGuardError,
  resolveLatestContextUsage,
} from "./useConversationStream";
import { resolveActionPanelVisibility } from "./streamState";
import { parseAgentAnswer } from "@/utils/sseParsers";

describe("useConversationStream helpers", () => {
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
});
