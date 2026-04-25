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
        "flex min-h-full items-center justify-center px-4 py-6",
        className
      )}
    >
      {/* 约束最大高度，避免长图或大图把工作区撑坏，同时保留点击放大预览能力。 */}
      <Image
        src={imageUrl}
        alt={fileName || "图片预览"}
        className="max-w-full"
        style={{
          maxHeight: "min(72vh, 960px)",
          objectFit: "contain",
        }}
      />
    </div>
  );
});

ImageRenderer.displayName = "ImageRenderer";

export default ImageRenderer;
