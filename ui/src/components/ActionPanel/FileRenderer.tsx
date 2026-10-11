import React, { useMemo } from "react";
import { useRequest } from "ahooks";
import { Alert } from "antd";
import MarkdownFileRenderer from "./MarkdownFileRenderer";
import SvgFileRenderer from "./SvgFileRenderer";
import MediaFileRenderer from "./MediaFileRenderer";
import HTMLRenderer from "./HTMLRenderer";
import ImageRenderer from "./ImageRenderer";
import DocumentFallback from "./DocumentFallback";
import SourceCodeView from "./SourceCodeView";
import Loading from "./Loading";
import GenUiModel3D from "@/components/genui/GenUiModel3D";
import { isBinaryBackedKind, resolveFilePreviewKind } from "@/lib/filePreview/kinds";
import { resolveCodeLanguage } from "@/lib/filePreview/language";
import { cn } from "@/lib/utils";

const LOADING_CLASS = "mr-32";
const ERROR_CLASS =
  "m-12 md:m-24 min-w-[260px] max-w-[calc(100%-24px)] md:max-w-[calc(100%-48px)] [&_.ant-alert-description]:break-words [&_.ant-alert-description]:whitespace-normal";

interface FileRendererProps {
  fileUrl: string;
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
  className?: string;
  /** 强制源码高亮（忽略 HTML iframe / 图形化预览） */
  forceSource?: boolean;
}

const resolveUnavailableReason = (error: Error) => {
  const message = error?.message || "";
  if (
    message.includes("Failed to fetch") ||
    message.includes("Network response was not ok") ||
    message.includes("NetworkError")
  ) {
    return "引用资源不存在或已失效";
  }
  return message || "引用资源不存在或已失效";
};

/**
 * 通用文件渲染器：按 `FilePreviewKind` 分派到对应预览器。
 *
 * 这是「不知道文件是什么、只能靠扩展名判断」时的兜底入口 —— 类型判定统一走
 * `resolveFilePreviewKind()`，本组件不再自己维护扩展名表。
 */
const FileRenderer: ReactorType.FC<FileRendererProps> = React.memo((props) => {
  const {
    fileUrl,
    fileName,
    downloadUrl,
    missingReason,
    className,
    forceSource = false,
  } = props;

  const kind = useMemo(
    () => resolveFilePreviewKind({ name: fileName, url: fileUrl }),
    [fileName, fileUrl]
  );
  const language = useMemo(
    () => resolveCodeLanguage(fileName || fileUrl),
    [fileName, fileUrl]
  );
  const resolvedDownload = downloadUrl || fileUrl;

  // 强制源码模式下不按类型分派，但**仍然需要把内容取回来**当纯文本读。
  const shouldFetchText =
    forceSource ||
    kind === "code" ||
    kind === "text" ||
    kind === "unknown" ||
    kind === "diff" ||
    kind === "csv";

  const { data, loading, error } = useRequest(
    async () => {
      if (missingReason) {
        throw new Error(missingReason);
      }
      if (!fileUrl) {
        throw new Error("引用资源不存在或已失效");
      }
      const response = await fetch(fileUrl);
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      return await response.text();
    },
    {
      refreshDeps: [fileUrl, missingReason, forceSource],
      ready: shouldFetchText,
    }
  );

  // ---- 强制源码模式：任何类型都当纯文本读 ----
  if (forceSource) {
    if (loading) {
      return (
        <div className={cn("flex h-full min-h-[200px] items-center justify-center bg-white", className)}>
          <Loading className={LOADING_CLASS} />
        </div>
      );
    }
    if (error) {
      return (
        <div className={cn("bg-white p-6", className)}>
          <Alert
            type="error"
            message="内容不可读取"
            description={resolveUnavailableReason(error as Error)}
            showIcon
            className={ERROR_CLASS}
          />
        </div>
      );
    }
    return (
      <SourceCodeView
        code={data || ""}
        language={language}
        className={className}
        sourcePath={fileName}
      />
    );
  }

  // ---- 有专门渲染器的类型：直接委派 ----
  switch (kind) {
    case "image":
      return (
        <ImageRenderer
          imageUrl={fileUrl}
          fileName={fileName}
          missingReason={missingReason}
          className={className}
        />
      );
    case "svg":
      return (
        <SvgFileRenderer
          fileUrl={fileUrl}
          fileName={fileName}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          className={className}
        />
      );
    case "markdown":
      return (
        <MarkdownFileRenderer
          fileUrl={fileUrl}
          fileName={fileName}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          className={className}
        />
      );
    case "video":
    case "audio":
      return (
        <MediaFileRenderer
          kind={kind}
          fileUrl={fileUrl}
          fileName={fileName}
          missingReason={missingReason}
          className={className}
        />
      );
    case "html":
      return (
        <HTMLRenderer
          htmlUrl={fileUrl}
          downloadUrl={resolvedDownload}
          missingReason={missingReason}
          showToolBar
          className={className}
        />
      );
    case "model3d":
      if (missingReason || !fileUrl) {
        return (
          <DocumentFallback
            label="GLB"
            title="3D 模型不可读取"
            description={missingReason || "引用资源不存在或已失效"}
            fileName={fileName}
            downloadUrl={resolvedDownload}
            className={className}
            type="info"
          />
        );
      }
      return (
        <div className={cn("flex h-full min-h-[360px] items-stretch bg-[#0f172a] p-3", className)}>
          <div className="min-h-[360px] w-full">
            <GenUiModel3D src={fileUrl} caption={fileName} height={520} />
          </div>
        </div>
      );
    default:
      break;
  }

  // ---- 二进制：引导下载 ----
  if (isBinaryBackedKind(kind)) {
    return (
      <DocumentFallback
        label={(fileName?.split(".").pop() || "FILE").toUpperCase()}
        title="该文件类型不支持在线预览"
        description={
          missingReason ||
          "二进制或办公文档无法以文本方式打开，请下载后使用本地应用查看。"
        }
        fileName={fileName}
        downloadUrl={resolvedDownload}
        className={className}
        type="info"
      />
    );
  }

  // ---- 其余（code / text / csv / diff / unknown）：按文本高亮 ----
  if (loading) {
    return (
      <div className={cn("flex h-full min-h-[200px] items-center justify-center bg-white", className)}>
        <Loading className={LOADING_CLASS} />
      </div>
    );
  }

  if (error) {
    return (
      <div className={cn("bg-white p-6", className)}>
        <Alert
          type="error"
          message="内容不可读取"
          description={resolveUnavailableReason(error as Error)}
          showIcon
          className={ERROR_CLASS}
        />
      </div>
    );
  }

  return (
    <SourceCodeView
      code={data || ""}
      language={language}
      className={className}
      sourcePath={fileName}
    />
  );
});

FileRenderer.displayName = "FileRenderer";

export default FileRenderer;
