import React, { useEffect, useMemo, useState } from "react";
import { useRequest } from "ahooks";
import { Alert } from "antd";
import type { BundledLanguage } from "shiki";
import { bundledLanguages } from "shiki";
import MarkdownRenderer from "./MarkdownRenderer";
import HTMLRenderer from "./HTMLRenderer";
import ImageRenderer from "./ImageRenderer";
import DocumentFallback from "./DocumentFallback";
import Loading from "./Loading";
import { highlightCode } from "@/components/ai-elements/code-block";
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
  /** 强制源码高亮（忽略 HTML iframe 直出） */
  forceSource?: boolean;
}

const MARKDOWN_EXTS = new Set(["md", "markdown", "txt"]);
const HTML_EXTS = new Set(["html", "htm"]);
/** 不应按文本解码的二进制/媒体扩展名 */
const BINARY_DOWNLOAD_EXTS = new Set([
  "zip",
  "rar",
  "7z",
  "tar",
  "gz",
  "bz2",
  "exe",
  "dll",
  "so",
  "dmg",
  "pkg",
  "apk",
  "mp3",
  "mp4",
  "mov",
  "avi",
  "mkv",
  "webm",
  "wav",
  "flac",
  "aac",
  "ogg",
  "ppt",
  "pptx",
  "pps",
  "ppsx",
  "doc",
  "docx",
  "xls",
  "xlsx",
  "xlsm",
  "pdf",
  // 图片不在此列：走 ImageRenderer，避免被误判成「不支持预览」
  "woff",
  "woff2",
  "ttf",
  "otf",
  "eot",
  "bin",
  "dat",
  "wasm",
]);

const IMAGE_PREVIEW_EXTS = new Set([
  "png",
  "jpg",
  "jpeg",
  "gif",
  "webp",
  "bmp",
  "ico",
  "svg",
  "avif",
]);

const LANG_ALIAS: Record<string, string> = {
  py: "python",
  js: "javascript",
  ts: "typescript",
  tsx: "tsx",
  jsx: "jsx",
  yml: "yaml",
  sh: "bash",
  shell: "bash",
  md: "markdown",
  htm: "html",
};

const getFileExtension = (fileName?: string): string => {
  return fileName?.split(".").pop()?.toLowerCase() || "";
};

const resolveLanguage = (ext: string): BundledLanguage => {
  const mapped = LANG_ALIAS[ext] || ext;
  if (mapped in bundledLanguages) {
    return mapped as BundledLanguage;
  }
  return "text" as BundledLanguage;
};

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

/** ClawsGO 风格源码视图：行号 + one-light 高亮，无多余外壳 */
const SourceCodeView: React.FC<{
  code: string;
  language: BundledLanguage;
  className?: string;
}> = ({ code, language, className }) => {
  const [html, setHtml] = useState("");

  useEffect(() => {
    let cancelled = false;
    highlightCode(code, language, true).then(([light]) => {
      if (!cancelled) {
        setHtml(light);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [code, language]);

  if (!html) {
    return <Loading className={LOADING_CLASS} />;
  }

  return (
    <div
      className={cn(
        "ws-source-view h-full min-h-0 w-full overflow-auto bg-white px-1 py-1 sm:px-2",
        className
      )}
    >
      <div
        className={cn(
          "source-code-highlight mx-auto max-w-[920px] text-[13px] leading-[1.7]",
          "[&>pre]:m-0 [&>pre]:bg-transparent! [&>pre]:p-0!",
          "[&>pre]:font-mono [&>pre]:text-[13px] [&>pre]:leading-[1.7]",
          "[&>pre]:whitespace-pre [&>pre]:overflow-x-auto",
          "[&_code]:font-mono [&_code]:text-[13px] [&_code]:bg-transparent!",
          // 行号：灰字、右对齐、不换行
          "[&_.line]:inline-block [&_.line]:w-full",
          "[&_span.min-w-10]:min-w-[2.5rem] [&_span.min-w-10]:mr-5",
          "[&_span.min-w-10]:text-right [&_span.min-w-10]:text-[#b0b0b5]",
          "[&_span.min-w-10]:text-[12px] [&_span.min-w-10]:leading-[1.7]"
        )}
        dangerouslySetInnerHTML={{ __html: html }}
      />
    </div>
  );
};

const FileRenderer: ReactorType.FC<FileRendererProps> = React.memo((props) => {
  const {
    fileUrl,
    fileName,
    downloadUrl,
    missingReason,
    className,
    forceSource = false,
  } = props;

  const ext = useMemo(() => getFileExtension(fileName), [fileName]);
  const language = useMemo(() => resolveLanguage(ext), [ext]);
  const isMarkdown = MARKDOWN_EXTS.has(ext);
  const isHtml = HTML_EXTS.has(ext) || ext === "html" || ext === "htm";
  const isImage = IMAGE_PREVIEW_EXTS.has(ext);
  const useIframe = isHtml && !forceSource;
  const isBinaryDownload = BINARY_DOWNLOAD_EXTS.has(ext) && !forceSource;
  const resolvedDownload = downloadUrl || fileUrl;

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
      // 图片 / HTML / 二进制均不按文本拉取
      ready: !useIframe && !isBinaryDownload && !isImage,
    }
  );

  if (isImage && !forceSource) {
    return (
      <ImageRenderer
        imageUrl={fileUrl}
        fileName={fileName}
        missingReason={missingReason}
        className={className}
      />
    );
  }

  if (isBinaryDownload) {
    const isMedia = ["mp3", "mp4", "mov", "webm", "wav", "ogg"].includes(ext);
    if (isMedia && fileUrl) {
      const isVideo = ["mp4", "mov", "webm"].includes(ext);
      return (
        <div className={cn("flex h-full items-center justify-center bg-white p-4", className)}>
          {isVideo ? (
            <video src={fileUrl} controls className="max-h-full max-w-full rounded-lg" />
          ) : (
            <audio src={fileUrl} controls className="w-full max-w-md" />
          )}
        </div>
      );
    }
    return (
      <DocumentFallback
        label={ext ? ext.toUpperCase() : "FILE"}
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

  // HTML：直接 iframe 预览，不做源码高亮
  if (useIframe) {
    return (
      <HTMLRenderer
        htmlUrl={fileUrl}
        downloadUrl={resolvedDownload}
        missingReason={missingReason}
        className={className}
      />
    );
  }

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

  if (isMarkdown) {
    return (
      <div className={cn("mx-auto max-w-[720px] bg-white px-6 py-6 sm:px-8", className)}>
        <MarkdownRenderer
          markDownContent={data || ""}
          normalizationScope="default"
        />
      </div>
    );
  }

  return (
    <SourceCodeView
      code={data || ""}
      language={language}
      className={className}
    />
  );
});

FileRenderer.displayName = "FileRenderer";

export default FileRenderer;
