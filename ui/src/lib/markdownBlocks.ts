/**
 * Markdown 块级切分的纯逻辑。
 *
 * 抽成独立模块（同 `Dialogue/markdown/codeHighlightPolicy.ts`）：
 * 1. 策略是纯函数，可以直接断言，不必搭渲染环境；
 * 2. `MarkdownRenderer` 只导出组件，避免 fast-refresh 失效。
 */

import { parseMarkdownIntoBlocks } from "streamdown";

/**
 * 链接引用定义：`[ref]: https://example.com`。
 *
 * 块级切分会把它与引用它的正文切到不同块，而每个块是**独立文档**解析的 ——
 * 定义不在同一文档里，`[文档][ref]` 就会退化成字面文本。
 * 实测：`参见 [文档][ref]。` 单独渲染 → `参见 [文档][ref]。`（链接丢失）。
 */
const LINK_DEF_LINE = /^\s{0,3}\[[^\]^]+\]:\s*\S+/;

/**
 * 脚注定义：`[^1]: 脚注内容`。
 *
 * 必须**排除**：它不像链接定义那样渲染为空，而是会渲染出一整个
 * `<section data-footnotes>`。追加到每个块会让脚注区重复 N 次。
 * 实测：脚注引用与定义切到同一块时切分结果与整段渲染完全一致，无需处理。
 */
const FOOTNOTE_DEF_LINE = /^\s{0,3}\[\^[^\]]+\]:/;

/**
 * 收集全部块里的链接引用定义（去重、保序）。
 *
 * 返回空串表示「这份内容没有链接引用定义」，调用方据此跳过追加。
 *
 * ⚠️ 必须跳过代码围栏内的行。围栏里的 `[ref]: url` 只是被展示的文本，
 * CommonMark 不会把它当作链接定义 —— 实测整段渲染 `WHOLE_HAS_ANCHOR=false`，
 * 若把它提升为文档级定义再追加给正文块，就会凭空生成指向该 URL 的真实 `<a>`：
 * 任何展示 YAML / Markdown 示例配置的回答都会中招（内容完整性 + 链接注入）。
 */
export function collectLinkDefinitions(blocks: readonly string[]): string {
  const defs: string[] = [];
  const seen = new Set<string>();

  for (const block of blocks) {
    let fence: string | null = null;
    for (const line of block.split("\n")) {
      const fenceMatch = /^\s{0,3}(`{3,}|~{3,})/.exec(line);
      if (fenceMatch) {
        const marker = fenceMatch[1][0];
        if (fence === null) {
          fence = marker;
        } else if (fence === marker) {
          fence = null;
        }
        continue;
      }
      // 围栏内（含缩进代码块）一律不参与定义收集。
      if (fence !== null || /^\s{4,}\S/.test(line)) {
        continue;
      }
      if (FOOTNOTE_DEF_LINE.test(line)) {
        continue;
      }
      if (LINK_DEF_LINE.test(line) && !seen.has(line)) {
        seen.add(line);
        defs.push(line);
      }
    }
  }

  return defs.join("\n");
}

/**
 * 把 Markdown 切成可独立渲染的块。
 *
 * 流式期间每帧只有**最后一块**在增长，前面几块内容不变 —— 配上逐块 `memo`
 * 就能只重解析最后一块，而不是每帧整段重解析（实测 400 行累计场景 4.4x）。
 *
 * 切分本身用 `streamdown` 导出的 `parseMarkdownIntoBlocks`：它已经处理了
 * 未闭合围栏、HTML 块、表格等边界，不自己再写一遍切分规则。
 */
export function splitMarkdownBlocks(content: string): string[] {
  return parseMarkdownIntoBlocks(content);
}

/**
 * 给单个块补上链接引用定义。
 *
 * 定义段渲染出来是**空的**（实测 `[ref]: https://e.com` → `""`），所以追加
 * 它不会多出可见内容，只是让 `[文档][ref]` 能在块内解析成真正的链接。
 *
 * 只追加**该块还没有**的那几行。原先用 `block.includes(defs)` 整组判等：
 * 块只含多个定义中的某一个时判定失败，整组被重复追加（实测
 * `"[r]: https://x.com"` → `"[r]: https://x.com\n\n[r]: https://x.com\n[r2]: https://y.com"`）。
 * CommonMark 取首个定义，所以不致错渲染，但每帧都要多解析这段冗余文本。
 */
export function withLinkDefinitions(block: string, defs: string): string {
  if (!defs) {
    return block;
  }
  const existing = new Set(block.split("\n"));
  const missing = defs.split("\n").filter((line) => !existing.has(line));
  if (missing.length === 0) {
    return block;
  }
  return `${block}\n\n${missing.join("\n")}`;
}
