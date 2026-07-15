import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import GeneralInput from "./index";

describe("GeneralInput", () => {
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
});
