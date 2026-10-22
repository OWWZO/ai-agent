import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useRequest } from "ahooks";
import { Button } from "@/components/ui/button";
import { Download } from "lucide-react";
import type { Options } from "docx-preview";
import { downloadFile } from "@/utils";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import { ViewerPanelShell } from "@/components/ui/viewer-panel-shell";
import { loadDocxPreview } from "@/lib/lazy/docxPreview";
import LegacyDocCanvas from "./LegacyDocCanvas";
import {
  calculateDocxPreviewFit,
  isSameDocxPreviewFit,
  type DocxPreviewFit,
} from "@/lib/office/previewFit";
import { installDocumentLinkSafety } from "@/lib/office/documentLinkSafety";
import {
  MAX_OFFICE_PREVIEW_MB,
  OfficeFileMissingError,
  assertOfficePreviewSize,
  isOfficeFileMissingError,
  isOfficeFileTooLargeError,
} from "@/lib/office/previewLimits";
import { cn } from "@/lib/utils";
import Loading from "./Loading";
import DocumentFallback from "./DocumentFallback";
import PreviewErrorBoundary from "./PreviewErrorBoundary";

const LOADING_CLASS = "mr-32";

/**
 * docx-preview 渲染选项。
 *
 * 与 ZCode `previewPaneOfficeDocxContent.tsx` 的 `DOCX_RENDER_OPTIONS` 对齐：
 * 保留 OOXML section 边界与各页 pgMar（`ignoreLastRenderedPageBreak`），
 * 打开页眉/页脚/脚注/尾注，关闭修订与批注（只读预览不需要）。
 */
const DOCX_RENDER_OPTIONS = {
  breakPages: true,
  debug: false,
  experimental: true,
  ignoreFonts: false,
  ignoreHeight: false,
  // docx-preview@0.4.0 在该选项为 false 时会合并纸张尺寸相同、
  // 但页边距不同的相邻 section，导致封面后的正文丢失分页并沿用封面的零边距。
  // 保持官方预览的默认行为，优先保留 OOXML section 边界和各页 pgMar。
  ignoreLastRenderedPageBreak: true,
  ignoreWidth: false,
  inWrapper: true,
  renderAltChunks: false,
  renderChanges: false,
  renderComments: false,
  renderEndnotes: true,
  renderFooters: true,
  renderFootnotes: true,
  renderHeaders: true,
  useBase64URL: true,
} satisfies Partial<Options>;

// docx-preview 默认 50% 黑色页面阴影，在预览面板里会形成过深黑边。
// 换成与 ZCode 一致的浅色纸张阴影（追加作用域样式覆盖，不修改库本身）。
const DOCX_PAGE_BOX_SHADOW =
  "0 2px 10px rgba(15, 23, 42, 0.08), 0 1px 2px rgba(15, 23, 42, 0.05)";

let nextDocxPreviewClassId = 0;

/** 每次挂载用独立作用域 class，避免不同实例的覆盖样式互相污染。 */
function createDocxPreviewClassName(): string {
  nextDocxPreviewClassId += 1;
  return `docx-preview-scope-${nextDocxPreviewClassId}`;
}

interface WordRendererProps {
  fileUrl: string;
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
  /**
   * true = 旧版 `.doc`（OLE2 复合文档）。
   *
   * 走 `LegacyDocCanvas`（react-docx 排版引擎），而不是 `.docx` 的 docx-preview。
   * 原因见 `@/lib/lazy/legacyDocImport`：转字节再交给 docx-preview 会丢分页，
   * 多页文档只剩第 1 页且被 `overflow: hidden` 裁掉。
   */
  legacyDoc?: boolean;
  /** 工作区内嵌时隐藏自带 header，避免与外层重复 */
  hideChrome?: boolean;
  className?: string;
}

/**
 * 把取数/渲染错误翻译成可读文案。
 *
 * 这里刻意**分类**（对齐 ZCode 的错误语义）：文件过大、文件不存在、网络/资源失效
 * 三种情况给不同提示，而不是统一成一句「引用资源不存在或已失效」。
 */
