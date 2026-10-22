import React, { useMemo, useRef, useState } from "react";
import { useRequest } from "ahooks";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { CircleAlert, Download } from "lucide-react";
import { downloadFile } from "@/utils";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import MarkdownRenderer from "./MarkdownRenderer";
import MarkdownSelectionMenu from "./MarkdownSelectionMenu";
import SourceCodeView from "./SourceCodeView";
import PreviewModeToggle, { type PreviewViewMode } from "./PreviewModeToggle";
import Loading from "./Loading";

const LOADING_CLASS = "mr-32";
const ERROR_CLASS =
  "m-6 min-w-[260px] max-w-[calc(100%-48px)] [&_[data-slot=alert-description]]:break-words [&_[data-slot=alert-description]]:whitespace-normal";

interface MarkdownFileRendererProps {
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
 * Markdown 文件预览：渲染 / 源码双模式。
 *
 * - 渲染模式复用 `MarkdownRenderer`（GFM、mermaid、代码高亮、按需数学公式），
 *   并挂上「选中正文 → 加入聊天」（`MarkdownSelectionMenu`）；
 * - 源码模式复用 `SourceCodeView`（markdown 高亮 + 行号 + 行选择评论）。
 *
 * 与 `MarkdownRenderer` 的分工：后者只负责「把 markdown 渲染成 HTML」，
 * 流式聊天与文件预览共用；本组件负责「文件级」的取数、双模式切换与外壳。
 */
const MarkdownFileRenderer: CuteType.FC<MarkdownFileRendererProps> = React.memo((props) => {
  const { fileUrl, fileName, downloadUrl, missingReason, hideChrome = false, className } = props;

  const [mode, setMode] = useState<PreviewViewMode>("preview");
  const markdownRootRef = useRef<HTMLDivElement | null>(null);

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

  const displayError = error ? resolveFetchError(error) : null;

  return (
    <ViewerPanelShell
      label="MD"
      subtitle={fileName || "Markdown 预览"}
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
            <AlertTitle>Markdown 不可读取</AlertTitle>
            <AlertDescription>{displayError}</AlertDescription>
          </Alert>
        </div>
      ) : mode === "source" ? (
        <SourceCodeView code={content || ""} language="markdown" sourcePath={fileName} />
      ) : (
        <div ref={markdownRootRef} className="h-full min-h-0 overflow-auto bg-white">
          <div className="mx-auto max-w-[720px] px-6 py-6 sm:px-8">
            <MarkdownRenderer markDownContent={content || ""} />
          </div>
          <MarkdownSelectionMenu rootRef={markdownRootRef} sourcePath={fileName} />
        </div>
      )}
    </ViewerPanelShell>
  );
});

MarkdownFileRenderer.displayName = "MarkdownFileRenderer";

export default MarkdownFileRenderer;
