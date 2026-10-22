import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import type { DocModel } from "@extend-ai/react-docx";
import { buildLegacyDocModel, loadLegacyDocModule, type LegacyDocModule } from "@/lib/lazy/legacyDocImport";
import {
  calculateDocxPreviewFit,
  isSameDocxPreviewFit,
  type DocxPreviewFit,
} from "@/lib/office/previewFit";
import { installDocumentLinkSafety } from "@/lib/office/documentLinkSafety";
import { cn } from "@/lib/utils";

interface LegacyDocCanvasProps {
  buffer: ArrayBuffer;
  fileName?: string;
  className?: string;
  onRenderError: (error: unknown) => void;
}

type EditorCanvasProps = {
  module: LegacyDocModule;
  model: DocModel;
  fileName?: string;
  zoomScale: number;
};

/**
 * 只读文档画布。
 *
 * 与 ZCode `previewPaneOfficeLegacyDocContent.tsx` 一致：不用轻量的 `ReactDocxViewer`
 * （它不走完整分页与排版链路，多页文档不会切成离散页面），而是用同一个 editor controller
 * 驱动文档画布并固定为只读，避免暴露任何编辑能力。
 *
 * 外层用 transform 缩放页面，但 react-docx 的虚拟列表不识别 transform，
 * 必须同步传入相同 zoomScale，否则滚动坐标按未缩放页高计算，会出现闪白或末页不挂载。
 */
const EditorCanvas: CuteType.FC<EditorCanvasProps> = (props) => {
  const { module: mod, model, fileName, zoomScale } = props;
  const editor = mod.useDocxEditor({
    starterModel: model,
    initialFileName: fileName,
    initialDocumentTheme: "light",
  });

  return (
    <mod.DocxEditorViewer
      className="min-w-max"
      editor={editor}
      mode="read-only"
      pageGapBackgroundColor="transparent"
      pageVirtualization={{ zoomScale }}
    />
  );
};

/**
 * 旧版 `.doc` 画布：用 react-docx 的排版引擎渲染，外层复用与 `.docx` 相同的适应宽度结构。
 *
 * 引擎与 `DocxCanvas`（docx-preview）不同 —— ZCode 的 `.docx` 与 `.doc` 本来也是两个引擎。
 * 不把 `.doc` 转成 `.docx` 字节再交给 docx-preview：转换会丢分页，多页文档只剩第 1 页
 * 且被 `overflow: hidden` 裁掉（详见 `@/lib/lazy/legacyDocImport`）。
 */
const LegacyDocCanvas: CuteType.FC<LegacyDocCanvasProps> = (props) => {
  const { buffer, fileName, className, onRenderError } = props;

  const viewportRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const hostRef = useRef<HTMLDivElement | null>(null);
  const [fit, setFit] = useState<DocxPreviewFit | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [loaded, setLoaded] = useState<{ module: LegacyDocModule; model: DocModel } | null>(null);
  const onRenderErrorRef = useRef(onRenderError);
  onRenderErrorRef.current = onRenderError;

  const updateFit = useCallback(() => {
    const viewport = viewportRef.current;
    const content = contentRef.current;
    if (!viewport || !content || state !== "ready") {
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
  }, [state]);

  // 加载引擎 + 解析模型。传 model（而非 file）可完全跳过库内部的 worker 解析路径。
  useEffect(() => {
    let active = true;
    setState("loading");
    setFit(null);
    setLoaded(null);

    void (async () => {
      try {
        const [mod, docModel] = await Promise.all([
          loadLegacyDocModule(),
          buildLegacyDocModel(buffer),
        ]);
        if (!active) return;
        setLoaded({ module: mod, model: docModel });
        setState("ready");
      } catch (error) {
        if (!active) return;
        console.error("[LegacyDocCanvas] 旧版 Word 解析失败", error);
        setState("error");
        onRenderErrorRef.current(error);
      }
    })();

    return () => {
      active = false;
    };
  }, [buffer]);

  // 文档内超链接来自不受信任的来源，挂载后统一净化并阻断内部导航。
  useEffect(() => {
    const host = hostRef.current;
    if (!host || state !== "ready") return;
    return installDocumentLinkSafety(host);
  }, [state]);

  useLayoutEffect(() => {
    if (state !== "ready") return;
    updateFit();
    if (typeof ResizeObserver === "undefined") return;
    const resizeObserver = new ResizeObserver(updateFit);
    if (viewportRef.current) resizeObserver.observe(viewportRef.current);
    if (contentRef.current) resizeObserver.observe(contentRef.current);
    return () => resizeObserver.disconnect();
  }, [state, updateFit]);

  if (state === "error") {
    return null;
  }

  return (
    <div
      aria-busy={state === "loading" ? "true" : undefined}
      className={cn(
        "relative h-full min-h-0 w-full min-w-0 bg-[var(--chat-surface-muted)]",
        className
      )}
      data-office-preview-kind="doc"
    >
      <div className="h-full min-h-0 w-full min-w-0 overflow-auto p-3 sm:p-4">
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
              <div ref={hostRef} className="theme-zai-light">
                {loaded ? (
                  <EditorCanvas
                    module={loaded.module}
                    model={loaded.model}
                    fileName={fileName}
                    zoomScale={fit?.scale ?? 1}
                  />
                ) : (
                  <div className="min-h-[300px] w-[794px]" />
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {state === "loading" ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <span className="text-xs text-muted-foreground">正在解析旧版 Word 文档…</span>
        </div>
      ) : null}
    </div>
  );
};

export default LegacyDocCanvas;
