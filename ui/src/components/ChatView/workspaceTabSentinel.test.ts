import { describe, expect, it } from "vitest";

import {
  decideWorkspaceTabCountChange,
  resolveAutoFollowTabKind,
  shouldAutoFollowWorkspaceTask,
} from "./workspaceTabSentinel";

describe("decideWorkspaceTabCountChange", () => {
  it("首次挂载上报 0 不触发收起（哨兵还是 false）", () => {
    // 关键回归：ActionView 挂载时 pending tab 的消费 effect 与上报 effect
    // 同批执行，上报读到的是旧 state（0）。把它当关闭命令 → 工作区刚打开就自己收起。
    expect(
      decideWorkspaceTabCountChange({
        hadTabs: false,
        count: 0,
        hasPendingOpenIntent: false,
      })
    ).toEqual({
      hadTabs: false,
      collapse: false
    });
  });

  it("一直空着再上报 0 也不触发收起", () => {
    expect(
      decideWorkspaceTabCountChange({
        hadTabs: false,
        count: 0,
        hasPendingOpenIntent: false,
      }).collapse
    ).toBe(false);
  });

  it("出现 tab 后置哨兵，此时不收起", () => {
    expect(
      decideWorkspaceTabCountChange({
        hadTabs: false,
        count: 1,
        hasPendingOpenIntent: false,
      })
    ).toEqual({
      hadTabs: true,
      collapse: false
    });
  });

  it("真的关掉最后一个 tab（>0 → 0）才收起", () => {
    expect(
      decideWorkspaceTabCountChange({
        hadTabs: true,
        count: 0,
        hasPendingOpenIntent: false,
      })
    ).toEqual({
      hadTabs: false,
      collapse: true
    });
  });

  it("「关最后一个 tab」与「开新 tab」并发时不收起，且保留哨兵", () => {
    // 否则刚点开的 tab 会被连带关掉（pending 意图已经被设上）。
    expect(
      decideWorkspaceTabCountChange({
        hadTabs: true,
        count: 0,
        hasPendingOpenIntent: true,
      })
    ).toEqual({
      hadTabs: true,
      collapse: false
    });
  });

  it("多个 tab 一起关到 0 也走同一条收起路径", () => {
    expect(
      decideWorkspaceTabCountChange({
        hadTabs: true,
        count: 0,
        hasPendingOpenIntent: false,
      }).collapse
    ).toBe(true);
  });
});

describe("shouldAutoFollowWorkspaceTask", () => {
  const base = {
    workspaceVisible: false,
    showAction: true,
    hasLiveTask: true,
    userClosedWorkspace: false,
    hasTabs: false,
  };

  it("工作区收起 + 流里有可渲染任务 → 自动跟随（展开并开一个真实 tab）", () => {
    expect(shouldAutoFollowWorkspaceTask(base)).toBe(true);
  });

  it("工作区已经可见 → 不动，不抢用户正在看的 tab", () => {
    expect(
      shouldAutoFollowWorkspaceTask({
        ...base,
        workspaceVisible: true
      })
    ).toBe(false);
  });

  it("本轮用户手动关过工作区 → 不弹回", () => {
    expect(
      shouldAutoFollowWorkspaceTask({
        ...base,
        userClosedWorkspace: true
      })
    ).toBe(false);
  });

  it("还有 tab → 不需要自动跟随兜底", () => {
    expect(shouldAutoFollowWorkspaceTask({
      ...base,
      hasTabs: true
    })).toBe(false);
  });

  it("没有流式意图或没有可渲染任务 → 不展开（避免空白面板）", () => {
    expect(shouldAutoFollowWorkspaceTask({
      ...base,
      showAction: false
    })).toBe(
      false
    );
    expect(shouldAutoFollowWorkspaceTask({
      ...base,
      hasLiveTask: false
    })).toBe(
      false
    );
  });
});

describe("resolveAutoFollowTabKind", () => {
  it("有可预览主产物 → 文件 tab（tab 上是文件名，不是工具名）", () => {
    // 关键回归：自动跟随原本一律开任务 tab，tab 标题是工具名（`bash`），
    // 而内容其实是那个文件的预览 —— 用户看到的「文件名不对」就是这个。
    expect(
      resolveAutoFollowTabKind({
        messageType: "tool_result",
        hasPrimaryFile: true,
      })
    ).toBe("file");
  });

  it("没有产物 → 任务 tab（标题用工具名）", () => {
    expect(
      resolveAutoFollowTabKind({
        messageType: "tool_result",
        hasPrimaryFile: false,
      })
    ).toBe("task");
  });

  it("深搜阶段即便带文件也保持任务 tab：过程面板才是它的主体展示", () => {
    expect(
      resolveAutoFollowTabKind({
        messageType: "deep_search",
        hasPrimaryFile: true,
      })
    ).toBe("task");
  });
});
