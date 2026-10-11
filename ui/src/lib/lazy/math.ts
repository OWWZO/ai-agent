import type { Options } from "react-markdown";

export interface MathPlugins {
  remarkPlugins: NonNullable<Options["remarkPlugins"]>;
  rehypePlugins: NonNullable<Options["rehypePlugins"]>;
}

let mathPluginsPromise: Promise<MathPlugins> | undefined;

/**
 * 按需加载数学公式支持（remark-math + rehype-katex + katex 样式）。
 *
 * katex 及样式体积可观，绝大多数 markdown 里没有公式，所以只在
 * `containsMath()` 命中时才真正拉取这个 chunk。
 */
export function loadMathPlugins(): Promise<MathPlugins> {
  return (mathPluginsPromise ??= import("./katexPlugins").then(
    (module) => module.mathPlugins
  ));
}

/**
 * 粗判文本里是否可能出现数学公式，用于决定要不要加载 katex。
 *
 * 这里只做「值不值得加载」的预筛，真正的语法判定交给 remark-math。
 * 允许误判为 true（多加载一个 chunk），但不允许漏判导致公式不渲染。
 *
 * 注意 `$100 到 $200` 这类金额写法：remark-math 要求收尾 `$` 前不能是空白，
 * 因此不会被当成公式，这里也就无需特意排除。
 */
export function containsMath(content: string): boolean {
  if (!content || !content.includes("$")) return false;
  // 块级 $$...$$
  if (/\$\$[\s\S]+?\$\$/.test(content)) return true;
  // 行内 $...$：开头不能是空白，结尾不能是空白
  return /(^|[^\\$])\$(?!\s)[^$\n]*?[^\s$]\$(?!\$)/.test(content);
}
