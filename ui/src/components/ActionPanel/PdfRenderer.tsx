import React, { useEffect, useMemo, useState } from "react";
import { useRequest } from "ahooks";
import { Button, Space } from "antd";
import {
  ChevronLeft,
  ChevronRight,
  Download,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import * as pdfjs from "pdfjs-dist";
import pdfWorkerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";
import { downloadFile } from "@/utils";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import Loading from "./Loading";
import DocumentFallback from "./DocumentFallback";

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

const LOADING_CLASS = "mr-32";

interface PdfRendererProps {
  fileUrl: string;
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
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

const PdfRenderer: ReactorType.FC<PdfRendererProps> = React.memo((props) => {
  const { fileUrl, fileName, downloadUrl, missingReason, className } = props;
  const [page, setPage] = useState(1);
  const [scale, setScale] = useState(1.15);
  const [pageCount, setPageCount] = useState(0);
  const [canvasEl, setCanvasEl] = useState<HTMLCanvasElement | null>(null);

  const resolvedUrl = useMemo(
    () => normalizeFileUrlForBrowser(fileUrl || ""),
    [fileUrl]
  );
  const resolvedDownload = useMemo(
    () =>
      normalizeFileUrlForBrowser(downloadUrl || fileUrl || "") || resolvedUrl,
    [downloadUrl, fileUrl, resolvedUrl]
  );

  const { data: pdfDoc, loading, error } = useRequest(
    async () => {
      if (missingReason) {
        throw new Error(missingReason);
      }
      if (!resolvedUrl) {
        throw new Error("引用资源不存在或已失效");
      }
      const response = await fetch(resolvedUrl);
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      const buffer = await response.arrayBuffer();
      const loadingTask = pdfjs.getDocument({ data: buffer });
      return loadingTask.promise;
    },
    {
      refreshDeps: [resolvedUrl, missingReason],
      onSuccess: (doc) => {
        setPageCount(doc.numPages);
        setPage(1);
      },
    }
  );

  useEffect(() => {
    let cancelled = false;

    const renderPage = async () => {
      if (!pdfDoc || !canvasEl) {
        return;
      }
      const safePage = Math.min(Math.max(page, 1), pdfDoc.numPages || 1);
      const pdfPage = await pdfDoc.getPage(safePage);
      if (cancelled) {
        return;
      }
      const viewport = pdfPage.getViewport({ scale });
      const context = canvasEl.getContext("2d");
      if (!context) {
        return;
      }
      canvasEl.height = viewport.height;
      canvasEl.width = viewport.width;
      await pdfPage.render({
        canvasContext: context,
        viewport,
        canvas: canvasEl,
      }).promise;
    };

    renderPage().catch(() => {
      // 渲染错误由外层 loading/error 兜底；单页失败不炸整页
    });

    return () => {
      cancelled = true;
    };
  }, [pdfDoc, canvasEl, page, scale]);

  if (loading) {
    return (
      <ViewerPanelShell
        label="PDF"
        subtitle={fileName || "PDF 预览"}
        className={className}
      >
        <Loading className={LOADING_CLASS} />
      </ViewerPanelShell>
    );
  }

  if (error || !pdfDoc) {
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
    <ViewerPanelShell
      label="PDF"
      subtitle={fileName || "PDF 预览"}
      className={className}
      bodyClassName="flex flex-col items-center gap-3 overflow-auto p-3 sm:p-4"
      headerRight={
        <Space size={4} wrap>
          <Button
            type="text"
            size="small"
            icon={<ZoomOut className="h-3.5 w-3.5" />}
            disabled={scale <= 0.6}
            onClick={() => setScale((s) => Math.max(0.6, Number((s - 0.15).toFixed(2))))}
          />
          <Button
            type="text"
            size="small"
            icon={<ZoomIn className="h-3.5 w-3.5" />}
            disabled={scale >= 2.4}
            onClick={() => setScale((s) => Math.min(2.4, Number((s + 0.15).toFixed(2))))}
          />
          <Button
            type="text"
            size="small"
            icon={<ChevronLeft className="h-3.5 w-3.5" />}
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          />
          <span className="min-w-[4.5rem] text-center text-[12px] text-[var(--chat-text-soft)]">
            {page} / {pageCount || "?"}
          </span>
          <Button
            type="text"
            size="small"
            icon={<ChevronRight className="h-3.5 w-3.5" />}
            disabled={page >= pageCount}
            onClick={() => setPage((p) => Math.min(pageCount, p + 1))}
          />
          {resolvedDownload ? (
            <Button
              type="text"
              size="small"
              icon={<Download className="h-3.5 w-3.5" />}
              onClick={() => downloadFile(resolvedDownload, fileName)}
            >
              下载
            </Button>
          ) : null}
        </Space>
      }
    >
      <canvas
        ref={setCanvasEl}
        className="max-w-full rounded-md bg-white shadow-[var(--shadow-xs)]"
      />
    </ViewerPanelShell>
  );
});

PdfRenderer.displayName = "PdfRenderer";

export default PdfRenderer;
