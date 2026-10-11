import React, {
  type RefObject,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useRequest } from "ahooks";
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Copy,
  MousePointer2,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { cn } from "@/lib/utils";
import Loading from "./Loading";
import DocumentFallback from "./DocumentFallback";
import {
  PPTX_MAX_ZOOM_PERCENT,
  PPTX_MIN_ZOOM_PERCENT,
  PPTX_ZOOM_STEP_PERCENT,
  clampPresentationPageNumber,
  clampPresentationZoomPercent,
} from "@/lib/pptx/presentationPreviewControls";
import { installDocumentLinkSafety, sanitizeDocumentHref } from "@/lib/office/documentLinkSafety";
import type {
  PresentationPageElement,
  PresentationPreviewDocument,
  PresentationRenderHandle,
} from "@/lib/pptx/types";

/**
 * PPTX 在线预览面板。
 *
 * 移植自 ZCode `packages/ui/src/components/ui/pptx-preview-viewer.tsx`，保留了
 * 缩略图轨 / 翻页 / 缩放 / 元素选择四段外壳与交互语言；剥离了 ZCode 平台耦合的
 * 部分（PDF 导出依赖平台 print host、引用跳转依赖 Composer、AI 编辑依赖对话派发）。
 * 元素选择的浮层动作相应改为「复制文本」——本项目暂无 composer 预填通道。
 */

const LOADING_CLASS = "mr-32";

interface PptxRendererProps {
  fileUrl: string;
  fileName?: string;
  downloadUrl?: string;
  missingReason?: string;
  className?: string;
}

function isSamePresentationElement(
  left: PresentationPageElement | null | undefined,
  right: PresentationPageElement | null | undefined,
) {
  if (!left || !right) {
    return false;
  }
  return (
    left.nodeId === right.nodeId &&
    left.nodeType === right.nodeType &&
    left.rowIndex === right.rowIndex &&
    left.cellIndex === right.cellIndex
  );
}

function elementKey(element: PresentationPageElement) {
  return [element.nodeId, element.nodeType, element.rowIndex ?? "", element.cellIndex ?? ""].join(
    ":"
  );
}

const TOOLBAR_ICON_BTN =
  "inline-flex size-7 shrink-0 items-center justify-center rounded-md text-foreground-subtle transition-colors hover:bg-hover hover:text-foreground disabled:pointer-events-none disabled:opacity-35";

interface RenderedPageProps {
  document: PresentationPreviewDocument;
  pageIndex: number;
  scale: number;
  generation: number;
  interactive?: boolean;
  onNavigate?: (pageIndex: number) => void;
  onOpenBrowserUrl?: (url: string) => void;
  onRenderError?: (generation: number, error: Error) => void;
  selectionMode?: boolean;
  elements?: readonly PresentationPageElement[];
  selectedElement?: PresentationPageElement | null;
  onSelectElement?: (element: PresentationPageElement) => void;
  /** 主视图把页面外框 ref 交给上层，用于计算选择浮层锚点。 */
  frameRef?: RefObject<HTMLDivElement | null>;
}

