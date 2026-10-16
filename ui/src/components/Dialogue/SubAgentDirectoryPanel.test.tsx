import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { SubAgentDirectoryPanel } from "./SubAgentDirectoryPanel";
import {
  collectSubAgentRoster,
  resolveRosterAgentTool,
  selectEndedSubAgents,
  selectRunningSubAgents,
  type SubAgentRosterItem,
} from "@/utils/chat/subAgentRoster";

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

function rosterItem(
  overrides: Partial<SubAgentRosterItem> & { id: string }
): SubAgentRosterItem {
  return {
    name: "子智能体",
    kind: "subagent",
    state: "run",
    timing: "",
    hostChat: emptyChat(),
    ...overrides,
  } as SubAgentRosterItem;
}

describe("SubAgentDirectoryPanel", () => {
  it("空名册显示空态", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentDirectoryPanel, { items: [] })
    );
    expect(html).toContain("子智能体目录");
    expect(html).toContain("暂无子智能体");
  });

  it("按「正在运行 / 已结束」两段渲染，行上有标题与状态文案", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentDirectoryPanel, {
        items: [
          rosterItem({ id: "tc-run", name: "继续完善 HTML 页面", state: "run" }),
          rosterItem({
            id: "tc-done",
            name: "编写 HTML 草稿一",
            state: "fail",
            summary: "Upstream HTTP/2 stream failed",
          }),
        ],
        onOpen: () => {},
      })
    );
    expect(html).toContain("正在运行 · 1");
    expect(html).toContain("已结束 · 1");
    expect(html).toContain("继续完善 HTML 页面");
    expect(html).toContain("运行中");
    expect(html).toContain("编写 HTML 草稿一");
    expect(html).toContain("失败");
    expect(html).toContain("Upstream HTTP/2 stream failed");
    expect(html.match(/sub-agent-directory-row/g)?.length).toBe(2);
  });

  it("摘要缺失时回退到回传正文首行", () => {
    const html = renderToStaticMarkup(
      createElement(SubAgentDirectoryPanel, {
        items: [
          rosterItem({
            id: "tc-done",
            name: "补写 HTML 草稿一",
            state: "done",
            output: ["已创建可直接打开的响应式落地页：index.html", "第二行"],
          }),
        ],
        onOpen: () => {},
      })
    );
    expect(html).toContain("已创建可直接打开的响应式落地页：index.html");
    expect(html).not.toContain("第二行");
  });
});

describe("sub-agent roster selectors", () => {
  it("运行中 / 已结束分组互斥，已结束按结束时间倒序", () => {
    const items = [
      rosterItem({ id: "a", state: "run", startedAtMs: 100 }),
      rosterItem({ id: "b", state: "done", endedAtMs: 1_000 }),
      rosterItem({ id: "c", state: "fail", endedAtMs: 2_000 }),
    ];
    expect(selectRunningSubAgents(items).map((i) => i.id)).toEqual(["a"]);
    expect(selectEndedSubAgents(items).map((i) => i.id)).toEqual(["c", "b"]);
  });

  it("名册跨会话聚合且运行中优先，hostChat 指向条目宿主", () => {
    const chatA = emptyChat();
    const chatB = { ...emptyChat(), requestId: "r2" } as CHAT.ChatItem;
    const roster = collectSubAgentRoster([chatA, chatB], chatB);
    // 两个空会话都没有子 Agent
    expect(roster).toHaveLength(0);
  });

  it("父卡缺失时用名册条目合成可渲染的 Agent 卡（保住 childSessionId）", () => {
    const tool = resolveRosterAgentTool(
      rosterItem({
        id: "tc-bg",
        name: "后台探索",
        meta: "Explore",
        state: "done",
        runInBackground: true,
        childSessionId: "sub_agent-1",
      })
    );
    const resultMap = tool.resultMap as Record<string, unknown>;
    expect(resultMap.toolName).toBe("Agent");
    expect(resultMap.childSessionId).toBe("sub_agent-1");
    expect(resultMap.runInBackground).toBe(true);
    expect((resultMap.input as Record<string, unknown>).description).toBe(
      "后台探索"
    );
  });
});
