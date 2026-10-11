export type XlsxViewerModule = typeof import("@extend-ai/react-xlsx");

let xlsxViewerPromise: Promise<XlsxViewerModule> | undefined;

/**
 * 懒加载 `@extend-ai/react-xlsx`（WASM 版表格查看器）。
 *
 * 与 ZCode `previewPaneOfficeXlsxContent.tsx` 一致：库需要先通过 `setWasmSource()`
 * 指定 duke_sheets 的 wasm 地址，之后 `XlsxViewer` 才会初始化引擎。
 * 整个模块（含 wasm）都很重，必须独立分包、按需拉取。
 */
export function loadXlsxViewer(): Promise<XlsxViewerModule> {
  xlsxViewerPromise ??= (async () => {
    const [mod, wasmModule] = await Promise.all([
      import("@extend-ai/react-xlsx"),
      import("@extend-ai/react-xlsx/duke_sheets_wasm_bg.wasm?url"),
    ]);
    mod.setWasmSource(wasmModule.default);
    return mod;
  })();
  return xlsxViewerPromise;
}