function RenderedPage({
  document,
  pageIndex,
  scale,
  generation,
  interactive = false,
  onNavigate,
  onOpenBrowserUrl,
  onRenderError,
  selectionMode = false,
  elements = [],
  selectedElement = null,
  onSelectElement,
  frameRef,
}: RenderedPageProps) {
  const internalFrameRef = useRef<HTMLDivElement | null>(null);
  const internalMountRef = useRef<HTMLDivElement | null>(null);
  const frame = frameRef ?? internalFrameRef;
  const mountRef = internalMountRef;

  useEffect(() => {
    const frameEl = frame.current;
    if (!frameEl) {
      return;
    }
    // 幻灯片内部 overflow:hidden 容器的滚动条已被全局隐藏，但选区拖拽自动滚动、
    // 链接焦点 scrollIntoView 等原生行为仍能滚动它们，内容会无声偏移且无法拖回。
    // scroll 不冒泡，这里用捕获监听把内部任何滚动偏移复位。
    const resetScroll = (event: Event) => {
      const target = event.target;
      if (target instanceof HTMLElement && (target.scrollTop !== 0 || target.scrollLeft !== 0)) {
        target.scrollTop = 0;
        target.scrollLeft = 0;
      }
    };
    frameEl.addEventListener("scroll", resetScroll, true);
    return () => frameEl.removeEventListener("scroll", resetScroll, true);
  }, [frame]);

  useEffect(() => {
    const mount = mountRef.current;
    if (!mount) {
      return;
    }

    let handle: PresentationRenderHandle | null = null;
    let disposeLinkSafety: (() => void) | undefined;
    let cancelled = false;
    const reportRenderError = (error: unknown) => {
      if (cancelled) {
        return;
      }
      onRenderError?.(generation, error instanceof Error ? error : new Error(String(error)));
    };
    try {
      handle = document.renderPage(pageIndex, mount, {
        onNavigate: (target) => {
          if (target.pageIndex !== undefined) {
            onNavigate?.(target.pageIndex);
            return;
          }
          const safeUrl = sanitizeDocumentHref(target.url);
          if (safeUrl && !safeUrl.startsWith("#")) {
            onOpenBrowserUrl?.(safeUrl);
          }
        },
      });
      // PPTX renderer 会把 OOXML 外链直接写成 <a href>；必须在页面挂载点
      // 统一净化并阻断主 renderer 导航，同时覆盖异步追加的链接节点。
      disposeLinkSafety = installDocumentLinkSafety(mount, onOpenBrowserUrl);
      void handle.ready.catch(reportRenderError);
    } catch (error) {
      reportRenderError(error);
    }

    return () => {
      // dispose() 不保证 ready Promise 同步结束；先取消回调，避免已卸载页面的迟到错误写入下一次会话。
      cancelled = true;
      disposeLinkSafety?.();
      handle?.dispose();
      mount.replaceChildren();
    };
  }, [document, generation, onNavigate, onOpenBrowserUrl, onRenderError, pageIndex]);

  return (
    <div
      ref={frame}
      className="relative shrink-0 overflow-hidden bg-background shadow-md"
      style={{
        width: document.pageSize.width * scale,
        height: document.pageSize.height * scale,
      }}
    >
      {/* renderer mount 必须是不含 React 子节点的独立 leaf：cleanup 的
          replaceChildren() 会清空它，若与 React 容器共用会让 React removeChild 崩溃。 */}
      <div
        data-pptx-render-surface=""
        className="absolute left-0 top-0 origin-top-left"
        style={{
          width: document.pageSize.width,
          height: document.pageSize.height,
          transform: `scale(${scale})`,
        }}
      >
        <div
          ref={mountRef}
          className={cn(
            "absolute inset-0",
            interactive && !selectionMode ? "pointer-events-auto" : "pointer-events-none"
          )}
        />
        {selectionMode ? (
          <div
            className="pointer-events-none absolute inset-0"
            data-pptx-element-selection-overlay="true"
          >
            {elements.map((element) => {
              const key = elementKey(element);
              const selected = isSamePresentationElement(selectedElement, element);
              return (
                <button
                  key={key}
                  type="button"
                  aria-pressed={selected}
                  aria-label={element.text || element.nodeName || element.nodeType}
                  className={cn(
                    "pointer-events-auto absolute border outline-none transition-colors",
                    selected
                      ? "border-primary bg-accent/30"
                      : "border-transparent bg-transparent hover:border-primary hover:bg-accent/20"
                  )}
                  style={{
                    left: element.bounds.x,
                    top: element.bounds.y,
                    width: element.bounds.width,
                    height: element.bounds.height,
                    zIndex: element.zIndex * 2 + (element.nodeType === "table-cell" ? 1 : 0),
                  }}
                  onClick={() => onSelectElement?.(element)}
                />
              );
            })}
          </div>
        ) : null}
      </div>
    </div>
  );
}

interface ThumbnailProps {
  document: PresentationPreviewDocument;
  pageIndex: number;
  generation: number;
  selected: boolean;
  scrollRoot: HTMLElement | null;
  onSelect: () => void;
}