const resolveFetchError = (error: unknown) => {
  if (isOfficeFileTooLargeError(error)) {
    return `文件过大（超过 ${MAX_OFFICE_PREVIEW_MB}MB），已停止在线预览。请下载后用本地 Word / WPS 打开。`;
  }
  if (isOfficeFileMissingError(error)) {
    return "文件不存在或已被清理，请确认该产物是否仍可访问。";
  }
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

interface DocxCanvasProps {
  buffer: ArrayBuffer;
  fileName?: string;
  /** 旧版 .doc 转换后同样走这条链路，但探针/样式需要区分来源 */
  previewKind?: "docx" | "doc";
  className?: string;
  onRenderError: (error: unknown) => void;
}

/**
 * docx 画布：viewport → frame → content 三层 + 响应式适应宽度。
 *
 * 纸张按原始像素宽度渲染（A4 ≈ 794px），窄面板下只缩小不放大，
 * 并把缩放后的宽高同步回 frame，避免 `transform` 之后仍保留未缩放的横向滚动区。
 */
const DocxCanvas: CuteType.FC<DocxCanvasProps> = (props) => {
  const { buffer, className, previewKind = "docx", onRenderError } = props;

  const viewportRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const renderContainerRef = useRef<HTMLDivElement | null>(null);
  const [previewClassName] = useState(createDocxPreviewClassName);
  const [fit, setFit] = useState<DocxPreviewFit | null>(null);
  const [renderState, setRenderState] = useState<"loading" | "ready" | "error">("loading");
  const onRenderErrorRef = useRef(onRenderError);
  onRenderErrorRef.current = onRenderError;

  const updateFit = useCallback(() => {
    const viewport = viewportRef.current;
    const content = contentRef.current;
    if (!viewport || !content || renderState !== "ready") {
      return;
    }

    const next = calculateDocxPreviewFit({
      availableWidth: viewport.clientWidth,
      naturalHeight: content.offsetHeight,
      naturalWidth: content.offsetWidth,
    });
    if (!next) {
      return;
    }

    setFit((current) => (isSameDocxPreviewFit(current, next) ? current : next));
  }, [renderState]);

  useEffect(() => {
    const visibleContainer = renderContainerRef.current;
    if (!visibleContainer) {
      return;
    }

    let active = true;
    let disposeLinkSafety: (() => void) | undefined;
    const className = previewClassName;
    const renderRoot = document.createElement("div");
    const styleContainer = document.createElement("div");
    const bodyContainer = document.createElement("div");
    renderRoot.dataset.docxRenderRoot = "";
    styleContainer.dataset.docxRenderStyles = "";
    bodyContainer.dataset.docxRenderBody = "";
    renderRoot.append(styleContainer, bodyContainer);

    setRenderState("loading");
    setFit(null);
    visibleContainer.replaceChildren();

    void loadDocxPreview()
      .then(({ renderAsync }) =>
        renderAsync(buffer, bodyContainer, styleContainer, {
          ...DOCX_RENDER_OPTIONS,
          className,
        })
      )
      .then(() => {
        if (!active) {
          return;
        }

        const wrapper = bodyContainer.querySelector<HTMLElement>(`.${className}-wrapper`);
        if (wrapper) {
          // docx-preview 默认给页面包装层写入灰色背景和固定 30px padding，
          // 会与预览面板主题冲突，也会让窄屏缩放把装饰间距算进纸张宽度。
          wrapper.style.background = "transparent";
          wrapper.style.padding = "0";
          wrapper.style.width = "max-content";
        }

        const pageSurfaceStyle = document.createElement("style");
        pageSurfaceStyle.dataset.docxPageSurfaceStyle = "";
        pageSurfaceStyle.textContent = `.${className}-wrapper>section.${className} { box-shadow: ${DOCX_PAGE_BOX_SHADOW}; }`;
        styleContainer.append(pageSurfaceStyle);

        // 文档内超链接来自不受信任的 OOXML，挂载后统一净化并阻断内部导航。
        disposeLinkSafety = installDocumentLinkSafety(renderRoot);

        visibleContainer.replaceChildren(renderRoot);
        setRenderState("ready");
      })
      .catch((error: unknown) => {
        if (!active) {
          return;
        }

        console.error("[WordRenderer] DOCX 文件解析失败", error);
        visibleContainer.replaceChildren();
        setRenderState("error");
        onRenderErrorRef.current(error);
      });

    return () => {
      // renderAsync 不提供取消能力；切换文件后只允许最新 buffer 提交可见 DOM，
      // 旧任务即使稍后完成也只能停留在脱离文档树的临时容器中。
      active = false;
      disposeLinkSafety?.();
      renderRoot.remove();
      visibleContainer.replaceChildren();
    };
  }, [buffer, previewClassName]);

  useLayoutEffect(() => {
    if (renderState !== "ready") {
      return;
    }

    updateFit();
    if (typeof ResizeObserver === "undefined") {
      return;
    }

    const resizeObserver = new ResizeObserver(updateFit);
    if (viewportRef.current) {
      resizeObserver.observe(viewportRef.current);
    }
    if (contentRef.current) {
      resizeObserver.observe(contentRef.current);
    }
    return () => {
      resizeObserver.disconnect();
    };
  }, [renderState, updateFit]);

  return (
    <div
      aria-busy={renderState === "loading" ? "true" : undefined}
      className={cn(
        "relative h-full min-h-0 w-full min-w-0 bg-[var(--chat-surface-muted)]",
        className
      )}
      data-office-preview-kind={previewKind}
    >
      <div
        className={cn(
          "h-full min-h-0 w-full min-w-0 overflow-auto p-3 sm:p-4",
          renderState === "error" && "hidden"
        )}
      >
        <div ref={viewportRef} className="w-full min-w-0" data-docx-fit-viewport>
          <div
            className="relative mx-auto"
            data-docx-fit-frame
            style={fit ? { width: fit.width, height: fit.height } : undefined}
          >
            <div
              ref={contentRef}
              className="w-max"
              data-docx-fit-content
              style={
                fit
                  ? {
                      position: "absolute",
                      left: 0,
                      top: 0,
                      transform: `scale(${fit.scale})`,
                      transformOrigin: "top left",
                    }
                  : undefined
              }
            >
              <div ref={renderContainerRef} />
            </div>
          </div>
        </div>
      </div>

      {renderState === "loading" ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <Loading className={LOADING_CLASS} />
        </div>
      ) : null}
    </div>
  );
};

