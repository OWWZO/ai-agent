/**
 * 基于 `@aiden0z/pptx-renderer` 的浏览器端预览引擎。
 *
 * 移植自 ZCode `packages/ui/src/presentation/pptxRendererPreviewEngine.ts`。
 * 负责：解析 zip → 构建演示文稿模型 → 按页渲染到 DOM，并暴露元素模型供选择模式使用。
 *
 * 与上游的差异：这里直接使用库导出的 `PresentationData` / `SlideData` 等真实类型，
 * 不再手写结构化子集 —— 1.3.x 起 `renderSlide` 的入参类型收紧，结构化子集无法再隐式匹配。
 */

import {
  RECOMMENDED_ZIP_LIMITS,
  buildTextIndex,
  buildPresentation,
  materializeSlideNodes,
  parseZipLazyMedia,
  renderSlide,
} from "@aiden0z/pptx-renderer/browser";
import type {
  PresentationData,
  ShapeNodeData,
  SlideData,
  SlideHandle,
  TableNodeData,
  TextBody,
} from "@aiden0z/pptx-renderer";
import type {
  PresentationPreviewDocument,
  PresentationPreviewEngine,
  PresentationPageElement,
  PresentationRenderHandle,
  PresentationRenderOptions,
} from "./types";
import { buildPresentationPageElements } from "./presentationElementModel";

function readTextBody(textBody: TextBody | undefined) {
  return textBody?.paragraphs
    .map((paragraph) => paragraph.runs.map((run) => run.text).join(""))
    .join("\n")
    .trim();
}

function toNodeInput(node: SlideData["nodes"][number]) {
  const base = {
    id: node.id,
    name: node.name,
    nodeType: node.nodeType,
    position: node.position,
    size: node.size,
  };
  if (node.nodeType === "shape") {
    return { ...base, text: readTextBody((node as ShapeNodeData).textBody) };
  }
  if (node.nodeType === "table") {
    const table = node as TableNodeData;
    return {
      ...base,
      columns: table.columns ?? [],
      rows: (table.rows ?? []).map((row) => ({
        height: row.height,
        cells: row.cells.map((cell) => ({
          gridSpan: cell.gridSpan,
          rowSpan: cell.rowSpan,
          hMerge: cell.hMerge,
          vMerge: cell.vMerge,
          text: readTextBody(cell.textBody),
        })),
      })),
    };
  }
  return base;
}

class PptxRendererPreviewDocument implements PresentationPreviewDocument {
  readonly pageCount: number;
  readonly pageSize: { width: number; height: number };

  private readonly activeHandles = new Set<PresentationRenderHandle>();
  private readonly elementCache = new Map<number, readonly PresentationPageElement[]>();
  private disposed = false;

  constructor(private readonly presentation: PresentationData) {
    this.pageCount = presentation.slides.length;
    this.pageSize = {
      width: presentation.width,
      height: presentation.height,
    };
  }

  getPageElements(pageIndex: number) {
    if (this.disposed) {
      throw new Error("Presentation preview document has been disposed");
    }
    const cached = this.elementCache.get(pageIndex);
    if (cached) {
      return cached;
    }
    const slide = this.presentation.slides[pageIndex];
    if (!slide) {
      throw new RangeError(`Presentation page index is out of range: ${pageIndex}`);
    }
    materializeSlideNodes(this.presentation, slide);
    // 只为当前页建立 group 文本索引，避免显式选择一页时提前 materialize 整份演示文稿。
    const groupTextIndex = buildTextIndex(
      { ...this.presentation, slides: [slide] },
      {
        includeGroups: true,
        includeShapes: true,
        includeTables: false,
      }
    );
    const directNodeIds = new Set(slide.nodes.map((node) => node.id));
    const elements = buildPresentationPageElements({
      slideIndex: pageIndex,
      slidePart: slide.slidePath,
      nodes: slide.nodes.map(toNodeInput),
      groupTextEntries: groupTextIndex
        .filter(
          (entry) =>
            entry.slideIndex === 0 &&
            entry.textKind === "shape" &&
            entry.nodePath.startsWith("slides/0/nodes/") &&
            !directNodeIds.has(entry.nodeId)
        )
        .map((entry) => ({
          nodeId: entry.nodeId,
          nodePath: entry.nodePath,
          text: entry.text,
          bounds: entry.bounds,
        })),
    });
    this.elementCache.set(pageIndex, elements);
    return elements;
  }

  renderPage(
    pageIndex: number,
    container: HTMLElement,
    options?: PresentationRenderOptions
  ): PresentationRenderHandle {
    if (this.disposed) {
      throw new Error("Presentation preview document has been disposed");
    }

    const slide = this.presentation.slides[pageIndex];
    if (!slide) {
      throw new RangeError(`Presentation page index is out of range: ${pageIndex}`);
    }

    const upstreamHandle: SlideHandle = renderSlide(this.presentation, slide, {
      onNavigate: options?.onNavigate
        ? (target: { slideIndex?: number; url?: string }) =>
            options.onNavigate?.({
              pageIndex: target.slideIndex,
              url: target.url,
            })
        : undefined,
      onNodeError: options?.onNodeError,
    });
    container.append(upstreamHandle.element);

    let handleDisposed = false;
    const handle: PresentationRenderHandle = {
      ready: upstreamHandle.ready,
      dispose: () => {
        if (handleDisposed) {
          return;
        }
        handleDisposed = true;
        upstreamHandle.dispose();
        upstreamHandle.element.remove();
        this.activeHandles.delete(handle);
      },
    };
    this.activeHandles.add(handle);
    return handle;
  }

  dispose(): void {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    for (const handle of this.activeHandles) {
      handle.dispose();
    }
    this.activeHandles.clear();
    this.elementCache.clear();
  }
}

export const pptxRendererPreviewEngine: PresentationPreviewEngine = {
  async open(data) {
    const files = await parseZipLazyMedia(data, RECOMMENDED_ZIP_LIMITS);
    const presentation = buildPresentation(files, { lazySlides: true });
    return new PptxRendererPreviewDocument(presentation);
  },
};
