import React, { useMemo } from "react";
import { useRequest } from "ahooks";
import { Button } from "antd";
import { Download } from "lucide-react";
import mammoth from "mammoth";
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

  const { data: html, loading, error } = useRequest(
    async () => {
      if (legacyOnly) {
        return null;
      }
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
      const result = await mammoth.convertToHtml({ arrayBuffer: buffer });
      return result.value || "<p>（文档无正文内容）</p>";
    },
    {
      refreshDeps: [resolvedUrl, missingReason, legacyOnly],
      ready: !legacyOnly,
    }
  );

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

  if (loading) {
    return (
      <ViewerPanelShell
        label="DOCX"
        subtitle={fileName || "Word 预览"}
        className={className}
      >
        <Loading className={LOADING_CLASS} />
      </ViewerPanelShell>
    );
  }

  if (error || !html) {
    return (
      <DocumentFallback
        label="DOCX"
        title="Word 不可预览"
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
      label="DOCX"
      subtitle={fileName || "Word 预览"}
      className={className}
      headerRight={
        resolvedDownload ? (
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
      bodyClassName="max-h-[min(72vh,960px)] overflow-auto bg-white"
    >
      <p className="mb-3 text-[11px] text-[var(--chat-text-soft)]">
        近似预览（复杂排版可能与原件有差异），需要精确格式请下载原件。
      </p>
      <div
        className="prose prose-sm max-w-none chat-markdown word-preview-body"
        // mammoth 输出为文档自身 HTML；仅用于只读预览
        dangerouslySetInnerHTML={{ __html: html }}
      />
    </ViewerPanelShell>
  );
});

WordRenderer.displayName = "WordRenderer";

export default WordRenderer;
