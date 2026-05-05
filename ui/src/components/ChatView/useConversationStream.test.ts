import { describe, expect, it } from "vitest";

import { applyGuardError } from "./useConversationStream";
import { resolveActionPanelVisibility } from "./streamState";
import { parseAgentAnswer } from "@/utils/sseParsers";

describe("useConversationStream helpers", () => {
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

  it("存在 plan 但没有 renderable task 时仍应打开右侧工作区", () => {
    expect(
      resolveActionPanelVisibility({
        plan: {
          stages: [{ title: "分析需求", status: "completed" }],
        } as unknown as CHAT.Plan,
        taskList: [],
      })
    ).toBe(true);
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
