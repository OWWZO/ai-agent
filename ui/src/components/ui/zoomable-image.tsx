import { useState, type CSSProperties } from "react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";

interface ZoomableImageProps {
  src: string;
  alt: string;
  /** 缩略图尺寸样式；放大后不受影响。 */
  style?: CSSProperties;
  className?: string;
  /** 点击放大时给用户的提示文案，undefined 表示不显示提示。 */
  maskText?: string;
}

/**
 * 点击放大的图片。
 *
 * 替代 antd `Image` 的 `preview`：缩略图按传入样式渲染，点击后在 Dialog 里
 * 以视口最大尺寸展示原图。只依赖 `ui/dialog`，不引入图片预览库。
 */
export function ZoomableImage({
  src,
  alt,
  style,
  className,
  maskText = "点击放大",
}: ZoomableImageProps) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        className={cn("group relative block cursor-zoom-in", className)}
        style={style}
        onClick={() => setOpen(true)}
        aria-label={maskText}
      >
        {/* 宽度由外层 button 的 style 决定（Retina 素材要按物理像素折算），
            这里只兜底不溢出 —— 写 `w-full` 会把 style.width 盖掉。 */}
        <img src={src} alt={alt} className="block h-auto max-w-full" />
        {maskText ? (
          <span className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/40 text-sm text-white opacity-0 transition-opacity group-hover:opacity-100">
            {maskText}
          </span>
        ) : null}
      </button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-[96vw] border-0 bg-transparent p-0 shadow-none">
          <DialogTitle className="sr-only">{alt}</DialogTitle>
          <img
            src={src}
            alt={alt}
            className="max-h-[92vh] max-w-[96vw] object-contain"
          />
        </DialogContent>
      </Dialog>
    </>
  );
}
