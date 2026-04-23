import { jumpUrl } from "@/utils";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import { Button } from "@/components/ui/button";
import { useBoolean } from "ahooks";
import classNames from "classnames";
import { Download, ExternalLink } from "lucide-react";
import { memo, useLayoutEffect, useMemo, useState } from "react";
import Loading from "./Loading";
import { Empty } from "antd";
import MarkdownRenderer from "./MarkdownRenderer";

interface HTMLRendererProps {
  htmlUrl?: string;
  downloadUrl?: string;
  missingReason?: string;
  showToolBar?: boolean;
  outputCode?: string;
  isStreaming?: boolean;
  className?: string;
}

const HTMLRenderer: ReactorType.FC<HTMLRendererProps> = memo((props) => {
  const {
    htmlUrl,
    className,
    downloadUrl,
    missingReason,
    showToolBar,
    outputCode,
    isStreaming = false,
  } = props;

  const [loading, { setTrue: startLoading, setFalse: stopLoading }] = useBoolean(false);
  const [error, setError] = useState<string | null>(null);

  useLayoutEffect(() => {
    if (htmlUrl) {
      startLoading();
    }
  }, [htmlUrl, startLoading]);

  const headerActions = useMemo(() => {
    if (!showToolBar || !htmlUrl) return null;
    return (
      <>
        <Button
          aria-label="在新窗口打开"
          className="h-7 w-7 shrink-0 rounded-md bg-[var(--chat-surface)] text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
          onClick={() => jumpUrl(htmlUrl)}
          size="icon-sm"
          title="在新窗口打开"
          type="button"
          variant="ghost"
        >
          <ExternalLink className="size-3.5" />
        </Button>
        {downloadUrl ? (
          <Button
            aria-label="下载"
            className="h-7 w-7 shrink-0 rounded-md bg-[var(--chat-surface)] text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
            onClick={() => jumpUrl(downloadUrl)}
            size="icon-sm"
            title="下载"
            type="button"
            variant="ghost"
          >
            <Download className="size-3.5" />
          </Button>
        ) : null}
      </>
    );
  }, [showToolBar, htmlUrl, downloadUrl]);

  const content = useMemo(() => {
    if (error) {
      return <div className="text-red-500">{error}</div>;
    }
    if (missingReason && !htmlUrl) {
      return <div className="text-red-500">{missingReason}</div>;
    }
    if (htmlUrl) {
      return (
        <iframe
          className="block h-[min(60vh,520px)] w-full rounded-lg bg-[var(--chat-surface)]"
          src={htmlUrl}
          title="HTML preview"
          onLoad={stopLoading}
          onError={() => {
            setError("引用资源不存在或已失效");
            stopLoading();
          }}
        />
      );
    }
    return <Empty description="暂无内容" className="mt-32" />;
  }, [error, htmlUrl, missingReason, stopLoading]);

  if (!htmlUrl && outputCode) {
    return (
      <MarkdownRenderer
        markDownContent={outputCode}
        isStreaming={isStreaming}
        normalizationScope="default"
      />
    );
  }

  if (htmlUrl) {
    return (
      <ViewerPanelShell
        bodyClassName="p-2 sm:p-3"
        className={classNames(className, "relative flex min-h-0 flex-1 flex-col")}
        headerRight={headerActions}
        label="HTML"
        subtitle="Preview"
      >
        <div className="relative min-h-0 flex-1 overflow-hidden rounded-lg">
          <Loading loading={loading} className="absolute left-0 top-0 z-10 h-full w-full" />
          {content}
        </div>
      </ViewerPanelShell>
    );
  }

  return (
    <div className={classNames(className, "relative")}>
      {content}
    </div>
  );
});

HTMLRenderer.displayName = "HTMLRenderer";

export default HTMLRenderer;