function PptxThumbnail({
  document,
  pageIndex,
  generation,
  selected,
  scrollRoot,
  onSelect,
}: ThumbnailProps) {
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const [visible, setVisible] = useState(false);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    const button = buttonRef.current;
    if (!button || typeof IntersectionObserver === "undefined") {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => setVisible(entry?.isIntersecting ?? false),
      { root: scrollRoot, rootMargin: "200px 0px" }
    );
    observer.observe(button);
    return () => observer.disconnect();
  }, [scrollRoot]);

  useEffect(() => {
    const button = buttonRef.current;
    if (!button) {
      return;
    }
    const updateWidth = () => setWidth(Math.max(0, button.clientWidth - 16));
    updateWidth();
    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", updateWidth);
      return () => window.removeEventListener("resize", updateWidth);
    }
    const observer = new ResizeObserver(updateWidth);
    observer.observe(button);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (selected) {
      buttonRef.current?.scrollIntoView({ block: "nearest" });
    }
  }, [selected]);

  const scale = width > 0 ? width / document.pageSize.width : 0;
  return (
    <button
      ref={buttonRef}
      type="button"
      aria-current={selected ? "page" : undefined}
      aria-label={`第 ${pageIndex + 1} 页缩略图`}
      className={cn(
        "flex w-full flex-col items-center gap-1 rounded-lg border p-2 text-ui-xs text-foreground-subtle outline-none transition-colors",
        selected
          ? "border-border-hover bg-selected text-foreground"
          : "border-transparent hover:border-border hover:bg-surface-hover"
      )}
      onClick={onSelect}
    >
      <div
        className="flex w-full items-center justify-center overflow-hidden bg-surface"
        style={{
          aspectRatio: `${document.pageSize.width} / ${document.pageSize.height}`,
        }}
      >
        {visible && scale > 0 ? (
          <RenderedPage
            document={document}
            generation={generation}
            pageIndex={pageIndex}
            scale={scale}
          />
        ) : null}
      </div>
      <span className="tabular-nums">{pageIndex + 1}</span>
    </button>
  );
}

function useElementSize(node: HTMLElement | null) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  useEffect(() => {
    if (!node) {
      return;
    }
    const update = () => setSize({ width: node.clientWidth, height: node.clientHeight });
    update();
    if (typeof ResizeObserver === "undefined") {
      window.addEventListener("resize", update);
      return () => window.removeEventListener("resize", update);
    }
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, [node]);
  return size;
}

