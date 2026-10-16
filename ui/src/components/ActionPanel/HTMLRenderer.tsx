import { showMessage } from "@/utils";
import { Button } from "@/components/ui/button";
import { useBoolean } from "ahooks";
import classNames from "classnames";
import { MousePointerClick } from "lucide-react";
import { memo, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useHtmlElementPicker } from "@/hooks/useHtmlElementPicker";
import { cn } from "@/lib/utils";
import { useFilePreviewHeaderSlot } from "./filePreviewHeaderSlot";
import Loading from "./Loading";
import { Empty } from "antd";
import MarkdownRenderer from "./MarkdownRenderer";

interface HTMLRendererProps {
  htmlUrl?: string;
  /**
   * 保留在 props 契约里（ActionPanel 统一注入，其它 renderer 用），
   * 但 HTML 预览**不再**自己渲染下载 / 外链按钮 —— 见 `headerActions` 的注释。
   */
  downloadUrl?: string;
  missingReason?: string;
  showToolBar?: boolean;
  /** 内联 HTML 字符串（无文件 URL 时用 srcDoc 渲染） */
  htmlContent?: string;
  outputCode?: string;
  isStreaming?: boolean;
  className?: string;
}

/**
 * 渲染进**工作区文件头部**那一行时用的图标按钮样式 —— 与 `ActionView` 的
 * `iconBtnClass` 保持一致（同一排按钮不能长得不一样）。
 * 不复用那个常量：`ActionView` 在更上层，互相 import 会成环。
 */
const HEADER_ICON_BTN_CLASS =
  "reactor-action-icon flex h-7 w-7 items-center justify-center rounded-lg text-foreground-subtle transition-colors hover:bg-surface-hover hover:text-foreground";

/** 没有头部插槽时（对话区文件卡）浮在预览右上角的胶囊按钮样式 */
const PILL_ICON_BTN_CLASS =
  "h-7 w-7 shrink-0 rounded-full text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]";

const HTMLRenderer: ReactorType.FC<HTMLRendererProps> = memo((props) => {
  const {
    htmlUrl,
    className,
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
  } = useHtmlElementPicker({
    iframeRef: frameRef,
    enabled: canPickElement
  });

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

  /**
   * HTML 预览右上角的浮动按钮 —— **只留「选择网页元素」**。
   *
   * 这里原本还有「在新窗口打开」「下载」，但它们和工作区头部**完全重复**：
   * 头部第一行的 `⧉`（在新窗口打开）打的也是这个文件的 previewUrl，第二行的 `⬇`
   * 打的也是同一个 downloadUrl。同一个文件在同一个面板里出现两组一模一样的按钮，
   * 用户看不出差别只会觉得冗余；头部是常驻 chrome、对所有文件类型都成立，所以留头部。
   *
   * 「选择网页元素」是 HTML 预览独有的能力（要挂 iframe 内的 picker），只能留在这里。
   */
  /**
   * 「选择网页元素」按钮 —— HTML 预览独有的能力（picker 要挂到 iframe 内部），
   * 所以只能由本组件提供。
   *
   * 它**渲染到哪里**由 `FilePreviewHeaderSlotContext` 决定：
   * - 在工作区文件预览里（头部提供了插槽）→ 用 portal 渲染进头部那一行，和
   *   「预览 / 源码 / 复制链接 / 刷新 / 下载」同列；浮在预览右上角既显重复又挡内容。
   * - 对话区里的文件卡（没有插槽）→ 回落到右上角的浮层胶囊。
   *
   * 注意这里**不再**有「在新窗口打开」「下载」：它们和工作区头部完全重复
   * （头部第一行的 `⧉` 打同一个 previewUrl，第二行的 `⬇` 打同一个 downloadUrl）。
   */
  const headerSlot = useFilePreviewHeaderSlot();

  const pickerButton = useMemo(() => {
    if (!showToolBar) return null;
    if (!htmlUrl && !htmlContent) return null;
    const inHeader = Boolean(headerSlot);
    return (
      <Button
        aria-label={isPicking ? "取消网页元素选择" : "选择网页元素加入聊天"}
        aria-pressed={isPicking}
        className={cn(
          inHeader ? HEADER_ICON_BTN_CLASS : PILL_ICON_BTN_CLASS,
          isPicking && "bg-selected text-foreground"
        )}
        onClick={() => void togglePicking()}
        size="icon-sm"
        title={isPicking ? "取消网页元素选择（Esc）" : "选择网页元素加入聊天"}
        type="button"
        variant="ghost"
      >
        <MousePointerClick className="size-3.5" />
      </Button>
    );
  }, [headerSlot, showToolBar, htmlUrl, htmlContent, isPicking, togglePicking]);

  // 头部插槽就位 → 按钮渲染进那一行（节点未就绪时先不渲染，别先飘一下再跳）。
  const headerActions = useMemo(() => {
    if (!headerSlot) {
      return pickerButton ? (
        <div className="absolute right-3 top-3 z-20 flex items-center gap-1 rounded-full bg-white/90 p-0.5 shadow-sm ring-1 ring-black/[0.04]">
          {pickerButton}
        </div>
      ) : null;
    }
    return headerSlot.node && pickerButton
      ? createPortal(pickerButton, headerSlot.node)
      : null;
  }, [headerSlot, pickerButton]);

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
