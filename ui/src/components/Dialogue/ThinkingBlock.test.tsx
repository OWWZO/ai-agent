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
    expect(html).toContain("深度思考");
    expect(html).toContain("kimi-think-live");
    expect(html).not.toContain("kimi-think-teaser");
  });

  it("流式结束后默认只留标题，正文收起", () => {
    const html = renderToStaticMarkup(
      <ThinkingBlock text="完整思考内容" durationLabel="1.2s" />
    );

    expect(html).toContain('data-open="false"');
    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain("深度思考");
    expect(html).not.toContain("kimi-think-live");
    expect(html).not.toContain("kimi-think-teaser");
    expect(html).not.toContain("完整思考内容");
  });

  it("折叠态标题按钮可展开", () => {
    const html = renderToStaticMarkup(<ThinkingBlock text="完整思考内容" />);

    expect(html).toContain('aria-label="展开思考"');
  });
});
