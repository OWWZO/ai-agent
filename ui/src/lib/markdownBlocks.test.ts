import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ReactMarkdown from "react-markdown";
import gfm from "remark-gfm";
import {
  collectLinkDefinitions,
  splitMarkdownBlocks,
  withLinkDefinitions,
} from "@/lib/markdownBlocks";

function renderBlocks(content: string): string {
  const blocks = splitMarkdownBlocks(content);
  const defs = collectLinkDefinitions(blocks);
  return blocks
    .map((block) =>
      renderToStaticMarkup(
        createElement(ReactMarkdown, { remarkPlugins: [gfm] }, withLinkDefinitions(block, defs))
      )
    )
    .join("\n");
}

function renderWhole(content: string): string {
  return renderToStaticMarkup(
    createElement(ReactMarkdown, { remarkPlugins: [gfm] }, content)
  );
}

/**
 * 块级切分必须与整段渲染**语义等价**。
 *
 * 实测已知差异：块之间原有的空行会被吃掉（`<p>a</p>` 之后直接接 `<p>b</p>`，
 * 少了整段渲染时的那一个换行）。这属于**块间距**，不是内容差异 —— 由外层
 * CSS（`space-y-*` / `> * + *`）负责，与 `streamdown` 自己的处理一致。
 * 所以这里比对的是「标签序列」，不是逐字节相等。
 */
function tagSequence(html: string): string {
  return (html.match(/<\/?[a-zA-Z][^>]*>/g) || []).join(" ");
}

describe("markdownBlocks", () => {
  const cases: Record<string, string> = {
    paragraphs: "第一段\n\n第二段\n\n第三段",
    heading: "# 标题\n\n段落一\n\n段落二",
    orderedList: "1. 第一步\n\n2. 第二步\n\n3. 第三步",
    tightList: "- a\n- b\n- c",
    nestedList: "- 一级\n  - 二级\n\n- 另一个",
    table: "| a | b |\n| --- | --- |\n| 1 | 2 |\n\n后续段落",
    blockquote: "> 引用一\n> 引用二\n\n普通段",
    fence: "正文\n\n```ts\nconst a = 1;\n```\n\n结尾",
    taskList: "- [x] 完成\n- [ ] 未完成",
    autolink: "见 https://example.com 与 www.a.com",
    emphasis: "**粗** _斜_ ~~删~~ `码`",
    footnote: "正文[^1]\n\n[^1]: 脚注内容",
    refLink: "参见 [文档][ref] 与 [站内][ref]。\n\n[ref]: https://example.com",
    refLinkSplit: "[a][r] 第一段\n\n[r]: https://x.com\n\n[b][r] 第二段",
    hardBreak: "第一行  \n第二行",
    htmlBlock: "<div>\n<span>x</span>\n</div>\n\n段落",
    inlineMath: "公式 $E=mc^2$ 行内\n\n$$\n\\frac{1}{2}\n$$",
    setext: "标题\n===\n\n段落",
    mixed:
      "# 标题\n\n- 列表一\n- 列表二\n\n| a | b |\n| --- | --- |\n| 1 | 2 |\n\n> 引用\n\n```js\nlet x=1;\n```\n\n收尾段落",
  };

  for (const [name, md] of Object.entries(cases)) {
    it(`${name}: 块级渲染与整段渲染标签序列一致`, () => {
      expect(tagSequence(renderBlocks(md))).toBe(tagSequence(renderWhole(md)));
    });
  }

  it("引用式链接在切块后仍解析为 <a>（这是必须补定义的原因）", () => {
    const md = "参见 [文档][ref]。\n\n[ref]: https://example.com";
    expect(renderBlocks(md)).toContain('href="https://example.com"');
  });

  it("流式中途：定义还没写完时也不崩、且能解析出半截 URL", () => {
    const partial = "参见 [文档][ref]。\n\n[ref]: http";
    const blocks = splitMarkdownBlocks(partial);
    const defs = collectLinkDefinitions(blocks);
    expect(defs).toBe("[ref]: http");
    expect(renderBlocks(partial)).toContain('href="http"');
  });

  it("脚注定义不会被追加（否则脚注区会重复渲染）", () => {
    const md = "正文[^1]\n\n[^1]: 脚注内容";
    const blocks = splitMarkdownBlocks(md);
    const defs = collectLinkDefinitions(blocks);
    // 脚注定义被显式排除，且正文块已自带定义 → withLinkDefinitions 原样返回
    expect(defs).toBe("");
    expect(blocks.map((b) => withLinkDefinitions(b, defs))).toEqual(blocks);
  });
});
