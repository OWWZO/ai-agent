import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { ToolPreviewPanel } from "./ToolPreviewPanel";

describe("ToolPreviewPanel navigation", () => {
  it("renders a back button when opened from a sub Agent", () => {
    const html = renderToStaticMarkup(
      <ToolPreviewPanel
        source={{
          type: "patch",
          title: "Write",
          patch: ""
        }}
        output={[]}
        onBack={vi.fn()}
        onClose={vi.fn()}
      />
    );

    expect(html).toContain('aria-label="返回子 Agent"');
    expect(html).toContain('title="返回子 Agent"');
  });

  it("does not render a back button for a standalone tool detail", () => {
    const html = renderToStaticMarkup(
      <ToolPreviewPanel
        source={{
          type: "patch",
          title: "Write",
          patch: ""
        }}
        output={[]}
        onClose={vi.fn()}
      />
    );

    expect(html).not.toContain('aria-label="返回子 Agent"');
  });

  it("patch source 渲染 diff 渲染器", () => {
    const html = renderToStaticMarkup(
      <ToolPreviewPanel
        source={{
          type: "patch",
          title: "a.ts",
          path: "src/a.ts",
          patch: "--- a/src/a.ts\n+++ b/src/a.ts\n@@\n-old\n+new",
        }}
        onClose={vi.fn()}
      />
    );

    expect(html).toContain("kimi-diff-wrap");
    expect(html).toContain("src/a.ts");
  });

  it("patch 为空但 status=error 时展示失败文案", () => {
    const html = renderToStaticMarkup(
      <ToolPreviewPanel
        source={{
          type: "patch",
          title: "a.ts",
          patch: ""
        }}
        status="error"
        output={[]}
        onClose={vi.fn()}
      />
    );

    expect(html).toContain("工具执行失败，无可用 diff");
  });

  it("file source 不在产物清单时同步给出降级说明（决策点 B）", () => {
    const html = renderToStaticMarkup(
      <ToolPreviewPanel
        source={{
          type: "file",
          title: "a.ts",
          path: "/abs/a.ts"
        }}
        unavailableReason="该文件不在会话产物中，无法预览：/abs/a.ts"
        onClose={vi.fn()}
      />
    );

    expect(html).toContain("无法预览");
    expect(html).toContain("/abs/a.ts");
  });

  it("image source 无 fileUrl 时同步给出降级说明", () => {
    const html = renderToStaticMarkup(
      <ToolPreviewPanel
        source={{
          type: "image",
          title: "p.png",
          path: "/abs/p.png"
        }}
        onClose={vi.fn()}
      />
    );

    expect(html).toContain("无法预览");
  });

  it("text source 直接渲染内容（不依赖网络）", () => {
    const html = renderToStaticMarkup(
      <ToolPreviewPanel
        source={{
          type: "text",
          title: "snip",
          content: "const a = 1;",
          language: "typescript",
        }}
        onClose={vi.fn()}
      />
    );

    expect(html).toContain("tool-preview-panel");
    expect(html).toContain("snip");
  });
});
