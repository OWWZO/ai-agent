import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import GeneralInput, { ATTACHMENT_ACCEPT, MAX_QUERY_CHARS } from "./index";

describe("GeneralInput", () => {
  it("附件 accept 包含 pptx/json/py/html", () => {
    expect(ATTACHMENT_ACCEPT).toContain(".pptx");
    expect(ATTACHMENT_ACCEPT).toContain(".json");
    expect(ATTACHMENT_ACCEPT).toContain(".py");
    expect(ATTACHMENT_ACCEPT).toContain(".html");
    expect(MAX_QUERY_CHARS).toBe(8000);
  });

  it("上传菜单触发器不会渲染嵌套 button", () => {
    const html = renderToStaticMarkup(
      <GeneralInput
        sessionId="session-1"
        placeholder="请输入问题"
        showBtn={false}
        disabled={false}
        size="default"
        send={vi.fn()}
      />
    );

    expect(html).not.toMatch(/<button[^>]*>\s*<button/i);
  });

  it("busy 时在发送按钮旁展示 Working shimmer", () => {
    const html = renderToStaticMarkup(
      <GeneralInput
        sessionId="session-1"
        placeholder="任务进行中..."
        showBtn={false}
        disabled
        busy
        size="default"
        send={vi.fn()}
      />
    );

    expect(html).toContain("Working");
    expect(html).toContain("thinking-shimmer");
    expect(html).toContain('aria-label="Working"');
  });

  it("输入工具条不再展示输出格式入口", () => {
    const html = renderToStaticMarkup(
      <GeneralInput
        sessionId="session-1"
        placeholder="请输入问题"
        showBtn
        disabled={false}
        size="default"
        product={{
          type: "task",
          name: "通用任务",
          placeholder: "请输入问题",
          img: "icon-task",
          color: "text-[#4040FF]",
        } as CHAT.Product}
        send={vi.fn()}
      />
    );

    expect(html).not.toContain("输出格式");
    expect(html).not.toContain("网页模式");
    expect(html).toContain("深度思考");
    expect(html).toContain("数据分析");
  });
});