const PptxViewer: React.FC<{ data: ArrayBuffer; className?: string }> = ({
  data,
  className,
}) => {
  const [presentation, setPresentation] = useState<{
    generation: number;
    document: PresentationPreviewDocument;
  } | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [pageNumber, setPageNumber] = useState(1);
  const [pageInput, setPageInput] = useState("1");
  const [zoomPercent, setZoomPercent] = useState(100);
  const [thumbnailRoot, setThumbnailRoot] = useState<HTMLElement | null>(null);
  const [previewViewport, setPreviewViewport] = useState<HTMLElement | null>(null);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedElement, setSelectedElement] = useState<PresentationPageElement | null>(null);
  const [copied, setCopied] = useState(false);
  const [anchor, setAnchor] = useState<{ left: number; top: number } | null>(null);
  const viewportSize = useElementSize(previewViewport);
  const generationRef = useRef(0);
  const mainFrameRef = useRef<HTMLDivElement | null>(null);
  const document = presentation?.document ?? null;
  const documentGeneration = presentation?.generation ?? 0;

  useEffect(() => {
    const generation = generationRef.current + 1;
    generationRef.current = generation;
    let cancelled = false;
    let openedDocument: PresentationPreviewDocument | null = null;
    setPresentation(null);
    setLoadError(false);
    setPageNumber(1);
    setPageInput("1");
    setZoomPercent(100);
    setSelectionMode(false);
    setSelectedElement(null);
    setCopied(false);

    void import("@/lib/pptx/pptxRendererPreviewEngine")
      .then(({ pptxRendererPreviewEngine }) => pptxRendererPreviewEngine.open(data))
      .then((nextDocument) => {
        openedDocument = nextDocument;
        if (cancelled || generation !== generationRef.current) {
          nextDocument.dispose();
          return;
        }
        setPresentation({ generation, document: nextDocument });
      })
      .catch((error: unknown) => {
        if (cancelled || generation !== generationRef.current) {
          return;
        }
        console.error("[PptxRenderer] PPTX 解析失败", error);
        setLoadError(true);
      });

    return () => {
      // open() 的 Promise 不会因 effect cleanup 自动取消；generation 让旧会话的迟到结果只能释放资源，不能污染新会话状态。
      cancelled = true;
      if (generation === generationRef.current) {
        generationRef.current += 1;
      }
      openedDocument?.dispose();
    };
  }, [data]);

  const goToPage = useCallback(
    (target: number) => {
      if (!document || document.pageCount === 0) {
        return;
      }
      const nextPage = clampPresentationPageNumber(target, document.pageCount);
      setPageNumber(nextPage);
      setPageInput(String(nextPage));
    },
    [document]
  );

  const commitPageInput = useCallback(() => {
    const parsed = Number.parseInt(pageInput.trim(), 10);
    if (Number.isNaN(parsed)) {
      setPageInput(String(pageNumber));
      return;
    }
    goToPage(parsed);
  }, [goToPage, pageInput, pageNumber]);

  const exitSelection = useCallback(() => {
    setSelectionMode(false);
    setSelectedElement(null);
    setCopied(false);
  }, []);

  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      const target = event.target as HTMLElement;
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable) {
        return;
      }
      if (event.key === "Escape" && selectionMode) {
        event.preventDefault();
        exitSelection();
      } else if (event.key === "ArrowLeft") {
        event.preventDefault();
        goToPage(pageNumber - 1);
      } else if (event.key === "ArrowRight") {
        event.preventDefault();
        goToPage(pageNumber + 1);
      }
    },
    [exitSelection, goToPage, pageNumber, selectionMode]
  );

  const mainScale = useMemo(() => {
    if (!document || viewportSize.width === 0 || viewportSize.height === 0) {
      return 1;
    }
    const fitScale = Math.min(
      Math.max(1, viewportSize.width - 32) / document.pageSize.width,
      Math.max(1, viewportSize.height - 32) / document.pageSize.height
    );
    return fitScale * (zoomPercent / 100);
  }, [document, viewportSize.height, viewportSize.width, zoomPercent]);

  const handleRenderError = useCallback((generation: number, error: Error) => {
    if (generation !== generationRef.current) {
      return;
    }
    console.error("[PptxRenderer] PPTX 页面渲染失败", error);
    setLoadError(true);
  }, []);

  const handlePageNavigate = useCallback(
    (targetPageIndex: number) => goToPage(targetPageIndex + 1),
    [goToPage]
  );

  useEffect(() => {
    setSelectedElement(null);
    setCopied(false);
  }, [documentGeneration, pageNumber]);

  const pageElements = useMemo(
    () => (document && selectionMode ? document.getPageElements(pageNumber - 1) : []),
    [document, pageNumber, selectionMode]
  );

  const handleSelectElement = useCallback((element: PresentationPageElement) => {
    setCopied(false);
    setSelectedElement(element);
  }, []);

  // 选择浮层锚点：由页面外框 client rect + 元素 bounds × scale 推出，
  // 不依赖 DOM 查询，缩放/滚动/翻页后重算即可保持一致。
  useEffect(() => {
    if (!selectionMode || !selectedElement) {
      setAnchor(null);
      return;
    }
    const compute = () => {
      const viewport = previewViewport;
      const frameEl = mainFrameRef.current;
      if (!viewport || !frameEl) {
        setAnchor(null);
        return;
      }
      const viewportRect = viewport.getBoundingClientRect();
      const frameRect = frameEl.getBoundingClientRect();
      setAnchor({
        left: frameRect.left - viewportRect.left + (selectedElement.bounds.x + selectedElement.bounds.width / 2) * mainScale,
        top: frameRect.top - viewportRect.top + selectedElement.bounds.y * mainScale,
      });
    };
    const raf = requestAnimationFrame(compute);
    previewViewport?.addEventListener("scroll", compute, { passive: true });
    window.addEventListener("resize", compute);
    const observer =
      typeof ResizeObserver !== "undefined" && previewViewport
        ? new ResizeObserver(compute)
        : null;
    if (previewViewport && observer) {
      observer.observe(previewViewport);
    }
    return () => {
      cancelAnimationFrame(raf);
      previewViewport?.removeEventListener("scroll", compute);
      window.removeEventListener("resize", compute);
      observer?.disconnect();
    };
  }, [selectionMode, selectedElement, previewViewport, mainScale]);

  const handleCopyText = useCallback(async () => {
    const text = selectedElement?.text?.trim();
    if (!text) {
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }, [selectedElement]);

  if (loadError) {
    return (
      <div className={cn("flex h-full items-center justify-center p-6", className)}>
        <DocumentFallback
          label="PPTX"
          title="演示文稿解析失败"
          description="文件可能已损坏，或使用了当前渲染引擎尚不支持的 OOXML 特性。"
          type="error"
        />
      </div>
    );
  }
  if (!document) {
    return (
      <div className={cn("flex h-full items-center justify-center bg-white", className)}>
        <Loading className={LOADING_CLASS} />
      </div>
    );
  }
  if (document.pageCount === 0) {
    return (
      <div className={cn("flex h-full items-center justify-center p-6 text-ui-base text-foreground-subtle", className)}>
        演示文稿中没有幻灯片
      </div>
    );
  }

  const pageIndex = pageNumber - 1;
  const selectedText = selectedElement?.text?.trim() || "";
  return (
    <div
      tabIndex={0}
      onKeyDown={handleKeyDown}
      className={cn("flex h-full min-h-0 outline-none", className)}
    >
      <aside
        ref={setThumbnailRoot}
        aria-label="幻灯片缩略图"
        className="w-24 shrink-0 overflow-y-auto border-r border-border bg-surface/30 p-2 sm:w-32"
      >
        <div className="flex flex-col gap-2">
          {Array.from({ length: document.pageCount }, (_, index) => (
            <PptxThumbnail
              key={index}
              document={document}
              generation={documentGeneration}
              pageIndex={index}
              selected={index === pageIndex}
              scrollRoot={thumbnailRoot}
              onSelect={() => goToPage(index + 1)}
            />
          ))}
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col bg-background">
        <div ref={setPreviewViewport} className="relative min-h-0 flex-1 overflow-auto">
          <div className="grid min-h-full min-w-full place-items-center p-4">
            <RenderedPage
              document={document}
              generation={documentGeneration}
              pageIndex={pageIndex}
              scale={mainScale}
              interactive
              selectionMode={selectionMode}
              elements={pageElements}
              selectedElement={selectedElement}
              onSelectElement={handleSelectElement}
              frameRef={mainFrameRef}
              onNavigate={handlePageNavigate}
              onRenderError={handleRenderError}
            />
          </div>

          {/* 元素选择浮层：跟随所选元素上方，动作按本项目能力裁剪为「复制文本」。 */}
          {selectionMode && selectedElement && anchor ? (
            <div
              className="pointer-events-none absolute z-20"
              style={{ left: anchor.left, top: anchor.top, transform: "translate(-50%, -100%)" }}
            >
              <div className="pointer-events-auto mb-2 flex max-w-[min(20rem,80vw)] items-center gap-1 rounded-lg border border-popover-border bg-popover p-1 shadow-md">
                <span className="min-w-0 flex-1 truncate px-2 text-ui-sm text-foreground-subtle">
                  {selectedText || selectedElement.nodeName || "已选中元素"}
                </span>
                <button
                  type="button"
                  disabled={!selectedText}
                  onClick={() => void handleCopyText()}
                  className={cn(
                    "inline-flex h-7 shrink-0 items-center gap-1 rounded-md px-2 text-ui-sm font-medium transition-colors",
                    "text-foreground hover:bg-hover disabled:pointer-events-none disabled:opacity-40"
                  )}
                >
                  {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
                  {copied ? "已复制" : "复制文本"}
                </button>
              </div>
            </div>
          ) : null}
        </div>

        <div className="flex min-h-10 shrink-0 flex-wrap items-center justify-center gap-1 border-t border-border bg-surface/30 px-2 py-1.5 text-ui-base">
          <button
            type="button"
            aria-pressed={selectionMode}
            aria-label={selectionMode ? "退出元素选择" : "选择元素"}
            title={selectionMode ? "退出元素选择" : "选择元素"}
            onClick={() => {
              if (selectionMode) {
                exitSelection();
              } else {
                setSelectionMode(true);
              }
            }}
            className={cn(
              "inline-flex h-7 shrink-0 items-center gap-1.5 rounded-md px-2 text-ui-sm font-medium transition-colors",
              selectionMode
                ? "bg-selected text-foreground"
                : "text-foreground-subtle hover:bg-hover hover:text-foreground"
            )}
          >
            <MousePointer2 className="size-4" />
            {selectionMode ? "退出选择" : "选择元素"}
          </button>
          <button
            type="button"
            aria-label="上一页"
            title="上一页"
            disabled={pageNumber <= 1}
            onClick={() => goToPage(pageNumber - 1)}
            className={TOOLBAR_ICON_BTN}
          >
            <ChevronLeft className="size-4" />
          </button>
          <input
            type="text"
            inputMode="numeric"
            aria-label="页码"
            value={pageInput}
            onChange={(event) => setPageInput(event.target.value)}
            onBlur={commitPageInput}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                commitPageInput();
                event.currentTarget.blur();
              }
            }}
            className="h-7 w-12 rounded-md border border-input-border bg-input text-center text-ui-base tabular-nums text-foreground outline-none focus-visible:border-input-border-focused"
          />
          <span className="min-w-10 text-center tabular-nums text-foreground-subtle">
            / {document.pageCount}
          </span>
          <button
            type="button"
            aria-label="下一页"
            title="下一页"
            disabled={pageNumber >= document.pageCount}
            onClick={() => goToPage(pageNumber + 1)}
            className={TOOLBAR_ICON_BTN}
          >
            <ChevronRight className="size-4" />
          </button>
          <div className="mx-1 h-5 w-px bg-border" aria-hidden="true" />
          <button
            type="button"
            aria-label="缩小"
            title="缩小"
            disabled={zoomPercent <= PPTX_MIN_ZOOM_PERCENT}
            onClick={() =>
              setZoomPercent((current) =>
                clampPresentationZoomPercent(current - PPTX_ZOOM_STEP_PERCENT)
              )
            }
            className={TOOLBAR_ICON_BTN}
          >
            <ZoomOut className="size-4" />
          </button>
          <span className="w-12 text-center tabular-nums text-foreground-subtle">
            {zoomPercent}%
          </span>
          <button
            type="button"
            aria-label="放大"
            title="放大"
            disabled={zoomPercent >= PPTX_MAX_ZOOM_PERCENT}
            onClick={() =>
              setZoomPercent((current) =>
                clampPresentationZoomPercent(current + PPTX_ZOOM_STEP_PERCENT)
              )
            }
            className={TOOLBAR_ICON_BTN}
          >
            <ZoomIn className="size-4" />
          </button>
        </div>
      </div>
    </div>
  );
};

