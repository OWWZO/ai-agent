import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { default as SubAgentsPanel } from "./SubAgentsPanel";
import {
  formatSubAgentElapsed,
  formatSubAgentRelativeTime,
  type AgentsPanelVariant,
} from "./subAgentsModel";
import { combineData, handleTaskData } from "@/utils/chat";

function emptyChat(): CHAT.ChatItem {
  return {
    sessionId: "s1",
    requestId: "r1",
    query: "q",
    files: [],
    forceStop: false,
    loading: false,
    multiAgent: { tasks: [] },
    tasks: [],
  } as CHAT.ChatItem;
}

function taskEvent(
  messageType: string,
  resultMap: Record<string, unknown>
): MESSAGE.EventData {
  return {
    messageOrder: 1,
    messageType: "task",
    messageId: String(resultMap.messageId || resultMap.toolCallId || "m1"),
    taskId: "t1",
    taskOrder: 1,
    resultMap: {
      messageType,
      messageTime: "2026-01-01T00:00:00Z",
      requestId: "r1",
      finish: false,
      isFinal: false,
      id: String(resultMap.messageId || resultMap.toolCallId || "m1"),
      ...resultMap,
    } as unknown as MESSAGE.Task,
  } as MESSAGE.EventData;
}

/** 运行中的后台（run_in_background）子 Agent */
function chatWithBackgroundAgent(): CHAT.ChatItem {
  let chat = emptyChat();
  chat = combineData(
    taskEvent("tool_call", {
      toolName: "Agent",
      toolCallId: "tc-bg",
      messageId: "tc-bg",
      status: "running",
      input: {
        description: "编写主HTML页面",
        prompt: "scan",
        subagent_type: "code-executor",
        run_in_background: true,
      },
    }),
    chat
  );
  handleTaskData(chat, true, chat.multiAgent);
  return chat;
}

/** 运行中的**同步**（前台）子 Agent —— 旧实现会被过滤掉，不在面板里出现 */
function chatWithForegroundAgent(): CHAT.ChatItem {
  let chat = emptyChat();
  chat = combineData(
    taskEvent("tool_call", {
      toolName: "Agent",
      toolCallId: "tc-fg",
      messageId: "tc-fg",
      status: "running",
      input: {
        description: "继续完善 HTML 页面",
        prompt: "scan",
        subagent_type: "general-purpose",
      },
    }),
    chat
  );
  handleTaskData(chat, true, chat.multiAgent);
  return chat;
}

/** 已结束（failed）的同步子 Agent */
function chatWithEndedAgent(): CHAT.ChatItem {
  let chat = emptyChat();
  chat = combineData(
    taskEvent("tool_result", {
      toolName: "Agent",
      toolCallId: "tc-done",
      messageId: "tc-done",
      status: "failed",
      errorMsg: "Upstream HTTP/2 stream failed",
      input: {
        description: "编写 HTML 草稿一",
        prompt: "write",
        subagent_type: "general-purpose",
      },
    }),
    chat
  );
  handleTaskData(chat, true, chat.multiAgent);
  return chat;
}

describe("formatSubAgentElapsed", () => {
  it("秒级：已运行 X 秒，至少有 1 秒", () => {
    expect(formatSubAgentElapsed(0)).toBe("已运行 1 秒");
    expect(formatSubAgentElapsed(1500)).toBe("已运行 1 秒");
    expect(formatSubAgentElapsed(42_000)).toBe("已运行 42 秒");
  });

  it("分钟级：已运行 X 分 Y 秒（对齐 ZCode 文案）", () => {
    expect(formatSubAgentElapsed(61_000)).toBe("已运行 1 分 1 秒");
    expect(formatSubAgentElapsed(2 * 60_000 + 5_000)).toBe(
      "已运行 2 分 5 秒"
    );
  });
});

describe("formatSubAgentRelativeTime", () => {
  const now = Date.parse("2026-01-01T12:00:00Z");

  it("按 ZCode 的四档给相对时间", () => {
    expect(formatSubAgentRelativeTime(now - 5_000, now)).toBe("刚刚");
    expect(formatSubAgentRelativeTime(now - 3 * 60_000, now)).toBe("3 分");
    expect(formatSubAgentRelativeTime(now - 2 * 3_600_000, now)).toBe("2 小时");
    expect(formatSubAgentRelativeTime(now - 3 * 86_400_000, now)).toBe("3 天");
  });

  it("没有时间戳时不产出文案", () => {
    expect(formatSubAgentRelativeTime(undefined, now)).toBe("");
  });
});

describe("SubAgentsPanel", () => {
  it("没有子 Agent 时不渲染（pill / panel 都不出现）", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentsPanel, {
        chat: emptyChat(),
        variant: "panel" as AgentsPanelVariant,
        onVariantChange: () => {},
      })
    );
    expect(html).toBe("");
  });

  it("mini 形态渲染胶囊：Bot 图标 + 「1 运行」", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentsPanel, {
        chat: chatWithBackgroundAgent(),
        variant: "mini",
        onVariantChange: () => {},
      })
    );
    expect(html).toContain("sub-agents-pill");
    expect(html).toContain("1 运行");
    expect(html).not.toContain("sub-agents-panel");
  });

  it("panel 形态渲染面板：标题 + 运行中条目 + 已运行计时", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentsPanel, {
        chat: chatWithBackgroundAgent(),
        variant: "panel",
        onVariantChange: () => {},
      })
    );
    expect(html).toContain("sub-agents-panel");
    expect(html).toContain("智能体");
    expect(html).toContain("1 运行");
    expect(html).toContain("编写主HTML页面");
    // startedAt 落在 2026-01-01，elapsed 一定进「分秒」分支
    expect(html).toContain("已运行");
    expect(html).toMatch(/已运行 \d+ 分 \d+ 秒/);
    expect(html).toContain("sub-agent-row");
  });

  it("同步（前台）子 Agent 同样进面板 —— 不再只收 run_in_background", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentsPanel, {
        chat: chatWithForegroundAgent(),
        variant: "panel",
        onVariantChange: () => {},
      })
    );
    expect(html).toContain("sub-agents-panel");
    expect(html).toContain("1 运行");
    expect(html).toContain("继续完善 HTML 页面");
    expect(html).toContain("sub-agent-row");
  });

  it("已结束条目收进页脚「已结束 N」，正文不铺历史", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentsPanel, {
        chat: chatWithEndedAgent(),
        variant: "panel",
        onVariantChange: () => {},
        onOpenDirectory: () => {},
      })
    );
    expect(html).toContain("sub-agents-ended-row");
    expect(html).toContain("已结束");
    // 已结束条目不再占用正文行
    expect(html).not.toContain("sub-agent-row");
    expect(html).toContain("1 个智能体");
  });

  it("不移植停止按钮（与 ZCode 的有意差异）", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentsPanel, {
        chat: chatWithBackgroundAgent(),
        variant: "panel",
        onVariantChange: () => {},
      })
    );
    expect(html).not.toContain("停止");
  });
});
