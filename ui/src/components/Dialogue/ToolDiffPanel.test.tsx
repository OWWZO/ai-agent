import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { ToolDiffPanel } from "./ToolDiffPanel";

describe("ToolDiffPanel navigation", () => {
  it("renders a back button when opened from a sub Agent", () => {
    const html = renderToStaticMarkup(
      <ToolDiffPanel
        title="Write"
        diffCode={null}
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
      <ToolDiffPanel
        title="Write"
        diffCode={null}
        output={[]}
        onClose={vi.fn()}
      />
    );

    expect(html).not.toContain('aria-label="返回子 Agent"');
  });
});
