import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { ToolGroup } from "./ToolGroup";

describe("ToolGroup", () => {
  it("标题由调用方传入，不再自己拼「N 个工具 · 已完成」", () => {
    const html = renderToStaticMarkup(
      createElement(ToolGroup, {
        title: "执行了 3 个步骤",
        count: 3,
        defaultOpen: false,
        children: createElement("span", null, "child")
      })
    );

    expect(html).toContain("执行了 3 个步骤");
    // 旧 UI 的「· 已完成」副标题必须消失 —— 它和混合组的「执行了 N 个步骤」是两套
    expect(html).not.toContain("kimi-tool-group-meta");
    expect(html).not.toContain("已完成");
  });

  it("不再渲染状态点与列表图标", () => {
    const html = renderToStaticMarkup(
      createElement(ToolGroup, {
        title: "正在执行 · 2 个步骤",
        count: 2,
        defaultOpen: true,
        children: createElement("span", null, "child")
      })
    );

    expect(html).not.toContain("kimi-tool-group-meta");
    expect(html).not.toContain("lucide-list");
  });

  it("收起时不渲染内容（inert 挂在外层）", () => {
    const html = renderToStaticMarkup(
      createElement(ToolGroup, {
        title: "执行了 1 个步骤",
        count: 1,
        defaultOpen: false,
        children: createElement("span", null, "child")
      })
    );

    expect(html).toContain("inert");
    expect(html).toContain("执行了 1 个步骤");
  });
});
