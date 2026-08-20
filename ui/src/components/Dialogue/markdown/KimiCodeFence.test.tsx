import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import type { ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import gfm from "remark-gfm";
import {
  KimiCodeFence,
  parseDiffLines,
  unwrapShikiPre,
} from "./KimiCodeFence";

describe("parseDiffLines", () => {
  it("classifies add/del/hunk/ctx", () => {
    const rows = parseDiffLines(
      "@@ -1,2 +1,2 @@\n-old line\n+new line\n context"
    );
    expect(rows.map((r) => r.type)).toEqual([
      "hunk",
      "del",
      "add",
      "ctx",
    ]);
    expect(rows[1]).toEqual({ type: "del", sign: "-", text: "old line" });
    expect(rows[2]).toEqual({ type: "add", sign: "+", text: "new line" });
  });
});

describe("unwrapShikiPre", () => {
  it("strips outer shiki pre", () => {
    const html =
      '<pre class="shiki github-light"><code><span class="line">x</span></code></pre>';
    expect(unwrapShikiPre(html)).toBe(
      '<code><span class="line">x</span></code>'
    );
  });
});

describe("markdown fence structure", () => {
  it("pre unwrap yields a single kimi fence shell without nested markdown pre", () => {
    const Pre = ({ children }: { children?: ReactNode }) => <>{children}</>;
    const Code = ({
      className,
      children,
    }: {
      className?: string;
      children?: ReactNode;
    }) => {
      const match = /language-(\w+)/.exec(className || "");
      const text = String(children ?? "").replace(/\n$/, "");
      if (match) {
        return <KimiCodeFence code={text} language={match[1]} />;
      }
      return <code>{children}</code>;
    };
    const html = renderToStaticMarkup(
      <ReactMarkdown remarkPlugins={[gfm]} components={{ pre: Pre, code: Code }}>
        {"```python\nprint(1)\n```"}
      </ReactMarkdown>
    );
    expect(html).toContain("kimi-code-fence");
    expect(html).toContain("python");
    expect(html.indexOf("<pre")).toBe(html.lastIndexOf("<pre"));
    expect(html).not.toContain('class="shiki');
  });
});
