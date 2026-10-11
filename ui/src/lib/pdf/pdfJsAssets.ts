/**
 * pdf.js 的两类外部资源：
 * - cMaps：Type0/CID 字体只声明预定义 CMap（如 ReportLab 的 STSong-Light）而 PDF 内并未
 *   嵌入字符映射时，必须显式提供，否则中文会渲染为空 / 取词乱码。
 * - standardFonts：未嵌入的 14 个标准字体（Helvetica / Times 等）的替代字体数据。
 *
 * 两者都随 pdfjs-dist 发布，由 vite 插件挂到 `${BASE_URL}pdfjs/*` 下（见 vite.config.ts）。
 */
export interface PdfJsDocumentOptions {
  cMapPacked: boolean;
  cMapUrl: string;
  standardFontDataUrl: string;
}

export function createPdfJsDocumentOptions(
  viteBaseUrl: string,
  documentUrl: string,
): PdfJsDocumentOptions {
  const applicationBaseUrl = new URL(viteBaseUrl || "/", documentUrl);
  return {
    cMapPacked: true,
    cMapUrl: new URL("pdfjs/cmaps/", applicationBaseUrl).href,
    standardFontDataUrl: new URL("pdfjs/standard_fonts/", applicationBaseUrl).href,
  };
}
