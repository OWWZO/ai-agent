import React, { useEffect, useMemo, useRef, useState } from "react";
import { useRequest } from "ahooks";
import {
  Download,
  List,
  MoreHorizontal,
  Search,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import * as pdfjs from "pdfjs-dist";
import type { PDFDocumentProxy, PDFPageProxy } from "pdfjs-dist";
import pdfWorkerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";
import { downloadFile } from "@/utils";
import { normalizeFileUrlForBrowser } from "@/utils/fileUrl";
import pdfIcon from "@/assets/icon/pdf.png";
import Loading from "./Loading";
import DocumentFallback from "./DocumentFallback";
import { cn } from "@/lib/utils";

pdfjs.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

const LOADING_CLASS = "mr-32";
const MAX_AUTO_PAGES = 80;
const MIN_SCALE = 0.6;
const MAX_SCALE = 2.4;
const SCALE_STEP = 0.15;

interface PdfRendererProps {
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

const ToolbarIconBtn: React.FC<{
  title: string;
  onClick?: () => void;
  disabled?: boolean;
  children: React.ReactNode;
}> = ({ title, onClick, disabled, children }) => (
  <button
    type="button"
    title={title}
    disabled={disabled}
    onClick={onClick}
    className={cn(
      "inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-[#6b6b70] transition-colors",
      "hover:bg-black/[0.05] hover:text-[#1d1d1f]",
      "disabled:pointer-events-none disabled:opacity-35"
    )}
  >
    {children}
  </button>
);

const PdfPageView: React.FC<{
  page: PDFPageProxy;
  scale: number;
  pageNumber: number;
}> = ({ page, scale, pageNumber }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const textLayerRef = useRef<HTMLDivElement | null>(null);
  const [size, setSize] = useState({
    width: 0,
    height: 0,
  });

  useEffect(() => {
    let cancelled = false;
    const render = async () => {
      const viewport = page.getViewport({ scale });
      const canvas = canvasRef.current;
      const textLayerEl = textLayerRef.current;
      if (!canvas) return;

      const context = canvas.getContext("2d");
      if (!context) return;

      canvas.height = viewport.height;
      canvas.width = viewport.width;
      if (!cancelled) {
        setSize({
          width: viewport.width,
          height: viewport.height,
        });
      }

      await page.render({
        canvasContext: context,
        viewport,
        canvas,
      }).promise;

      if (cancelled || !textLayerEl) return;

      textLayerEl.innerHTML = "";
      textLayerEl.style.width = `${viewport.width}px`;
      textLayerEl.style.height = `${viewport.height}px`;
      textLayerEl.style.setProperty("--scale-factor", String(viewport.scale));

      try {
        const textContent = await page.getTextContent();
        if (typeof (pdfjs as any).TextLayer === "function") {
          const layer = new (pdfjs as any).TextLayer({
            textContentSource: textContent,
            container: textLayerEl,
            viewport,
          });
          await layer.render();
        } else if ((pdfjs as any).Util?.transform) {
          textContent.items.forEach((item: any) => {
            if (!item?.str) return;
            const span = document.createElement("span");
            span.textContent = item.str;
            span.style.position = "absolute";
            span.style.whiteSpace = "pre";
            span.style.color = "transparent";
            span.style.transformOrigin = "0% 0%";
            const tx = (pdfjs as any).Util.transform(
              viewport.transform,
              item.transform
            );
            const fontHeight = Math.hypot(tx[2], tx[3]);
            span.style.left = `${tx[4]}px`;
            span.style.top = `${tx[5] - fontHeight}px`;
            span.style.fontSize = `${fontHeight}px`;
            span.style.fontFamily = "sans-serif";
            textLayerEl.appendChild(span);
          });
        }
      } catch {
        // 文本层失败不影响 canvas 预览
      }
    };

    render().catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [page, scale]);

  return (
    <div
      className="relative mx-auto mb-4 overflow-hidden bg-white shadow-[0_1px_3px_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.04)]"
      style={{ width: size.width || undefined }}
      data-page={pageNumber}
    >
      <canvas ref={canvasRef} className="block max-w-full" />
      <div
        ref={textLayerRef}
        className={cn(
          "pointer-events-auto absolute left-0 top-0 overflow-hidden leading-none",
          "[&_span]:absolute [&_span]:cursor-text [&_span]:whitespace-pre [&_span]:text-transparent"
        )}
      />
    </div>
  );
};

const PdfToolbar: React.FC<{
  scale: number;
  setScale: React.Dispatch<React.SetStateAction<number>>;
  jumpPage: number;
  setJumpPage: (n: number) => void;
  pageCount: number;
  onJump: (page?: number) => void;
  showOutline: boolean;
  setShowOutline: (v: boolean) => void;
  showSearch: boolean;
  setShowSearch: (v: boolean) => void;
  searchQuery: string;
  setSearchQuery: (v: string) => void;
}> = ({
  scale,
  setScale,
  jumpPage,
  setJumpPage,
  pageCount,
  onJump,
  showOutline,
  setShowOutline,
  showSearch,
  setShowSearch,
  searchQuery,
  setSearchQuery,
}) => (
  <div className="flex shrink-0 flex-col border-b border-[#ececef] bg-white">
    <div className="flex h-10 items-center gap-0.5 px-2">
      <ToolbarIconBtn
        title={showOutline ? "隐藏页码列表" : "页码列表"}
        onClick={() => setShowOutline(!showOutline)}
      >
        <List className="h-3.5 w-3.5" />
      </ToolbarIconBtn>
      <ToolbarIconBtn title="更多">
        <MoreHorizontal className="h-3.5 w-3.5" />
      </ToolbarIconBtn>

      <div className="mx-1 h-4 w-px shrink-0 bg-[#e5e5ea]" />

      <ToolbarIconBtn
        title="缩小"
        disabled={scale <= MIN_SCALE}
        onClick={() =>
          setScale((s) =>
            Math.max(MIN_SCALE, Number((s - SCALE_STEP).toFixed(2)))
          )
        }
      >
        <ZoomOut className="h-3.5 w-3.5" />
      </ToolbarIconBtn>
      <ToolbarIconBtn
        title="放大"
        disabled={scale >= MAX_SCALE}
        onClick={() =>
          setScale((s) =>
            Math.min(MAX_SCALE, Number((s + SCALE_STEP).toFixed(2)))
          )
        }
      >
        <ZoomIn className="h-3.5 w-3.5" />
      </ToolbarIconBtn>

      <form
        className="mx-1 flex h-7 items-center rounded-md border border-[#e5e5ea] bg-[#fafafa] px-2"
        onSubmit={(e) => {
          e.preventDefault();
          onJump();
        }}
      >
        <input
          type="text"
          inputMode="numeric"
          value={String(jumpPage)}
          onChange={(e) => {
            const raw = e.target.value.replace(/[^\d]/g, "");
            if (!raw) {
              setJumpPage(1);
              return;
            }
            setJumpPage(Math.min(Math.max(Number(raw), 1), pageCount || 1));
          }}
          onBlur={() => onJump()}
          className="w-6 bg-transparent text-center text-[12.5px] font-medium tabular-nums text-[#1d1d1f] outline-none"
          aria-label="页码"
        />
        <span className="mx-1 text-[12.5px] text-[#aeaeb2]">/</span>
        <span className="min-w-[1.25rem] text-center text-[12.5px] tabular-nums text-[#6b6b70]">
          {pageCount || "?"}
        </span>
      </form>

      <ToolbarIconBtn title="更多">
        <MoreHorizontal className="h-3.5 w-3.5" />
      </ToolbarIconBtn>

      <div className="flex-1" />

      <ToolbarIconBtn
        title={showSearch ? "关闭搜索" : "搜索"}
        onClick={() => setShowSearch(!showSearch)}
      >
        <Search className="h-3.5 w-3.5" />
      </ToolbarIconBtn>
      <ToolbarIconBtn title="更多">
        <MoreHorizontal className="h-3.5 w-3.5" />
      </ToolbarIconBtn>
    </div>

    {showSearch ? (
      <div className="flex items-center gap-2 border-t border-[#f0f0f2] px-3 py-1.5">
        <Search className="h-3.5 w-3.5 shrink-0 text-[#aeaeb2]" />
        <input
          autoFocus
          type="search"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="在文档中查找…"
          className="h-7 min-w-0 flex-1 bg-transparent text-[12.5px] text-[#1d1d1f] outline-none placeholder:text-[#aeaeb2]"
        />
      </div>
    ) : null}
  </div>
);

const PdfRenderer: ReactorType.FC<PdfRendererProps> = React.memo((props) => {
  const {
    fileUrl,
    fileName,
    downloadUrl,
    missingReason,
    hideChrome = false,
    className,
  } = props;
  const [scale, setScale] = useState(1.15);
  const [pages, setPages] = useState<PDFPageProxy[]>([]);
  const [pageCount, setPageCount] = useState(0);
  const [jumpPage, setJumpPage] = useState(1);
  const [showOutline, setShowOutline] = useState(false);
  const [showSearch, setShowSearch] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const resolvedUrl = useMemo(
    () => normalizeFileUrlForBrowser(fileUrl || ""),
    [fileUrl]
  );
  const resolvedDownload = useMemo(
    () =>
      normalizeFileUrlForBrowser(downloadUrl || fileUrl || "") || resolvedUrl,
    [downloadUrl, fileUrl, resolvedUrl]
  );

  const displayName = fileName || "document.pdf";

  const { data: pdfDoc, loading, error } = useRequest(
    async () => {
      if (missingReason) throw new Error(missingReason);
      if (!resolvedUrl) throw new Error("引用资源不存在或已失效");
      const response = await fetch(resolvedUrl);
      if (!response.ok) throw new Error("Network response was not ok");
      const buffer = await response.arrayBuffer();
      return (await pdfjs.getDocument({ data: buffer })
        .promise) as PDFDocumentProxy;
    },
    {
      refreshDeps: [resolvedUrl, missingReason],
      onSuccess: async (doc) => {
        setPageCount(doc.numPages);
        setJumpPage(1);
        const limit = Math.min(doc.numPages, MAX_AUTO_PAGES);
        const loaded: PDFPageProxy[] = [];
        for (let i = 1; i <= limit; i += 1) {
          loaded.push(await doc.getPage(i));
        }
        setPages(loaded);
      },
    }
  );

  const scrollToPage = (pageNum: number) => {
    const safe = Math.min(Math.max(pageNum, 1), pageCount || 1);
    setJumpPage(safe);
    const root = scrollRef.current;
    if (!root) return;
    root.querySelector(`[data-page="${safe}"]`)?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  // 滚动时同步当前页码
  useEffect(() => {
    const root = scrollRef.current;
    if (!root || !pageCount) return;

    const onScroll = () => {
      const nodes = root.querySelectorAll<HTMLElement>("[data-page]");
      if (!nodes.length) return;
      const rootTop = root.getBoundingClientRect().top;
      let current = 1;
      nodes.forEach((node) => {
        const rect = node.getBoundingClientRect();
        if (rect.top - rootTop <= 48) {
          current = Number(node.dataset.page) || current;
        }
      });
      setJumpPage(current);
    };

    root.addEventListener("scroll", onScroll, { passive: true });
    return () => root.removeEventListener("scroll", onScroll);
  }, [pageCount, pages.length]);

  const shellClass = cn(
    "relative flex h-full w-full min-h-0 flex-col overflow-hidden bg-white",
    !hideChrome &&
      "rounded-xl border border-black/[0.04] shadow-[0_1px_2px_rgba(0,0,0,0.03)]",
    className
  );

  if (loading) {
    return (
      <div className={shellClass}>
        {!hideChrome ? (
          <div className="flex shrink-0 items-center gap-2.5 border-b border-[#f0f0f2] px-3 py-2.5">
            <img src={pdfIcon} alt="" className="h-8 w-8 shrink-0 rounded-md" />
            <div className="min-w-0">
              <div className="truncate text-[13.5px] font-semibold tracking-[-0.01em] leading-tight text-[#1d1d1f]">
                {displayName}
              </div>
              <div className="mt-px truncate text-[12px] leading-snug text-[#86868b]">
                PDF · PDF
              </div>
            </div>
          </div>
        ) : null}
        <Loading className={LOADING_CLASS} />
      </div>
    );
  }

  if (error || !pdfDoc) {
    return (
      <DocumentFallback
        label="PDF"
        title="PDF 不可预览"
        description={resolveFetchError(error)}
        fileName={fileName}
        downloadUrl={resolvedDownload}
        className={className}
        type="error"
      />
    );
  }

  const toolbar = (
    <PdfToolbar
      scale={scale}
      setScale={setScale}
      jumpPage={jumpPage}
      setJumpPage={setJumpPage}
      pageCount={pageCount}
      onJump={(page) => scrollToPage(page ?? jumpPage)}
      showOutline={showOutline}
      setShowOutline={setShowOutline}
      showSearch={showSearch}
      setShowSearch={setShowSearch}
      searchQuery={searchQuery}
      setSearchQuery={setSearchQuery}
    />
  );

  return (
    <div className={shellClass}>
      {!hideChrome ? (
        <div className="flex shrink-0 items-center gap-2.5 border-b border-[#f0f0f2] px-3 py-2.5">
          <img src={pdfIcon} alt="" className="h-8 w-8 shrink-0 rounded-md" />
          <div className="min-w-0 flex-1">
            <div className="truncate text-[13.5px] font-semibold tracking-[-0.01em] leading-tight text-[#1d1d1f]">
              {displayName}
            </div>
            <div className="mt-px truncate text-[12px] leading-snug text-[#86868b]">
              PDF · PDF
            </div>
          </div>
          {resolvedDownload ? (
            <button
              type="button"
              title="下载"
              onClick={() => downloadFile(resolvedDownload, fileName)}
              className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[#86868b] transition-colors hover:bg-black/[0.05] hover:text-[#1d1d1f]"
            >
              <Download className="h-3.5 w-3.5" />
            </button>
          ) : null}
        </div>
      ) : null}

      {toolbar}

      <div className="relative flex min-h-0 flex-1 overflow-hidden">
        {showOutline ? (
          <div className="flex w-[88px] shrink-0 flex-col overflow-auto border-r border-[#f0f0f2] bg-[#fafafa] py-2">
            {Array.from({ length: pageCount }, (_, i) => i + 1).map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => {
                  scrollToPage(n);
                }}
                className={cn(
                  "mx-1.5 mb-1 rounded-md px-2 py-1.5 text-center text-[12px] tabular-nums transition-colors",
                  jumpPage === n
                    ? "bg-white font-medium text-[#1d1d1f] shadow-[0_0_0_1px_rgba(0,0,0,0.06)]"
                    : "text-[#6b6b70] hover:bg-white/80 hover:text-[#1d1d1f]"
                )}
              >
                {n}
              </button>
            ))}
          </div>
        ) : null}

        <div
          ref={scrollRef}
          className="min-h-0 flex-1 overflow-auto bg-[#f5f5f7] px-4 py-4 sm:px-6 sm:py-5"
        >
          {pages.map((page, index) => (
            <PdfPageView
              key={index + 1}
              page={page}
              scale={scale}
              pageNumber={index + 1}
            />
          ))}
          {pageCount > MAX_AUTO_PAGES ? (
            <p className="py-3 text-center text-[12px] text-[#86868b]">
              已连续渲染前 {MAX_AUTO_PAGES} 页（共 {pageCount} 页），完整内容请下载原件。
            </p>
          ) : null}
        </div>
      </div>
    </div>
  );
});

PdfRenderer.displayName = "PdfRenderer";

export default PdfRenderer;
