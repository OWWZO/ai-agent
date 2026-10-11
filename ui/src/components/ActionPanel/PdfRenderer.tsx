import React, { Suspense, lazy, useEffect, useMemo, useState } from "react";
import { useRequest } from "ahooks";
import { downloadFile } from "@/utils";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import Loading from "./Loading";
import DocumentFallback from "./DocumentFallback";
import { cn } from "@/lib/utils";

// react-pdf（含 pdf.js 与 worker）体积较大，懒加载让它只在首次打开 PDF 预览时进入 bundle，
// 不拖慢没有用到 PDF 的会话的启动（对齐 ZCode previewPanePdfContent 的做法）。
const PdfViewer = lazy(() => import("./PdfViewer"));

const LOADING_CLASS = "mr-32";

interface PdfRendererProps {
  fileUrl: string;
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
  hideChrome?: boolean;
  className?: string;
}

const resolveFetchError = (error: unknown) => {
  const message = error instanceof Error ? error.message : String(error || "");
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
 * PDF 预览适配层：负责取数（整档 ArrayBuffer）与加载 / 失败态。
 * 阅读器是 <PdfViewer>（连续滚动 + 左侧缩略图栏 + 顶部工具条），
 * 文件名 / 下载 / 打印都归它的工具条管，这里不再重复渲染头部。
 */
const PdfRenderer: React.FC<PdfRendererProps> = React.memo((props) => {
  const {
    fileUrl,
    fileName,
    downloadUrl,
    missingReason,
    hideChrome = false,
    className,
  } = props;

  const resolvedUrl = useMemo(
    () => normalizeFileUrlForBrowser(fileUrl || ""),
    [fileUrl]
  );
  const resolvedDownload = useMemo(
    () =>
      normalizeFileUrlForBrowser(downloadUrl || fileUrl || "") || resolvedUrl,
    [downloadUrl, fileUrl, resolvedUrl]
  );

  const displayName = fileName || "document.pdf";

  const { data: buffer, loading, error } = useRequest(
    async () => {
      // URL / 缺失原因变化会重新获取文档；先读完整 buffer 再交给 pdf.js，
      // 避免把远端响应流的生命周期泄漏到阅读器组件。
      if (missingReason) throw new Error(missingReason);
      if (!resolvedUrl) throw new Error("引用资源不存在或已失效");
      const response = await fetch(resolvedUrl);
      if (!response.ok) throw new Error("Network response was not ok");
      return await response.arrayBuffer();
    },
    { refreshDeps: [resolvedUrl, missingReason] }
  );

  // pdf.js 会把数据 transfer 给 worker；这里按 buffer 缓存视图，
  // 既保证 source 引用稳定（不触发文档重载），又由 PdfViewer 内部复制后再传入。
  const [source, setSource] = useState<Uint8Array | null>(null);
  useEffect(() => {
    setSource(buffer ? new Uint8Array(buffer) : null);
  }, [buffer]);

  const shellClass = cn(
    "relative flex h-full w-full min-h-0 flex-col overflow-hidden bg-background",
    !hideChrome && "rounded-xl border border-border",
    className
  );

  if (loading) {
    return (
      <div className={shellClass}>
        <Loading className={LOADING_CLASS} />
      </div>
    );
  }

  if (error || !source) {
    return (
      <DocumentFallback
        label="PDF"
        title="PDF 不可预览"
        description={resolveFetchError(error)}
        fileName={fileName}
        downloadUrl={resolvedDownload}
        className={className}
        type="error"
      />
    );
  }

  return (
    <div className={shellClass}>
      {/* 这个 Suspense 承载 PdfViewer 的动态 import（react-pdf 体积大，按需加载）；
          文档自身的加载态由 PdfViewer 内部的 <Document loading> 承载。 */}
      <Suspense
        fallback={
          <div className="flex min-h-0 flex-1 items-center justify-center text-ui-base text-foreground-subtle">
            正在加载 PDF…
          </div>
        }
      >
        <PdfViewer
          source={source}
          fileUrl={resolvedUrl}
          // 面板自己已经有文件名 / 下载入口时（工作区文件 tab 的 embedded 形态）不再重复。
          fileName={hideChrome ? undefined : displayName}
          onDownload={
            hideChrome || !resolvedDownload
              ? undefined
              : () => downloadFile(resolvedDownload, fileName)
          }
          className="min-h-0 flex-1"
        />
      </Suspense>
    </div>
  );
});

PdfRenderer.displayName = "PdfRenderer";

export default PdfRenderer;