const WordRenderer: CuteType.FC<WordRendererProps> = React.memo((props) => {
  const {
    fileUrl,
    fileName,
    downloadUrl,
    missingReason,
    legacyDoc = false,
    hideChrome = false,
    className,
  } = props;

  const [renderError, setRenderError] = useState<unknown>(null);
  const docLabel = legacyDoc ? "DOC" : "DOCX";

  const resolvedUrl = useMemo(
    () => normalizeFileUrlForBrowser(fileUrl || ""),
    [fileUrl]
  );
  const resolvedDownload = useMemo(
    () => normalizeFileUrlForBrowser(downloadUrl || fileUrl || "") || resolvedUrl,
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
      if (response.status === 404) {
        throw new OfficeFileMissingError();
      }
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      // 体积预检：Content-Length 可用时在下载前就拒绝；拿不到头就等读完后按真实字节数兜底。
      const contentLength = Number(response.headers.get("Content-Length") ?? "");
      assertOfficePreviewSize(Number.isFinite(contentLength) ? contentLength : null);
      const buffer = await response.arrayBuffer();
      assertOfficePreviewSize(buffer.byteLength);
      return buffer;
    },
    {
      refreshDeps: [legacyDoc, resolvedUrl, missingReason],
      onBefore: () => {
        setRenderError(null);
      },
    }
  );

  const displayError = error ?? renderError;
  if (displayError) {
    return (
      <DocumentFallback
        label={docLabel}
        title="Word 不可预览"
        description={resolveFetchError(displayError)}
        fileName={fileName}
        downloadUrl={resolvedDownload}
        className={className}
        type="error"
      />
    );
  }

  const errorFallback = (
    <DocumentFallback
      label={docLabel}
      title="Word 不可预览"
      description="文档渲染失败，请下载原件后用本地 Word / WPS 打开。"
      fileName={fileName}
      downloadUrl={resolvedDownload}
      className={className}
      type="error"
    />
  );

  return (
    <ViewerPanelShell
      label={docLabel}
      subtitle={fileName || "Word 预览"}
      className={className}
      hideHeader={hideChrome}
      headerRight={
        !hideChrome && resolvedDownload ? (
          <Button variant="ghost" size="sm" onClick={() => downloadFile(resolvedDownload, fileName)}>
            <Download className="h-3.5 w-3.5" />
            下载
          </Button>
        ) : null
      }
      bodyClassName={cn("p-0", hideChrome ? "h-full min-h-0 flex-1" : "h-[min(72vh,960px)]")}
    >
      {buffer ? (
        <PreviewErrorBoundary
          label={docLabel}
          fallback={errorFallback}
          resetKey={`${fileName ?? ""}:${buffer.byteLength}`}
        >
          {legacyDoc ? (
            <LegacyDocCanvas buffer={buffer} onRenderError={setRenderError} />
          ) : (
            <DocxCanvas
              buffer={buffer}
              fileName={fileName}
              previewKind="docx"
              onRenderError={setRenderError}
            />
          )}
        </PreviewErrorBoundary>
      ) : (
        <div className="flex h-full min-h-[200px] flex-col items-center justify-center gap-2 bg-[var(--chat-surface-muted)]">
          <Loading className={LOADING_CLASS} />
          {legacyDoc ? (
            <span className="text-xs text-muted-foreground">正在解析旧版 Word 文档…</span>
          ) : null}
        </div>
      )}
    </ViewerPanelShell>
  );
});

WordRenderer.displayName = "WordRenderer";

export default WordRenderer;
