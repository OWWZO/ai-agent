import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import VisitorLoginGate from "./VisitorLoginGate";

describe("VisitorLoginGate", () => {
  it("展示独立登录界面", () => {
    const html = renderToStaticMarkup(
      <VisitorLoginGate loading={false} onSubmit={vi.fn()} />
    );

    expect(html).toContain("登录");
    expect(html).toContain("输入用户名后进入工作台");
    expect(html).toContain("请输入用户名");
    expect(html).toContain("进入对话");
  });
});
