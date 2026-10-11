import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { ThinkingBlock } from "./ThinkingBlock";

describe("ThinkingBlock", () => {
  it("流式展示时展开正文并显示可点击标题", () => {
    const html = renderToStaticMarkup(
      <ThinkingBlock text="正在分析问题" streaming durationLabel="0.8s" />
    );

    expect(html).toContain('data-open="true"');
    expect(html).toContain('aria-expanded="true"');
    expect(html).toContain(">思考<");
    expect(html).toContain("kimi-think-live");
    expect(html).not.toContain("kimi-think-teaser");
  });

  it("流式结束后默认只留标题，正文收起", () => {
    const html = renderToStaticMarkup(
      <ThinkingBlock text="完整思考内容" durationLabel="1.2s" />
    );

    expect(html).toContain('data-open="false"');
    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain(">思考<");
    expect(html).not.toContain("kimi-think-live");
    expect(html).not.toContain("kimi-think-teaser");
    expect(html).not.toContain("完整思考内容");
  });

  it("折叠态标题按钮可展开", () => {
    const html = renderToStaticMarkup(<ThinkingBlock text="完整思考内容" />);

    expect(html).toContain('aria-label="展开思考"');
  });

  it("渲染大脑图标（对齐 ZCode BrainIcon）", () => {
    const html = renderToStaticMarkup(<ThinkingBlock text="内容" />);

    expect(html).toContain("lucide-brain");
  });

  it("传入 durationMs 时拼成「持续了 X 秒」", () => {
    const html = renderToStaticMarkup(
      <ThinkingBlock text="内容" durationMs={5_400} />
    );

    expect(html).toContain("持续了 6 秒");
    expect(html).toContain("kimi-think-duration");
  });

  it("无时长且已结束时显示「持续了几秒」", () => {
    const html = renderToStaticMarkup(<ThinkingBlock text="内容" />);

    expect(html).toContain("持续了几秒");
  });

  it("durationMs 优先于 durationLabel", () => {
    const html = renderToStaticMarkup(
      <ThinkingBlock text="内容" durationMs={3_000} durationLabel="0.1s" />
    );

    expect(html).toContain("持续了 3 秒");
    expect(html).not.toContain("0.1s");
  });

  it("流式时默认展开，摘要只在收起态出现", () => {
    // streaming → 初始 open=true，所以摘要不出现；收起态需要交互，SSR 覆盖不到，
    // 由 CDP 实测（plans/030 §6）。
    const html = renderToStaticMarkup(
      <ThinkingBlock text={"第一行\n最后的摘要"} streaming />
    );

    expect(html).toContain('data-open="true"');
    expect(html).not.toContain("thinking-streaming-summary");
  });
});
