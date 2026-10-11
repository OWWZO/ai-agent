import { jumpUrl, showMessage } from "@/utils";
import { Button } from "@/components/ui/button";
import { useBoolean } from "ahooks";
import classNames from "classnames";
import { Download, ExternalLink, MousePointerClick } from "lucide-react";
import { memo, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useHtmlElementPicker } from "@/hooks/useHtmlElementPicker";
import { cn } from "@/lib/utils";
import Loading from "./Loading";
import { Empty } from "antd";
import MarkdownRenderer from "./MarkdownRenderer";

interface HTMLRendererProps {
  htmlUrl?: string;
  downloadUrl?: string;
  missingReason?: string;
  showToolBar?: boolean;
  /** 内联 HTML 字符串（无文件 URL 时用 srcDoc 渲染） */
  htmlContent?: string;
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
    htmlContent,
    outputCode,
    isStreaming = false,
  } = props;

  const [loading, { setTrue: startLoading, setFalse: stopLoading }] = useBoolean(
    Boolean(htmlUrl || htmlContent)
  );
  const [error, setError] = useState<string | null>(null);
  const frameRef = useRef<HTMLIFrameElement | null>(null);

  const hasFrameContent = Boolean(htmlUrl || htmlContent);
  const canPickElement = Boolean(showToolBar) && hasFrameContent && !isStreaming;

  const {
    isPicking,
    error: pickerError,
    togglePicking,
  } = useHtmlElementPicker({ iframeRef: frameRef, enabled: canPickElement });

  useEffect(() => {
    if (pickerError) {
      showMessage()?.warning(pickerError);
    }
  }, [pickerError]);

  useLayoutEffect(() => {
    setError(null);
    if (htmlUrl || htmlContent) {
      startLoading();
      // srcDoc 同步可用，短延迟后关 loading
      if (htmlContent && !htmlUrl) {
        const t = window.setTimeout(() => stopLoading(), 50);
        return () => window.clearTimeout(t);
      }
    } else {
      stopLoading();
    }
  }, [htmlUrl, htmlContent, startLoading, stopLoading]);

  const headerActions = useMemo(() => {
    if (!showToolBar) return null;
    if (!htmlUrl && !htmlContent) return null;
    return (
      <div className="absolute right-3 top-3 z-20 flex items-center gap-1 rounded-full bg-white/90 p-0.5 shadow-sm ring-1 ring-black/[0.04]">
        <Button
          aria-label={isPicking ? "取消网页元素选择" : "选择网页元素加入聊天"}
          aria-pressed={isPicking}
          className={cn(
            "h-7 w-7 shrink-0 rounded-full text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]",
            isPicking && "bg-[var(--chat-surface-muted)] text-[var(--chat-text)]"
          )}
          onClick={() => void togglePicking()}
          size="icon-sm"
          title={isPicking ? "取消网页元素选择（Esc）" : "选择网页元素加入聊天"}
          type="button"
          variant="ghost"
        >
          <MousePointerClick className="size-3.5" />
        </Button>
        {htmlUrl ? (
          <Button
            aria-label="在新窗口打开"
            className="h-7 w-7 shrink-0 rounded-full text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
            onClick={() => jumpUrl(htmlUrl)}
            size="icon-sm"
            title="在新窗口打开"
            type="button"
            variant="ghost"
          >
            <ExternalLink className="size-3.5" />
          </Button>
        ) : null}
        {downloadUrl ? (
          <Button
            aria-label="下载"
            className="h-7 w-7 shrink-0 rounded-full text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
            onClick={() => jumpUrl(downloadUrl)}
            size="icon-sm"
            title="下载"
            type="button"
            variant="ghost"
          >
            <Download className="size-3.5" />
          </Button>
        ) : null}
      </div>
    );
  }, [showToolBar, htmlUrl, htmlContent, downloadUrl, isPicking, togglePicking]);

  if (!htmlUrl && !htmlContent && outputCode) {
    return (
      <MarkdownRenderer
        markDownContent={outputCode}
        isStreaming={isStreaming}
      />
    );
  }

  if (error) {
    return (
      <div
        className={classNames(
          "flex h-full min-h-[320px] items-center justify-center p-6 text-red-500",
          className
        )}
      >
        {error}
      </div>
    );
  }

  if (missingReason && !htmlUrl && !htmlContent) {
    return (
      <div
        className={classNames(
          "flex h-full min-h-[320px] items-center justify-center p-6 text-red-500",
          className
        )}
      >
        {missingReason}
      </div>
    );
  }

  if (htmlUrl || htmlContent) {
    return (
      <div
        className={classNames(
          "relative h-full min-h-[480px] w-full flex-1 bg-white",
          className
        )}
      >
        {headerActions}
        {loading ? (
          <Loading
            loading
            className="absolute inset-0 z-10 h-full w-full bg-white/70"
          />
        ) : null}
        <iframe
          ref={frameRef}
          className="absolute inset-0 h-full w-full border-0 bg-white"
          title="HTML preview"
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
          referrerPolicy="no-referrer-when-downgrade"
          {...(htmlUrl
            ? { src: htmlUrl }
            : { srcDoc: htmlContent || "" })}
          onLoad={stopLoading}
          onError={() => {
            setError("引用资源不存在或已失效");
            stopLoading();
          }}
        />
      </div>
    );
  }

  return (
    <div className={classNames(className, "relative min-h-[240px]")}>
      <Empty description="暂无内容" className="mt-32" />
    </div>
  );
});

HTMLRenderer.displayName = "HTMLRenderer";

export default HTMLRenderer;
