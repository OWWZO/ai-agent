import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import VisitorLoginGate from "./VisitorLoginGate";

describe("VisitorLoginGate", () => {
  it("展示独立登录界面", () => {
    const html = renderToStaticMarkup(
      <VisitorLoginGate loading={false} onSubmit={vi.fn()} />
    );

    expect(html).toContain("你好，探索者");
    expect(html).toContain("输入一个名字，开启与 AI 的协作之旅");
    expect(html).toContain("你的名字");
    expect(html).toContain("进入工作台");
  });
});
