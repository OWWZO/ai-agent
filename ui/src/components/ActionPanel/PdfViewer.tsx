import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  Download,
  Ellipsis,
  ExternalLink,
  Minus,
  PanelLeft,
  Plus,
  Printer,
  Scaling,
} from "lucide-react";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/Page/TextLayer.css";
import pdfWorkerSrc from "pdfjs-dist/build/pdf.worker.min.mjs?url";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  DEFAULT_PAGE_SIZE,
  DEFAULT_SCALE,
  MAX_SCALE,
  MIN_SCALE,
  WHEEL_ZOOM_SENSITIVITY,
  ZOOM_COMMIT_DELAY_MS,
  ZOOM_STEP,
  clampScale,
  type PdfPageSize,
} from "@/lib/pdf/pdfZoom";
import { createPdfJsDocumentOptions } from "@/lib/pdf/pdfJsAssets";

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorkerSrc;

// 与 Vite base 解析，子路径部署时也能读到各自的静态资源。
const DOCUMENT_OPTIONS = createPdfJsDocumentOptions(
  typeof import.meta.env?.BASE_URL === "string" ? import.meta.env.BASE_URL : "./",
  globalThis.location?.href ?? "http://localhost/",
);

// 页面四周留白；页与页之间的间隙另算。
const STAGE_PADDING_X = 32;
const PAGE_GAP = 16;
// 只有落在可视区附近的页才真正渲染，避免一次性创建上百个 canvas。
const PAGE_RENDER_MARGIN = "900px 0px";
const THUMB_RENDER_MARGIN = "500px 0px";
// 离开可视区后延迟卸载：缩放会改变占位高度并触发整屏重排，边界页会在极短时间内
// 反复进出可视区。立刻卸载 <Page> 会取消正在进行的渲染任务，而 react-pdf 在渲染被取消时
// 不会恢复 canvas 的 visibility，页面会停在空白。留一段宽限期即可消除这种自伤式取消。
const SLOT_RELEASE_DELAY_MS = 2000;
// 单页渲染真正报错时的换 canvas 重挂载上限（有界）。
//
// 这里刻意**不**加计时器 watchdog：pdf.js 一次页面渲染在慢环境里可能要几十秒
// （实测 dpr=2 下 20–60s，见 plans/021 的 022 章节），任何"等 N 秒没出图就重来"的兜底
// 都会 cancel 掉正在推进的渲染任务并从头再来，把空白时间成倍放大。
// 渲染是否结束必须以 react-pdf 的 onRenderSuccess / onRenderError 信号为准。
const MAX_RENDER_REMOUNT = 2;

export type PdfViewerSource = string | Blob | ArrayBuffer | Uint8Array;

export interface PdfViewerLabels {
  loading: string;
  loadError: string;
  noData: string;
  pageInput: string;
  zoomIn: string;
  zoomOut: string;
  fitWidth: string;
  toggleSidebar: string;
  download: string;
  print: string;
  openInNewTab: string;
  more: string;
  thumbnails: string;
  rendering: string;
  renderFailed: string;
}

const DEFAULT_LABELS: PdfViewerLabels = {
  loading: "正在加载 PDF…",
  loadError: "PDF 加载失败",
  noData: "没有可显示的 PDF 数据",
  pageInput: "页码",
  zoomIn: "放大",
  zoomOut: "缩小",
  fitWidth: "适应页宽",
  toggleSidebar: "缩略图",
  download: "下载",
  print: "打印",
  openInNewTab: "在新标签页打开",
  more: "更多",
  thumbnails: "页面缩略图",
  rendering: "渲染中…",
  renderFailed: "渲染失败",
};

export interface PdfViewerProps {
  source: PdfViewerSource;
  /** 原始文件地址，用于「打印 / 在新标签页打开」；不传则隐藏这两项。 */
  fileUrl?: string;
  /** 传入后在工具条左侧显示文件名。 */
  fileName?: string;
  /** 传入后显示下载按钮。 */
  onDownload?: () => void;
  labels?: Partial<PdfViewerLabels>;
  onLoadError?: (error: Error) => void;
  className?: string;
}

type PdfDocumentFile = string | Blob | { data: Uint8Array };

