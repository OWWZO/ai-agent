import React from "react";
import classNames from "classnames";
import { Alert, Image } from "antd";

const ERROR_CLASS =
  "m-12 md:m-24 min-w-[260px] max-w-[calc(100%-24px)] md:max-w-[calc(100%-48px)] [&_.ant-alert-description]:break-words [&_.ant-alert-description]:whitespace-normal";

interface ImageRendererProps {
  imageUrl: string;
  fileName?: string;
  missingReason?: string;
  className?: string;
}

const ImageRenderer: ReactorType.FC<ImageRendererProps> = React.memo((props) => {
  const { imageUrl, fileName, missingReason, className } = props;

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
      <div className="ws-doc-media mx-auto w-full max-w-[720px] overflow-hidden rounded-2xl border border-[var(--chat-border)]/70 bg-white p-3 shadow-[0_1px_2px_oklch(0%_0_0_/_0.03)] sm:p-4">
        <Image
          src={imageUrl}
          alt={fileName || "图片预览"}
          className="!max-w-full"
          style={{
            maxHeight: "min(72vh, 960px)",
            width: "100%",
            objectFit: "contain",
          }}
        />
      </div>
    </div>
  );
});

ImageRenderer.displayName = "ImageRenderer";

export default ImageRenderer;
