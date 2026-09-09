import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import GeneralInput, { ATTACHMENT_ACCEPT, MAX_QUERY_CHARS } from "./index";

describe("GeneralInput", () => {
  it("附件不按扩展名拦截", () => {
    expect(ATTACHMENT_ACCEPT).toBe("");
    expect(MAX_QUERY_CHARS).toBe(8000);
  });

  it("上传按钮直接打开可多选的文件选择器", () => {
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
    expect(html).toMatch(/<input[^>]*multiple[^>]*type="file"/i);
    expect(html).toContain("上传附件，可一次多选");
  });

  it("busy 时不展示状态文案", () => {
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

    expect(html).not.toContain("Working");
    expect(html).not.toContain("thinking-shimmer");
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

  it("欢迎页选 PlanExecute 不展示计划按钮", () => {
    const html = renderToStaticMarkup(
      <GeneralInput
        sessionId="session-1"
        placeholder="请输入问题"
        showBtn
        disabled={false}
        size="default"
        deepThink
        send={vi.fn()}
      />
    );

    expect(html).not.toContain(">计划<");
  });

  it("PlanExecute 会话展示本轮计划按钮", () => {
    const html = renderToStaticMarkup(
      <GeneralInput
        sessionId="session-1"
        placeholder="请输入问题"
        showBtn={false}
        disabled={false}
        size="default"
        deepThink
        send={vi.fn()}
      />
    );

    expect(html).toContain("计划");
  });
});