function normalizePdfSource(source: PdfViewerSource): PdfDocumentFile {
  if (typeof source === "string" || source instanceof Blob) {
    return source;
  }
  // pdf.js 会把二进制数据 transfer 给 worker，导致原 buffer detached；
  // 复制一份，保证调用方复用同一份数据重新打开预览时不会报错。
  if (source instanceof ArrayBuffer) {
    return { data: new Uint8Array(source.slice(0)) };
  }
  return { data: new Uint8Array(source) };
}

// macOS 绑定 command，其余平台（Windows / Linux）绑定 ctrl。
function isAppleKeyboardPlatform(): boolean {
  if (typeof navigator === "undefined") return false;
  const platform =
    (navigator as unknown as { userAgentData?: { platform?: string } }).userAgentData?.platform ||
    navigator.platform ||
    "";
  return /mac|iphone|ipad|ipod/i.test(platform);
}

/**
 * 带释放宽限期的可视区探测。
 *
 * 直接跟随 `isIntersecting` 卸载内容会带来一个隐蔽的自伤：缩放会在同一次提交里
 * 改掉占位高度，滚动容器随之整体重排，边界页的 `isIntersecting` 会在几帧内抖动。
 * 抖动期间卸载 `<Page>` 会 cancel 掉正在进行中的渲染任务，而 react-pdf 的 Canvas 只在
 * 渲染成功时恢复 `canvas.style.visibility`（取消路径直接 return），于是该页会停在空白。
 *
 * 这里只在「离开可视区且持续 `releaseDelayMs` 仍未回来」时才真正释放，抖动被吸收掉。
 */
function useStickyInViewport<T extends HTMLElement>(
  root: HTMLElement | null,
  rootMargin: string,
  releaseDelayMs = SLOT_RELEASE_DELAY_MS,
) {
  const elementRef = useRef<T | null>(null);
  const [mounted, setMounted] = useState(false);
  const releaseTimerRef = useRef<number | null>(null);

  useEffect(() => {
    const element = elementRef.current;
    if (!element || !root) return;

    const cancelRelease = () => {
      if (releaseTimerRef.current !== null) {
        window.clearTimeout(releaseTimerRef.current);
        releaseTimerRef.current = null;
      }
    };

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            cancelRelease();
            setMounted(true);
          } else if (releaseTimerRef.current === null) {
            releaseTimerRef.current = window.setTimeout(() => {
              releaseTimerRef.current = null;
              setMounted(false);
            }, releaseDelayMs);
          }
        }
      },
      { root, rootMargin },
    );
    observer.observe(element);

    return () => {
      observer.disconnect();
      cancelRelease();
    };
  }, [releaseDelayMs, root, rootMargin]);

  return { elementRef, mounted };
}

interface PageSlotProps {
  pageNumber: number;
  scale: number;
  root: HTMLElement | null;
  size?: PdfPageSize;
  labels: Pick<PdfViewerLabels, "rendering" | "renderFailed">;
  onMeasure: (pageNumber: number, size: PdfPageSize) => void;
  registerSlot: (pageNumber: number, element: HTMLDivElement | null) => void;
}

type PageRenderState = "pending" | "ready" | "failed";

/**
 * 连续滚动里的单页容器：先占位撑出滚动高度，进入可视区才挂载真正的 <Page>。
 *
 * 渲染状态以 react-pdf 的 `onRenderSuccess` / `onRenderError` 为准，而不是靠计时器猜测：
 * - 成功 → ready，占位层移除；
 * - 真正失败（非 AbortException，react-pdf 已经过滤掉取消错误）→ 换一个 canvas 重挂载一次，
 *   因为 pdf.js 的 `InternalRenderTask` 用一个静态 WeakSet 记录「正在使用的 canvas」，
 *   一旦某次渲染既没完成也没被取消，同一个 canvas 上的后续 render 会直接抛
 *   "Cannot use the same canvas during multiple render() operations"，并且再也不会有新的尝试。
 *   换元素是唯一能真正脱困的方式，同时用 MAX_RENDER_REMOUNT 保证有界。
 */
