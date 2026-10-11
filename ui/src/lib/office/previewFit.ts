/**
 * 文档预览的「适应宽度」换算。
 *
 * 从 ZCode `packages/ui/src/lib/officeFilePreview.ts` 移植，语义保持一致：
 * **只缩小、不放大** —— 纸张按原始像素宽度渲染（docx 默认 A4 ≈ 794px），
 * 窄面板下若不放缩就会被撑破并出现横向滚动条。
 */

export interface DocxPreviewFit {
  scale: number;
  width: number;
  height: number;
}

/**
 * 按可视区可用宽度换算缩放比与包装层尺寸。
 *
 * `scale = min(1, availableWidth / naturalWidth)`：内容比可视区窄时保持 1，
 * 不做放大（放大只会把纸张拉模糊，且与官方预览行为不一致）。
 */
export function calculateDocxPreviewFit({
  availableWidth,
  naturalHeight,
  naturalWidth,
}: {
  availableWidth: number;
  naturalHeight: number;
  naturalWidth: number;
}): DocxPreviewFit | null {
  if (
    !Number.isFinite(availableWidth) ||
    !Number.isFinite(naturalHeight) ||
    !Number.isFinite(naturalWidth) ||
    availableWidth <= 0 ||
    naturalHeight <= 0 ||
    naturalWidth <= 0
  ) {
    return null;
  }

  const scale = Math.min(1, availableWidth / naturalWidth);
  return {
    scale,
    width: naturalWidth * scale,
    height: naturalHeight * scale,
  };
}

/**
 * 判断两次 fit 是否「足够接近」。
 *
 * 拖动面板会按帧触发 ResizeObserver，浮点抖动若直接写入 state 会导致
 * 每帧重渲染；用阈值把亚像素变化吸收掉。
 */
export function isSameDocxPreviewFit(
  current: DocxPreviewFit | null,
  next: DocxPreviewFit
): boolean {
  return (
    current !== null &&
    Math.abs(current.scale - next.scale) < 0.001 &&
    Math.abs(current.width - next.width) < 0.5 &&
    Math.abs(current.height - next.height) < 0.5
  );
}
