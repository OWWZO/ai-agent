import React, { useEffect, useMemo, useState, type CSSProperties } from "react";
import classNames from "classnames";
import { Alert, Image } from "antd";

const ERROR_CLASS =
  "m-12 md:m-24 min-w-[260px] max-w-[calc(100%-24px)] md:max-w-[calc(100%-48px)] [&_.ant-alert-description]:break-words [&_.ant-alert-description]:whitespace-normal";

/**
 * 透明底的棋盘格。
 *
 * 透明 PNG / WebP 直接放在纯白底上会看不出透明区域（尤其是截图类素材），
 * 棋盘格是图片编辑器的通行做法。颜色取本项目的主题变量，暗色下自动跟随。
 */
const CHECKERBOARD_STYLE: CSSProperties = {
  backgroundColor: "var(--chat-surface)",
  backgroundImage: [
    "linear-gradient(45deg, var(--chat-surface-muted) 25%, transparent 25%)",
    "linear-gradient(-45deg, var(--chat-surface-muted) 25%, transparent 25%)",
    "linear-gradient(45deg, transparent 75%, var(--chat-surface-muted) 75%)",
    "linear-gradient(-45deg, transparent 75%, var(--chat-surface-muted) 75%)",
  ].join(","),
  backgroundPosition: "0 0, 0 4px, 4px -4px, -4px 0",
  backgroundSize: "8px 8px",
};

/**
 * 从文件名里解析 Retina 物理倍率：`icon@2x.png` → 2。
 * 这类素材的像素尺寸是物理像素，直接按自然尺寸显示会被放大一倍。
 */
export function getImagePixelRatio(sourceName?: string | null): number {
  const leaf = String(sourceName ?? "")
    .replace(/\\/g, "/")
    .split("/")
    .pop() ?? "";
  const match = /@(\d+(?:\.\d+)?)x(?=(?:\.[^./\\]+)?$)/i.exec(leaf);
  const ratio = match ? Number(match[1]) : 1;
  return Number.isFinite(ratio) && ratio > 0 ? ratio : 1;
}

interface ImageRendererProps {
  imageUrl: string;
  fileName?: string;
  missingReason?: string;
  className?: string;
}

interface NaturalSize {
  source: string;
  width: number;
  height: number;
}

const ImageRenderer: ReactorType.FC<ImageRendererProps> = React.memo((props) => {
  const { imageUrl, fileName, missingReason, className } = props;

  const [naturalSize, setNaturalSize] = useState<NaturalSize | null>(null);
  const pixelRatio = useMemo(
    () => getImagePixelRatio(fileName || imageUrl),
    [fileName, imageUrl]
  );

  // 只有 Retina 素材（@2x/@3x）才需要知道自然尺寸，普通图片零开销。
  //
  // 这里刻意**不用** antd `Image` 的 onLoad：rc-image 内部会用自己的 onLoad 覆盖透传的同名
  // 属性，回调根本不会到我们手上（实测 @2x 素材不会折算）。用一个游离的 Image 预加载最稳。
  useEffect(() => {
    if (pixelRatio <= 1 || !imageUrl) {
      setNaturalSize(null);
      return;
    }
    let cancelled = false;
    const probe = new window.Image();
    probe.onload = () => {
      if (cancelled) return;
      setNaturalSize({
        source: imageUrl,
        width: probe.naturalWidth,
        height: probe.naturalHeight,
      });
    };
    probe.onerror = () => {
      if (!cancelled) setNaturalSize(null);
    };
    probe.src = imageUrl;
    return () => {
      cancelled = true;
    };
  }, [imageUrl, pixelRatio]);

  // Retina 素材（@2x/@3x）折算回 CSS 像素，避免被放大显示；普通图片保持自适应。
  const imageStyle = useMemo<CSSProperties>(() => {
    const base: CSSProperties = {
      maxWidth: "100%",
      maxHeight: "min(72vh, 960px)",
      objectFit: "contain",
    };
    if (
      pixelRatio <= 1 ||
      naturalSize?.source !== imageUrl ||
      naturalSize.width <= 0 ||
      naturalSize.height <= 0
    ) {
      return { ...base, width: "100%" };
    }
    return {
      ...base,
      width: naturalSize.width / pixelRatio,
      height: naturalSize.height / pixelRatio,
    };
  }, [imageUrl, naturalSize, pixelRatio]);

  if (missingReason || !imageUrl) {
    return (
      <Alert
        type="error"
        message="图片不可读取"
        description={missingReason || "引用资源不存在或已失效"}
        showIcon
        className={ERROR_CLASS}
      />
    );
  }

  return (
    <div
      className={classNames(
        "ws-preview-stage flex min-h-full items-center justify-center px-4 py-6",
        className
      )}
    >
      <div
        className="ws-doc-media mx-auto w-full max-w-[720px] overflow-hidden rounded-2xl border border-[var(--chat-border)]/70 p-3 shadow-[0_1px_2px_oklch(0%_0_0_/_0.03)] sm:p-4"
        style={CHECKERBOARD_STYLE}
      >
        <Image
          src={imageUrl}
          alt={fileName || "图片预览"}
          className="!max-w-full"
          style={imageStyle}
          preview={{ mask: "点击放大" }}
        />
      </div>
    </div>
  );
});

ImageRenderer.displayName = "ImageRenderer";

export default ImageRenderer;
