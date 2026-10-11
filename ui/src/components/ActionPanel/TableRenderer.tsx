import { memo, useEffect, useMemo, useRef, useState } from "react";
import { Alert } from "antd";
import { useRequest } from "ahooks";
import { useIsDarkTheme } from "@/hooks/useIsDarkTheme";
import { loadXlsx } from "@/lib/lazy/xlsx";
import { loadXlsxViewer, type XlsxViewerModule } from "@/lib/lazy/xlsxViewer";
import { installDocumentLinkSafety } from "@/lib/office/documentLinkSafety";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import { cn } from "@/lib/utils";
import Loading from "./Loading";
import DocumentFallback from "./DocumentFallback";
import PreviewErrorBoundary from "./PreviewErrorBoundary";
import XlsxSheetTabs from "./XlsxSheetTabs";

const ERROR_CLASS =
  "m-12 md:m-24 min-w-[260px] max-w-[calc(100%-24px)] md:max-w-[calc(100%-48px)] [&_.ant-alert-description]:break-words [&_.ant-alert-description]:whitespace-normal";

const LOADING_CLASS = "mr-32";

/** 走 xlsx 二进制解析的扩展名；其余（含 csv）先转成工作簿再交给同一个查看器。 */
const EXCEL_EXTENSIONS = new Set(["xlsx", "xlsm", "xlsb", "xls"]);

const resolveUnavailableReason = (error: Error) => {
  const message = error?.message || "";
  if (
    message.includes("Failed to fetch") ||
    message.includes("Network response was not ok") ||
    message.includes("网络错误") ||
    message.includes("NetworkError")
  ) {
    return "引用资源不存在或已失效";
  }
  return message || "引用资源不存在或已失效";
};

interface TableRendererProps {
  fileUrl: string;
  mode?: "csv" | "excel";
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
  className?: string;
}

/**
 * 表格预览（xlsx / xlsm / xlsb / xls / csv）。
 *
 * 引擎与 ZCode `previewPaneOfficeXlsxContent.tsx` 对齐：`@extend-ai/react-xlsx`
 * 的 WASM `XlsxViewer`（只读、关闭默认工具栏、只保留 sheet tabs、主题跟随）。
 * CSV 先经 SheetJS 转成工作簿字节，复用同一个查看器，避免维护第二套表格实现。
 */
const TableRenderer: ReactorType.FC<TableRendererProps> = memo((props) => {
  const { fileUrl, mode, fileName, downloadUrl, missingReason, className } = props;
  const isDark = useIsDarkTheme();
  const viewerRef = useRef<HTMLDivElement | null>(null);

  const ext = (fileName || fileUrl).split(".").pop()?.toLowerCase();
  const fileType: "csv" | "excel" = mode || (ext && EXCEL_EXTENSIONS.has(ext) ? "excel" : "csv");

  const resolvedUrl = useMemo(
    () => normalizeFileUrlForBrowser(fileUrl || ""),
    [fileUrl]
  );

  const {
    data: buffer,
    loading,
    error,
  } = useRequest(
    async () => {
      if (missingReason) {
        throw new Error(missingReason);
      }
      if (!resolvedUrl) {
        throw new Error("引用资源不存在或已失效");
      }
      const res = await fetch(resolvedUrl);
      if (!res.ok) {
        throw new Error("网络错误");
      }

      if (fileType === "excel") {
        return res.arrayBuffer();
      }

      // CSV → 工作簿字节：SheetJS 只做一次格式转换，渲染仍交给 WASM 查看器。
      const text = await res.text();
      const xlsx = await loadXlsx();
      const workbook = xlsx.read(text, { type: "string", raw: false, FS: "," });
      return xlsx.write(workbook, { type: "array", bookType: "xlsx" }) as ArrayBuffer;
    },
    { refreshDeps: [resolvedUrl, missingReason, fileType] }
  );

  const [viewerModule, setViewerModule] = useState<XlsxViewerModule | null>(null);
  const [moduleError, setModuleError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    loadXlsxViewer()
      .then((mod) => {
        if (!cancelled) {
          setViewerModule(mod);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          console.error("[TableRenderer] 表格预览引擎加载失败", err);
          setModuleError(err);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 表格内的超链接来自不受信任的 OOXML，挂载后统一净化并阻断内部导航。
  useEffect(() => {
    const root = viewerRef.current;
    return root ? installDocumentLinkSafety(root) : undefined;
  }, [viewerModule, buffer]);

  const displayError = error ?? moduleError;
  if (displayError) {
    return (
      <Alert
        type="error"
        message="内容不可读取"
        description={resolveUnavailableReason(displayError as Error)}
        showIcon
        className={ERROR_CLASS}
      />
    );
  }

  if (loading || !buffer || !viewerModule) {
    return (
      <div className={cn("flex h-full min-h-[200px] items-center justify-center", className)}>
        <Loading className={LOADING_CLASS} />
      </div>
    );
  }

  const XlsxViewer = viewerModule.XlsxViewer;

  const errorFallback = (
    <DocumentFallback
      label="XLSX"
      title="表格不可预览"
      description="表格渲染失败，请下载原件后用本地 Excel / WPS 打开。"
      fileName={fileName}
      downloadUrl={downloadUrl || fileUrl}
      className={className}
      type="error"
    />
  );

  return (
    <div
      ref={viewerRef}
      className={cn("h-full min-h-[320px] w-full min-w-0 overflow-hidden", className)}
      data-office-preview-kind="excel"
    >
      <PreviewErrorBoundary
        label="XLSX"
        fallback={errorFallback}
        resetKey={`${fileName ?? ""}:${buffer.byteLength}`}
      >
        <XlsxViewer
          file={buffer}
          fileName={fileName || "workbook.xlsx"}
          height="100%"
          isDark={isDark}
          readOnly
          // 关掉库内置的解析 worker：它用 `new Worker(new URL("./xlsx-worker.js", import.meta.url))`
          // 建 worker，dev 下依赖预打包会把 `import.meta.url` 指到 `/node_modules/.vite/deps/`，
          // worker 被解析成不存在的 `/node_modules/.vite/deps/xlsx-worker.js`（404）→
          // worker error → 库统一抛 `Worker request failed.`，表格直接不可读。
          // 关掉后解析回到主线程（走同一个 WASM 模块），行为在 dev / build 下一致。
          useWorker={false}
          rounded={false}
          showDefaultToolbar={false}
          toolbar={(controller) => <XlsxSheetTabs controller={controller} label="工作表" />}
          loadingState={
            <div
              aria-busy="true"
              className="flex h-full min-h-[200px] w-full items-center justify-center bg-[var(--chat-surface)]"
            >
              <Loading className={LOADING_CLASS} />
            </div>
          }
          errorState={(err: Error) => (
            <Alert
              type="error"
              message="内容不可读取"
              description={resolveUnavailableReason(err)}
              showIcon
              className={ERROR_CLASS}
            />
          )}
          fileTooLargeState={() => (
            <Alert
              type="warning"
              message="文件过大"
              description="该表格超出在线预览的大小上限，请下载后用本地 Excel / WPS 打开。"
              showIcon
              className={ERROR_CLASS}
            />
          )}
        />
      </PreviewErrorBoundary>
    </div>
  );
});

TableRenderer.displayName = "TableRenderer";

export default TableRenderer;
