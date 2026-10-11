/**
 * 旧版 Word（`.doc`）预览引擎 —— 懒加载 `@extend-ai/react-docx`。
 *
 * ## 为什么 `.doc` 能用 OOXML 库解析
 *
 * 该库的 WASM 解析器**不只吃 OOXML**：它对 OLE2/CFB（复合文档，魔数
 * `d0 cf 11 e0 a1 b1 1a e1`，即 Word 97-2003 的 `.doc`）同样能解析，并把结果
 * 归一成同一个 `OoxmlPackage`（part 名仍是 `[Content_Types].xml` / `word/document.xml`）。
 * 实测三个真实 `.doc`（课程设计任务书、大作业报告、概率论考卷）全部解析成功、无 warning。
 *
 * ## 为什么不能「转成 .docx 再交给 docx-preview」
 *
 * 最初的实现是 `parseDocx → packageToArrayBuffer → docx-preview.renderAsync`，
 * 文本能全部拿到，但**分页信息在转换中丢失**：多页文档只渲染出 1 个 `<section>`
 * （1056px，`overflow: hidden`），第 2 页起的内容被裁掉不可见（同内容 `.docx` 是 3 页）。
 * 因此 `.doc` 必须走 react-docx 自己的排版引擎，和 ZCode `previewPaneOfficeLegacyDocContent`
 * 的做法一致（ZCode 的 `.docx` 用 docx-preview、`.doc` 用 react-docx，两个引擎本来就是分开的）。
 *
 * ## 为什么自己解析 + 传 model，而不用 `useDocxModel` / `file` prop
 *
 * `useDocxModel` 和 `<ReactDocxViewer file={...}>` 都会走 `importDocxBuffer()`，
 * 它在浏览器里 `new Worker(new URL("./docx-import-worker.js", import.meta.url))`。
 * 这个相对 `import.meta.url` 的写法一旦被 vite 依赖预打包，worker 就会解析到
 * `/node_modules/.vite/deps/docx-import-worker.js`（404）—— 与 `@extend-ai/react-xlsx`
 * 上一轮踩的坑完全一样（见 `plans/024`）。
 *
 * 而 `ReactDocxViewer` 的 `model` prop 是官方支持的入口：传入预构建模型时
 * **完全跳过解析与 worker**。所以这里自己 `parseDocx → buildDocModel`，再把 model 交给它。
 *
 * ## 代价
 *
 * JS 约 1.03 MB + wasm 1.07 MB，必须懒加载 —— 只有真正打开 `.doc` 时才拉取。
 */

export type LegacyDocModule = typeof import("@extend-ai/react-docx");

let modulePromise: Promise<LegacyDocModule> | undefined;

/** 懒加载并设置 wasm 地址；整个模块（含 wasm）都很重，只在打开 .doc 时拉取。 */
export function loadLegacyDocModule(): Promise<LegacyDocModule> {
  modulePromise ??= (async () => {
    const [mod, wasmModule] = await Promise.all([
      import("@extend-ai/react-docx"),
      import("@extend-ai/react-docx/docx_wasm_bg.wasm?url"),
    ]);
    // 必须在任何解析动作之前指定 wasm 地址，否则库会用默认路径去取。
    mod.setWasmSource(wasmModule.default);
    return mod;
  })();
  return modulePromise;
}

/**
 * 把旧版 `.doc` 字节解析成 react-docx 的文档模型。
 *
 * `ensureDocModelBlockIds` 是库内部 `importDocxOnMainThread` 的同款收尾步骤，
 * 缺了它渲染时拿不到稳定的块 id。
 */
export async function buildLegacyDocModel(
  buffer: ArrayBuffer
): Promise<Awaited<ReturnType<LegacyDocModule["buildDocModel"]>>> {
  const mod = await loadLegacyDocModule();
  const pkg = await mod.parseDocx(buffer);
  return mod.ensureDocModelBlockIds(await mod.buildDocModel(pkg));
}
