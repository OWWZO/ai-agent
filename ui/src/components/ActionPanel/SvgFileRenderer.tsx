import React, { useMemo, useState } from "react";
import { useRequest } from "ahooks";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { CircleAlert, Download } from "lucide-react";
import { downloadFile } from "@/utils";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import { cn } from "@/lib/utils";
import Loading from "./Loading";
import SourceCodeView from "./SourceCodeView";
import PreviewModeToggle, { type PreviewViewMode } from "./PreviewModeToggle";

const LOADING_CLASS = "mr-32";
const ERROR_CLASS =
  "m-6 min-w-[260px] max-w-[calc(100%-48px)] [&_[data-slot=alert-description]]:break-words [&_[data-slot=alert-description]]:whitespace-normal";

const CHECKERBOARD_STYLE: React.CSSProperties = {
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

interface SvgFileRendererProps {
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
 * SVG 预览：图形 / 源码双模式。
 *
 * SVG 既能当图形看、也需要能当代码读（图标素材改色、检查导出内容都常用），
 * 所以不能像位图那样只给 `<img>`。图形模式用 data URL 内联，避免同源策略
 * 阻止外部 SVG 的字体 / 内嵌资源；源码模式复用 `SourceCodeView` 的 xml 高亮。
 */
const SvgFileRenderer: ReactorType.FC<SvgFileRendererProps> = React.memo((props) => {
  const { fileUrl, fileName, downloadUrl, missingReason, hideChrome = false, className } = props;

  const [mode, setMode] = useState<PreviewViewMode>("preview");

  const resolvedUrl = useMemo(
    () => normalizeFileUrlForBrowser(fileUrl || ""),
    [fileUrl]
  );
  const resolvedDownload = useMemo(
    () => normalizeFileUrlForBrowser(downloadUrl || fileUrl || "") || resolvedUrl,
    [downloadUrl, fileUrl, resolvedUrl]
  );

  const { data: content, loading, error } = useRequest(
    async () => {
      if (missingReason) throw new Error(missingReason);
      if (!resolvedUrl) throw new Error("引用资源不存在或已失效");
      const response = await fetch(resolvedUrl);
      if (!response.ok) throw new Error("Network response was not ok");
      return response.text();
    },
    { refreshDeps: [resolvedUrl, missingReason] }
  );

  const graphicSource = useMemo(() => {
    if (!content) return "";
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(content)}`;
  }, [content]);

  const displayError = error ? resolveFetchError(error) : null;

  return (
    <ViewerPanelShell
      label="SVG"
      subtitle={fileName || "SVG 预览"}
      className={className}
      hideHeader={hideChrome}
      headerRight={
        <>
          <PreviewModeToggle mode={mode} onChange={setMode} />
          {resolvedDownload ? (
            <Button variant="ghost" size="sm" onClick={() => downloadFile(resolvedDownload, fileName)}>
              <Download className="h-3.5 w-3.5" />
              下载
            </Button>
          ) : null}
        </>
      }
      bodyClassName="min-h-0 flex-1 p-0"
    >
      {loading ? (
        <div className="flex h-full min-h-[240px] items-center justify-center">
          <Loading className={LOADING_CLASS} />
        </div>
      ) : displayError ? (
        <div className="p-4">
          <Alert variant="destructive" className={ERROR_CLASS}>
            <CircleAlert />
            <AlertTitle>SVG 不可预览</AlertTitle>
            <AlertDescription>{displayError}</AlertDescription>
          </Alert>
        </div>
      ) : mode === "source" ? (
        <SourceCodeView code={content || ""} language="xml" sourcePath={fileName} />
      ) : (
        <div
          className={cn("flex h-full min-h-[240px] items-center justify-center overflow-auto p-6")}
          style={CHECKERBOARD_STYLE}
        >
          <img
            src={graphicSource}
            alt={fileName || "SVG 预览"}
            className="h-auto max-h-full w-auto max-w-full object-contain"
          />
        </div>
      )}
    </ViewerPanelShell>
  );
});

SvgFileRenderer.displayName = "SvgFileRenderer";

export default SvgFileRenderer;
