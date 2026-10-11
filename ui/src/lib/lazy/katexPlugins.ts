import "katex/dist/katex.min.css";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import type { Options } from "react-markdown";

/**
 * 数学公式插件集合（独立模块，便于整块懒加载）。
 *
 * 这里同时静态引入 katex 样式：样式必须和渲染公式的 katex 实例**版本一致**，
 * 否则类名对不上（0.16 用 `.base`，0.19 改成 `.katex-base`），公式会完全错版。
 * `rehype-katex` 依赖 `katex@^0.16.0`，因此 package.json 里 katex 也锁在 `^0.16.0`。
 */
export const mathPlugins: {
  remarkPlugins: NonNullable<Options["remarkPlugins"]>;
  rehypePlugins: NonNullable<Options["rehypePlugins"]>;
} = {
  remarkPlugins: [remarkMath],
  rehypePlugins: [rehypeKatex],
};
