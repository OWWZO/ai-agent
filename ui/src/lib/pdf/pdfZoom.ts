/**
 * 多页连续滚动视图下的缩放常量与几何工具。
 * 说明：单页视图时代用过「复制位图做双缓冲」的 overlay 方案，连续滚动下页面是纵向堆叠的，
 * 该方案不再适用，改为「缩放提交时按当前页锚点校正滚动位置」。
 */
export const ZOOM_STEP = 0.25;
export const MIN_SCALE = 0.25;
export const MAX_SCALE = 4;
export const DEFAULT_SCALE = 1;
// 修饰键 + 滚轮的连续缩放灵敏度：兼容触控板高频小 delta 和鼠标滚轮大 delta。
export const WHEEL_ZOOM_SENSITIVITY = 0.002;
// 手势停顿后才提交真实渲染，连续手势期间只更新百分比显示。
export const ZOOM_COMMIT_DELAY_MS = 180;

// 页面尺寸未知时的兜底（A4 / 72dpi）。
export const DEFAULT_PAGE_SIZE = { width: 595, height: 842 };

export interface PdfPageSize {
  width: number;
  height: number;
}

export function clampScale(scale: number): number {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
}
