import React, { useEffect, useMemo, useRef, useState } from "react";
import { useRequest } from "ahooks";
import { Button } from "antd";
import { Download } from "lucide-react";
import { renderAsync } from "docx-preview";
import { downloadFile } from "@/utils";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import Loading from "./Loading";
import DocumentFallback from "./DocumentFallback";

const LOADING_CLASS = "mr-32";

interface WordRendererProps {
  fileUrl: string;
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
  /** true = 老 .doc，不解析 */
  legacyOnly?: boolean;
  /** 工作区内嵌时隐藏自带 header，避免与外层重复 */
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

const WordRenderer: ReactorType.FC<WordRendererProps> = React.memo((props) => {
  const {
    fileUrl,
    fileName,
    downloadUrl,
    missingReason,
    legacyOnly,
    hideChrome = false,
    className,
  } = props;

  const bodyRef = useRef<HTMLDivElement>(null);
  const [renderError, setRenderError] = useState<unknown>(null);
  const [renderReady, setRenderReady] = useState(false);

  const resolvedUrl = useMemo(
    () => normalizeFileUrlForBrowser(fileUrl || ""),
    [fileUrl]
  );
  const resolvedDownload = useMemo(
    () =>
      normalizeFileUrlForBrowser(downloadUrl || fileUrl || "") || resolvedUrl,
    [downloadUrl, fileUrl, resolvedUrl]
  );

  const { data: buffer, error } = useRequest(
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
      return response.arrayBuffer();
    },
    {
      refreshDeps: [resolvedUrl, missingReason],
      ready: !legacyOnly,
    }
  );

  useEffect(() => {
    if (legacyOnly || !buffer) {
      setRenderReady(false);
      return;
    }

    let cancelled = false;
    setRenderError(null);
    setRenderReady(false);

    const run = async () => {
      await Promise.resolve();
      const container = bodyRef.current;
      if (cancelled || !container) {
        return;
      }
      container.innerHTML = "";
      try {
        await renderAsync(buffer, container, undefined, {
          className: "docx-preview",
          inWrapper: true,
          breakPages: true,
          renderHeaders: true,
          renderFooters: true,
          renderFootnotes: true,
          renderEndnotes: true,
          ignoreWidth: false,
          ignoreHeight: false,
          useBase64URL: true,
        });
        if (!cancelled) {
          setRenderReady(true);
        }
      } catch (err) {
        if (!cancelled) {
          setRenderError(err);
          setRenderReady(false);
        }
      }
    };

    void run();

    return () => {
      cancelled = true;
      if (bodyRef.current) {
        bodyRef.current.innerHTML = "";
      }
    };
  }, [buffer, legacyOnly]);

  if (legacyOnly) {
    return (
      <DocumentFallback
        label="DOC"
        title="暂不支持预览旧版 Word（.doc）"
        description="请下载原件后用本地 Word / WPS 打开。建议保存为 .docx 以便在线预览。"
        fileName={fileName}
        downloadUrl={resolvedDownload}
        className={className}
        type="info"
      />
    );
  }

  const displayError = error ?? renderError;
  if (displayError) {
    return (
      <DocumentFallback
        label="DOCX"
        title="Word 不可预览"
        description={resolveFetchError(displayError)}
        fileName={fileName}
        downloadUrl={resolvedDownload}
        className={className}
        type="error"
      />
    );
  }

  return (
    <ViewerPanelShell
      label="DOCX"
      subtitle={fileName || "Word 预览"}
      className={className}
      hideHeader={hideChrome}
      headerRight={
        !hideChrome && resolvedDownload && renderReady ? (
          <Button
            type="text"
            size="small"
            icon={<Download className="h-3.5 w-3.5" />}
            onClick={() => downloadFile(resolvedDownload, fileName)}
          >
            下载
          </Button>
        ) : null
      }
      bodyClassName={
        hideChrome
          ? "max-h-full min-h-0 flex-1 overflow-auto bg-[#f3f4f6] p-3 sm:p-4"
          : "max-h-[min(72vh,960px)] overflow-auto bg-[#f3f4f6]"
      }
    >
      {!renderReady ? <Loading className={LOADING_CLASS} /> : null}
      {renderReady ? (
        <p className="mb-3 text-[11px] text-[var(--chat-text-soft)]">
          高保真预览（复杂排版仍可能与原件有差异），需要精确格式请下载原件。
        </p>
      ) : null}
      <div ref={bodyRef} className="word-preview-body" hidden={!renderReady} />
    </ViewerPanelShell>
  );
});

WordRenderer.displayName = "WordRenderer";

export default WordRenderer;