const PptxRenderer: ReactorType.FC<PptxRendererProps> = React.memo((props) => {
  const { fileUrl, fileName, downloadUrl, missingReason, className } = props;
  const resolvedDownload = downloadUrl || fileUrl;

  const { data, loading, error } = useRequest(
    async () => {
      if (missingReason) {
        throw new Error(missingReason);
      }
      if (!fileUrl) {
        throw new Error("引用资源不存在或已失效");
      }
      const response = await fetch(fileUrl);
      if (!response.ok) {
        throw new Error("Network response was not ok");
      }
      return await response.arrayBuffer();
    },
    {
      refreshDeps: [fileUrl, missingReason],
    }
  );

  if (loading) {
    return (
      <div className={cn("flex h-full min-h-[200px] items-center justify-center bg-white", className)}>
        <Loading className={LOADING_CLASS} />
      </div>
    );
  }

  if (error || !data) {
    return (
      <DocumentFallback
        label="PPTX"
        title="演示文稿不可读取"
        description={missingReason || "引用资源不存在或已失效"}
        fileName={fileName}
        downloadUrl={resolvedDownload}
        className={className}
        type="info"
      />
    );
  }

  return <PptxViewer data={data} className={className} />;
});

PptxRenderer.displayName = "PptxRenderer";

export default PptxRenderer;