function PageSlot({
  pageNumber,
  scale,
  root,
  size,
  labels,
  onMeasure,
  registerSlot,
}: PageSlotProps) {
  const { elementRef, mounted } = useStickyInViewport<HTMLDivElement>(root, PAGE_RENDER_MARGIN);
  const [renderState, setRenderState] = useState<PageRenderState>("pending");
  const [remountToken, setRemountToken] = useState(0);
  const remountRef = useRef(0);
  // 每一批渲染参数（scale）对应一次新的渲染生命周期。
  const renderKey = String(scale);

  useEffect(() => {
    registerSlot(pageNumber, elementRef.current);
    return () => registerSlot(pageNumber, null);
  }, [pageNumber, registerSlot]);

  // 缩放提交、或槽位重新挂载，都会带来一次全新的 canvas 渲染：
  // 回到 pending（露出占位层），并重置该批次的失败重挂载预算。
  useEffect(() => {
    remountRef.current = 0;
    setRemountToken(0);
    setRenderState("pending");
  }, [mounted, renderKey]);

  const remountOnce = useCallback(() => {
    if (remountRef.current >= MAX_RENDER_REMOUNT) return false;
    remountRef.current += 1;
    setRemountToken((token) => token + 1);
    return true;
  }, []);

  // 渲染真正报错（AbortException 已被 react-pdf 过滤）时换一个 canvas 重挂载。
  const handleRenderError = useCallback(() => {
    if (remountOnce()) {
      setRenderState("pending");
      return;
    }
    setRenderState("failed");
  }, [remountOnce]);

  const pageSize = size ?? DEFAULT_PAGE_SIZE;
  const placeholderHeight = pageSize.height * scale;
  const placeholderWidth = pageSize.width * scale;
  // 只有真正挂载了 <Page> 的槽位才需要提示「渲染中」，未进入可视区的槽位保持纯占位。
  const pending = mounted && renderState !== "ready";

  return (
    <div
      ref={elementRef}
      className="relative mx-auto"
      style={{ minHeight: placeholderHeight, marginBottom: PAGE_GAP }}
      data-pdf-page={pageNumber}
    >
      {mounted ? (
        <Page
          key={`${pageNumber}-${remountToken}`}
          pageNumber={pageNumber}
          scale={scale}
          renderAnnotationLayer={false}
          loading={<div style={{ height: placeholderHeight }} />}
          onLoadSuccess={(page) =>
            onMeasure(pageNumber, { width: page.originalWidth, height: page.originalHeight })
          }
          onRenderSuccess={() => setRenderState("ready")}
          onRenderError={handleRenderError}
          className="shadow-md"
        />
      ) : (
        <div style={{ width: placeholderWidth, height: placeholderHeight }} />
      )}

      {/* 画布渲染完成前 react-pdf 会把 canvas 设为 visibility:hidden，此时露出白底占位，
          避免缩放期间出现一块没有解释的空白。 */}
      {pending ? (
        <div
          className="pointer-events-none absolute inset-0 flex items-center justify-center rounded-sm bg-white text-ui-xs text-foreground-subtle"
          aria-hidden
        >
          {renderState === "failed" ? labels.renderFailed : labels.rendering}
        </div>
      ) : null}
    </div>
  );
}

interface ThumbnailItemProps {
  pageNumber: number;
  active: boolean;
  root: HTMLElement | null;
  onSelect: (pageNumber: number) => void;
  registerItem: (pageNumber: number, element: HTMLButtonElement | null) => void;
}

function ThumbnailItem({ pageNumber, active, root, onSelect, registerItem }: ThumbnailItemProps) {
  const { elementRef, mounted } = useStickyInViewport<HTMLButtonElement>(
    root,
    THUMB_RENDER_MARGIN,
  );

  return (
    <button
      type="button"
      ref={(element) => {
        elementRef.current = element;
        registerItem(pageNumber, element);
      }}
      onClick={() => onSelect(pageNumber)}
      className="group mb-3 flex w-full flex-col items-center gap-1.5"
      aria-label={`第 ${pageNumber} 页`}
      aria-current={active ? "page" : undefined}
    >
      <span
        className={cn(
          "flex w-full items-center justify-center overflow-hidden rounded-md border bg-white p-0.5 transition-colors",
          active
            ? "border-brand ring-1 ring-brand"
            : "border-border group-hover:border-foreground-subtlest",
        )}
        style={{ minHeight: 148 }}
      >
        {mounted ? (
          <Page pageNumber={pageNumber} width={104} renderAnnotationLayer={false} loading={null} />
        ) : null}
      </span>
      <span
        className={cn("text-ui-xs tabular-nums", active ? "text-brand" : "text-foreground-subtle")}
      >
        {pageNumber}
      </span>
    </button>
  );
}

