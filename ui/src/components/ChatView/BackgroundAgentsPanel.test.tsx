import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { default as BackgroundAgentsPanel } from "./BackgroundAgentsPanel";
import { formatBackgroundAgentElapsed } from "./backgroundAgentsModel";
import type { AgentsPanelVariant } from "./backgroundAgentsModel";
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

describe("formatBackgroundAgentElapsed", () => {
  it("秒级：已运行 X 秒，至少有 1 秒", () => {
    expect(formatBackgroundAgentElapsed(0)).toBe("已运行 1 秒");
    expect(formatBackgroundAgentElapsed(1500)).toBe("已运行 1 秒");
    expect(formatBackgroundAgentElapsed(42_000)).toBe("已运行 42 秒");
  });

  it("分钟级：已运行 X 分 Y 秒（对齐 ZCode 文案）", () => {
    expect(formatBackgroundAgentElapsed(61_000)).toBe("已运行 1 分 1 秒");
    expect(formatBackgroundAgentElapsed(2 * 60_000 + 5_000)).toBe(
      "已运行 2 分 5 秒"
    );
  });
});

describe("BackgroundAgentsPanel", () => {
  it("无后台子 Agent 时不渲染（pill / panel 都不出现）", () => {
    const html = renderToStaticMarkup(
      createElement(BackgroundAgentsPanel, {
        chat: emptyChat(),
        variant: "panel" as AgentsPanelVariant,
        onVariantChange: () => {},
      })
    );
    expect(html).toBe("");
  });

  it("mini 形态渲染胶囊：Bot 图标 + 「1 运行」", () => {
    const html = renderToStaticMarkup(
      createElement(BackgroundAgentsPanel, {
        chat: chatWithBackgroundAgent(),
        variant: "mini",
        onVariantChange: () => {},
      })
    );
    expect(html).toContain("background-agents-pill");
    expect(html).toContain("1 运行");
    expect(html).not.toContain("background-agents-panel");
  });

  it("panel 形态渲染面板：标题 + 运行中条目 + 已运行计时", () => {
    const html = renderToStaticMarkup(
      createElement(BackgroundAgentsPanel, {
        chat: chatWithBackgroundAgent(),
        variant: "panel",
        onVariantChange: () => {},
      })
    );
    expect(html).toContain("background-agents-panel");
    expect(html).toContain("智能体");
    expect(html).toContain("1 运行");
    expect(html).toContain("编写主HTML页面");
    // startedAt 落在 2026-01-01，elapsed 一定进「分秒」分支
    expect(html).toContain("已运行");
    expect(html).toMatch(/已运行 \d+ 分 \d+ 秒/);
    expect(html).toContain("background-agent-row");
  });

  it("不移植停止按钮（与 ZCode 的有意差异）", () => {
    const html = renderToStaticMarkup(
      createElement(BackgroundAgentsPanel, {
        chat: chatWithBackgroundAgent(),
        variant: "panel",
        onVariantChange: () => {},
      })
    );
    expect(html).not.toContain("停止");
  });

  it("点击条目时下钻：onOpenAgent 收到 Agent 父卡", () => {
    // 静态渲染只验证产出可含下钻钩子；点击行为由 Browser 层 E2E 覆盖。
    const chat = chatWithBackgroundAgent();
    const html = renderToStaticMarkup(
      createElement(BackgroundAgentsPanel, {
        chat,
        variant: "panel",
        onVariantChange: () => {},
        onOpenAgent: () => {},
      })
    );
    expect(html).toContain("background-agent-row");
  });
});
