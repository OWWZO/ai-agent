import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { WorkspaceTabOverview } from "./WorkspaceTabOverview";
import { WorkspaceTabIcon } from "./WorkspaceTabIcon";
import type { WorkspaceTab } from "./workspaceFiles";

/**
 * 说明：项目 vitest 跑在 node 环境、组件测试统一用 `renderToStaticMarkup`。
 * Radix Popover 的内容走 Portal，SSR 阶段不会渲染，所以这里只能断言触发器
 * （以及整棵组件树不炸）；搜索/排序/相对时间的逻辑在
 * `workspaceTabsModel.test.ts`，展示解析在 `workspaceTabDisplay.test.ts`。
 */

function agentTab(): WorkspaceTab {
  return {
    kind: "agent",
    key: "agent:tc-1",
    toolCallId: "tc-1",
    title: "继续完善 HTML 页面",
    fallbackTool: {},
    fallbackChat: {},
  } as unknown as WorkspaceTab;
}

describe("WorkspaceTabOverview", () => {
  it("渲染最左的标签页列表触发器", () => {
    const html = renderToStaticMarkup(
      createElement(WorkspaceTabOverview, {
        tabs: [agentTab()],
        activeKey: "agent:tc-1",
        onActivate: () => {},
        onCloseTab: () => {},
      })
    );
    expect(html).toContain("workspace-tab-overview-trigger");
    expect(html).toContain("标签页列表");
  });

  it("没有 tab 时也不炸（触发器仍在，只是列表为空）", () => {
    const html = renderToStaticMarkup(
      createElement(WorkspaceTabOverview, {
        tabs: [],
        activeKey: "",
        onActivate: () => {},
        onCloseTab: () => {},
      })
    );
    expect(html).toContain("workspace-tab-overview-trigger");
  });
});

describe("WorkspaceTabIcon", () => {
  it("glyph 渲染 lucide 图标", () => {
    const html = renderToStaticMarkup(
      createElement(WorkspaceTabIcon, {
        spec: {
          kind: "glyph",
          glyph: "bot"
        },
      })
    );
    expect(html).toContain("lucide-bot");
  });

  it("badge 渲染紧凑角标，size=md 比 sm 大一档", () => {
    const sm = renderToStaticMarkup(
      createElement(WorkspaceTabIcon, {
        spec: {
          kind: "badge",
          badge: "Py"
        }
      })
    );
    const md = renderToStaticMarkup(
      createElement(WorkspaceTabIcon, {
        spec: {
          kind: "badge",
          badge: "Py"
        },
        size: "md",
      })
    );
    expect(sm).toContain("Py");
    expect(sm).toContain("size-4");
    expect(md).toContain("size-5");
  });
});