interface ToolbarButtonProps {
  label: string;
  active?: boolean;
  disabled?: boolean;
  onClick?: () => void;
  children: ReactNode;
}

function ToolbarButton({ label, active, disabled, onClick, children }: ToolbarButtonProps) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      title={label}
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "text-foreground-subtle hover:text-foreground",
        active && "bg-selected text-foreground",
      )}
    >
      {children}
    </Button>
  );
}

export function PdfViewer({
  source,
  fileUrl,
  fileName,
  onDownload,
  labels,
  onLoadError,
  className,
}: PdfViewerProps) {
  const mergedLabels = useMemo(() => ({ ...DEFAULT_LABELS, ...labels }), [labels]);
  const file = useMemo<PdfDocumentFile>(() => normalizePdfSource(source), [source]);
  // 缩略图栏用独立的 pdf.js 文档。原因：react-pdf 的 <Page> 以 pageNumber 为键缓存
  // PDFPageProxy，同一个文档下两处渲染同一页会拿到同一个 proxy 并互相打断渲染，
  // canvas 会永久停在 visibility:hidden。二进制源必须再复制一份 —— pdf.js 会把 buffer
  // transfer 给 worker，两个文档不能共享同一份。
  const railFile = useMemo<PdfDocumentFile>(() => {
    if (typeof source === "string" || source instanceof Blob) return source;
    if (source instanceof ArrayBuffer) return { data: new Uint8Array(source.slice(0)) };
    return { data: new Uint8Array(source) };
  }, [source]);

  const [numPages, setNumPages] = useState<number | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageInput, setPageInput] = useState("1");
  const [scale, setScale] = useState(DEFAULT_SCALE);
  const [committedScale, setCommittedScale] = useState(DEFAULT_SCALE);
  const [railOpen, setRailOpen] = useState(true);
  const [pageSizes, setPageSizes] = useState<Record<number, PdfPageSize>>({});
  const [scrollEl, setScrollEl] = useState<HTMLDivElement | null>(null);
  const [railScrollEl, setRailScrollEl] = useState<HTMLDivElement | null>(null);
  const [containerWidth, setContainerWidth] = useState(0);

  const slotElementsRef = useRef(new Map<number, HTMLDivElement>());
  const railElementsRef = useRef(new Map<number, HTMLButtonElement>());
  const pendingZoomAnchorRef = useRef<{ page: number; ratio: number } | null>(null);
  const didInitialFitRef = useRef(false);
  const onLoadErrorRef = useRef(onLoadError);
  onLoadErrorRef.current = onLoadError;

  const pageNumbers = useMemo(
    () => (numPages ? Array.from({ length: numPages }, (_, index) => index + 1) : []),
    [numPages],
  );

  // ---- 尺寸与容器宽度 -------------------------------------------------------

  const handleMeasure = useCallback((pageNumber: number, size: PdfPageSize) => {
    setPageSizes((current) => {
      const existing = current[pageNumber];
      if (existing && existing.width === size.width && existing.height === size.height) {
        return current;
      }
      return { ...current, [pageNumber]: size };
    });
  }, []);

  const registerSlot = useCallback((pageNumber: number, element: HTMLDivElement | null) => {
    if (element) {
      slotElementsRef.current.set(pageNumber, element);
    } else {
      slotElementsRef.current.delete(pageNumber);
    }
  }, []);

  const registerRailItem = useCallback(
    (pageNumber: number, element: HTMLButtonElement | null) => {
      if (element) {
        railElementsRef.current.set(pageNumber, element);
      } else {
        railElementsRef.current.delete(pageNumber);
      }
    },
    [],
  );

  useEffect(() => {
    const element = scrollEl;
    if (!element) return;
    setContainerWidth(element.clientWidth);
    const observer = new ResizeObserver(() => setContainerWidth(element.clientWidth));
    observer.observe(element);
    return () => observer.disconnect();
  }, [scrollEl]);

  const baseSize = pageSizes[1] ?? DEFAULT_PAGE_SIZE;

  const computeFitScale = useCallback(() => {
    if (!containerWidth) return null;
    const available = Math.max(120, containerWidth - STAGE_PADDING_X);
    return clampScale(available / baseSize.width);
  }, [baseSize, containerWidth]);

  const applyFitWidth = useCallback(() => {
    const next = computeFitScale();
    if (next === null) return;
    setScale(next);
  }, [computeFitScale]);

  // 文档与首屏尺寸就绪后，默认按页宽铺满（对齐参考截图的初始观感）。
  useEffect(() => {
    if (didInitialFitRef.current || !numPages || !containerWidth || !pageSizes[1]) return;
    didInitialFitRef.current = true;
    applyFitWidth();
  }, [applyFitWidth, containerWidth, numPages, pageSizes]);

  // ---- 缩放提交（手势期间只改百分比，停顿后再交给 <Page>） --------------------

  /** 记录锚点：当前页顶部相对可视区顶部的比例，提交后按新布局还原，避免丢失阅读位置。 */
  const captureAnchor = useCallback(() => {
    const container = scrollEl;
    const element = slotElementsRef.current.get(currentPage);
    if (!container || !element) return;
    const containerRect = container.getBoundingClientRect();
    const elementRect = element.getBoundingClientRect();
    pendingZoomAnchorRef.current = {
      page: currentPage,
      ratio: elementRect.height > 0 ? (containerRect.top - elementRect.top) / elementRect.height : 0,
    };
  }, [currentPage, scrollEl]);

  useEffect(() => {
    if (scale === committedScale) return;
    const timer = window.setTimeout(() => {
      captureAnchor();
      setCommittedScale(scale);
    }, ZOOM_COMMIT_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [captureAnchor, committedScale, scale]);

  // 缩放提交会改变页面占位尺寸，需要在布局落地后按锚点校正滚动位置。
  useLayoutEffect(() => {
    const anchor = pendingZoomAnchorRef.current;
    pendingZoomAnchorRef.current = null;
    const container = scrollEl;
    if (!anchor || !container) return;
    const element = slotElementsRef.current.get(anchor.page);
    if (!element) return;
    const containerRect = container.getBoundingClientRect();
    const elementRect = element.getBoundingClientRect();
    container.scrollTop += elementRect.top + elementRect.height * anchor.ratio - containerRect.top;
  }, [committedScale, scrollEl]);

  // 修饰键 + 滚轮连续缩放。React 的 onWheel 委托在 root 上是 passive 监听，
  // preventDefault 不生效，这里直接挂非 passive 的原生监听。
  useEffect(() => {
    const container = scrollEl;
    if (!container || !numPages) return;
    const zoomWithAppleModifier = isAppleKeyboardPlatform();
    const handleWheel = (event: WheelEvent) => {
      const pressed = zoomWithAppleModifier ? event.metaKey : event.ctrlKey;
      if (!pressed) return;
      event.preventDefault();
      if (event.deltaY === 0) return;
      setScale((current) => clampScale(current * Math.exp(-event.deltaY * WHEEL_ZOOM_SENSITIVITY)));
    };
    container.addEventListener("wheel", handleWheel, { passive: false });
    return () => container.removeEventListener("wheel", handleWheel);
  }, [numPages, scrollEl]);

  // ---- 当前页跟踪 -----------------------------------------------------------

  const syncCurrentPage = useCallback(() => {
    const container = scrollEl;
    if (!container) return;
    const containerTop = container.getBoundingClientRect().top;
    let best = 1;
    let bestTop = Number.NEGATIVE_INFINITY;
    slotElementsRef.current.forEach((element, pageNumber) => {
      const top = element.getBoundingClientRect().top - containerTop;
      if (top <= 24 && top > bestTop) {
        bestTop = top;
        best = pageNumber;
      }
    });
    setCurrentPage(best);
  }, [scrollEl]);

  useEffect(() => {
    const container = scrollEl;
    if (!container) return;
    container.addEventListener("scroll", syncCurrentPage, { passive: true });
    return () => container.removeEventListener("scroll", syncCurrentPage);
  }, [scrollEl, syncCurrentPage]);

  useEffect(() => {
    if (numPages) syncCurrentPage();
  }, [numPages, syncCurrentPage]);

  useEffect(() => {
    setPageInput(String(currentPage));
  }, [currentPage]);

  // 缩略图栏跟随当前页滚动，但只在目标不可见时才动，避免打断用户。
  useEffect(() => {
    if (!railOpen) return;
    const rail = railScrollEl;
    const item = railElementsRef.current.get(currentPage);
    if (!rail || !item) return;
    const railRect = rail.getBoundingClientRect();
    const itemRect = item.getBoundingClientRect();
    if (itemRect.top < railRect.top) {
      rail.scrollTop += itemRect.top - railRect.top - 8;
    } else if (itemRect.bottom > railRect.bottom) {
      rail.scrollTop += itemRect.bottom - railRect.bottom + 8;
    }
  }, [currentPage, railOpen, railScrollEl]);

  // ---- 交互 -----------------------------------------------------------------

  const goToPage = useCallback(
    (target: number) => {
      if (!numPages) return;
      const clamped = Math.min(Math.max(1, Math.round(target)), numPages);
      const container = scrollEl;
      const element = slotElementsRef.current.get(clamped);
      if (container && element) {
        container.scrollTop +=
          element.getBoundingClientRect().top - container.getBoundingClientRect().top - 8;
      }
      setCurrentPage(clamped);
      setPageInput(String(clamped));
    },
    [numPages, scrollEl],
  );

  const commitPageInput = useCallback(() => {
    const parsed = Number.parseInt(pageInput.trim(), 10);
    if (Number.isNaN(parsed)) {
      setPageInput(String(currentPage));
      return;
    }
    goToPage(parsed);
  }, [currentPage, goToPage, pageInput]);

  const zoomBy = useCallback((delta: number) => {
    setScale((current) => clampScale(current + delta));
  }, []);

  const handleLoadSuccess = useCallback((document: { numPages: number }) => {
    setNumPages(document.numPages);
    setCurrentPage(1);
    setPageInput("1");
  }, []);

  const handlePrint = useCallback(() => {
    if (!fileUrl) return;
    // 交给浏览器内置 PDF 阅读器打印：用隐藏 iframe 承载原始文件，避免打印整个应用页面。
    const frame = document.createElement("iframe");
    frame.style.cssText = "position:fixed;right:0;bottom:0;width:0;height:0;border:0";
    frame.src = fileUrl;
    frame.onload = () => {
      try {
        frame.contentWindow?.focus();
        frame.contentWindow?.print();
      } catch {
        window.open(fileUrl, "_blank", "noopener");
      }
      window.setTimeout(() => frame.remove(), 60_000);
    };
    document.body.appendChild(frame);
  }, [fileUrl]);

  // ---- 渲染 -----------------------------------------------------------------

  const zoomPercent = Math.round(scale * 100);
  const controlsDisabled = numPages === null;

  const toolbar = (
    <div className="flex h-11 shrink-0 items-center gap-0.5 border-b border-border bg-surface/30 px-2">
      <ToolbarButton
        label={mergedLabels.toggleSidebar}
        active={railOpen}
        onClick={() => setRailOpen((open) => !open)}
      >
        <PanelLeft />
      </ToolbarButton>

      {fileName ? (
        <>
          <div className="mx-1 h-5 w-px shrink-0 bg-border" />
          <span className="max-w-[240px] shrink-0 truncate text-ui-base text-foreground">
            {fileName}
          </span>
        </>
      ) : null}

      <div className="mx-1 h-5 w-px shrink-0 bg-border" />

      <div className="flex shrink-0 items-center gap-1 text-ui-base text-foreground-subtle">
        <input
          value={pageInput}
          inputMode="numeric"
          disabled={controlsDisabled}
          aria-label={mergedLabels.pageInput}
          onChange={(event) => setPageInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              commitPageInput();
            }
          }}
          onBlur={commitPageInput}
          className="h-6 w-10 rounded-md border border-input-border bg-input px-1 text-center text-ui-base text-foreground outline-none transition-colors hover:border-input-border-hover focus-visible:border-input-border-focused disabled:pointer-events-none disabled:opacity-50"
        />
        <span>/ {numPages ?? "-"}</span>
      </div>

      <div className="mx-1 h-5 w-px shrink-0 bg-border" />

      <ToolbarButton
        label={mergedLabels.zoomOut}
        disabled={controlsDisabled || scale <= MIN_SCALE}
        onClick={() => zoomBy(-ZOOM_STEP)}
      >
        <Minus />
      </ToolbarButton>
      <span className="w-11 shrink-0 text-center text-ui-base tabular-nums text-foreground-subtle">
        {zoomPercent}%
      </span>
      <ToolbarButton
        label={mergedLabels.zoomIn}
        disabled={controlsDisabled || scale >= MAX_SCALE}
        onClick={() => zoomBy(ZOOM_STEP)}
      >
        <Plus />
      </ToolbarButton>

      <ToolbarButton label={mergedLabels.fitWidth} disabled={controlsDisabled} onClick={() => applyFitWidth()}>
        <Scaling />
      </ToolbarButton>

      <div className="flex-1" />

      {onDownload ? (
        <ToolbarButton label={mergedLabels.download} onClick={onDownload}>
          <Download />
        </ToolbarButton>
      ) : null}

      {fileUrl ? (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              title={mergedLabels.more}
              aria-label={mergedLabels.more}
              className="text-foreground-subtle hover:text-foreground"
            >
              <Ellipsis />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="min-w-[168px]">
            <DropdownMenuItem onSelect={handlePrint}>
              <Printer />
              {mergedLabels.print}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => window.open(fileUrl, "_blank", "noopener")}>
              <ExternalLink />
              {mergedLabels.openInNewTab}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      ) : null}
    </div>
  );

  return (
    <div className={cn("flex h-full min-h-0 flex-col bg-surface-hover", className)}>
      {toolbar}
      <div className="flex min-h-0 flex-1">
        {/* 缩略图栏的独立文档常驻挂载（关闭时只 display:none），避免每次开关都重新解析 PDF。 */}
        <Document
          file={railFile}
          options={DOCUMENT_OPTIONS}
          suspense={false}
          loading={null}
          error={null}
          noData={null}
          className={cn("w-[152px] shrink-0 flex-col", railOpen ? "flex" : "hidden")}
        >
          <div
            ref={setRailScrollEl}
            aria-label={mergedLabels.thumbnails}
            className="flex-1 overflow-y-auto border-r border-border bg-surface/30 px-2.5 py-3"
          >
            {pageNumbers.map((pageNumber) => (
              <ThumbnailItem
                key={pageNumber}
                pageNumber={pageNumber}
                active={pageNumber === currentPage}
                root={railScrollEl}
                onSelect={goToPage}
                registerItem={registerRailItem}
              />
            ))}
          </div>
        </Document>

        <Document
          file={file}
          options={DOCUMENT_OPTIONS}
          // react-pdf 默认 suspense=true，内部用 React 19 的 use() 读资源 promise；
          // 在本项目的 React 19.2.6 上该路径会一直挂起（补 Suspense 边界、传模块级稳定引用
          // 都无法解决），最终报 "An unknown Component is an async Client Component"。
          // 改用 react-pdf 自带的非 suspense 加载路径（effect + resolver），渲染结果一致。
          suspense={false}
          onLoadSuccess={handleLoadSuccess}
          onLoadError={onLoadError}
          loading={
            <div className="w-full p-3 text-ui-base text-foreground-subtle">
              {mergedLabels.loading}
            </div>
          }
          error={
            <div className="w-full p-3 text-ui-base text-destructive">{mergedLabels.loadError}</div>
          }
          noData={
            <div className="w-full p-3 text-ui-base text-foreground-subtle">
              {mergedLabels.noData}
            </div>
          }
          className="flex min-h-0 flex-1"
        >
          <div ref={setScrollEl} className="min-h-0 flex-1 overflow-auto">
            <div className="mx-auto w-max py-4" style={{ paddingInline: STAGE_PADDING_X / 2 }}>
              {pageNumbers.map((pageNumber) => (
                <PageSlot
                  key={pageNumber}
                  pageNumber={pageNumber}
                  scale={committedScale}
                  root={scrollEl}
                  size={pageSizes[pageNumber]}
                  labels={mergedLabels}
                  onMeasure={handleMeasure}
                  registerSlot={registerSlot}
                />
              ))}
            </div>
          </div>
        </Document>
      </div>
    </div>
  );
}

export default PdfViewer;
