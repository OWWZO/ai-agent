import type {
  DeepSearchCardItem,
  DeepSearchChapterWorkspaceModel,
  DeepSearchPreviewModel,
  DeepSearchStage,
} from "@/types/deepSearch";

const DEEP_SEARCH_STAGES: DeepSearchStage[] = [
  "extend",
  "search",
  "chapter_summary",
  "report",
];

export function isDeepSearchStage(value: unknown): value is DeepSearchStage {
  return typeof value === "string" && DEEP_SEARCH_STAGES.includes(value as DeepSearchStage);
}

export function resolveDeepSearchStage(
  stage: unknown,
  fallbackStage?: unknown
): DeepSearchStage {
  // 优先使用当前事件的 stage，再回退到兼容字段；最终固定为 search，避免未知值让 UI 卡在空状态。
  if (isDeepSearchStage(stage)) {
    return stage;
  }
  if (isDeepSearchStage(fallbackStage)) {
    return fallbackStage;
  }
  return "search";
}

export function normalizeDeepSearchQueries(value: unknown): string[] {
  // 后端可能返回单个字符串或数组，统一成去空白、去空项的数组供标题和卡片复用。
  const rawQueries = Array.isArray(value)
    ? value
    : value == null
      ? []
      : [value];

  return rawQueries
    .map((item) => String(item ?? "").trim())
    .filter(Boolean);
}

export function formatDeepSearchQueryText(
  value: unknown,
  separator = " / "
): string {
  return normalizeDeepSearchQueries(value).join(separator);
}

export function resolveDeepSearchActionText(
  stage: unknown,
  isFinal?: boolean
): string {
  const normalizedStage = resolveDeepSearchStage(stage);

  if (normalizedStage === "report") {
    return isFinal ? "总结完成" : "正在总结";
  }
  if (normalizedStage === "chapter_summary") {
    return "章节完成";
  }
  if (normalizedStage === "search") {
    return "搜索完成";
  }
  return "正在搜索";
}

export function resolveDeepSearchTitle(
  stage: unknown,
  queries: unknown
): string {
  const normalizedStage = resolveDeepSearchStage(stage);
  const queryText = formatDeepSearchQueryText(queries);

  if (normalizedStage === "report") {
    return queryText || "研究报告";
  }
  if (normalizedStage === "chapter_summary") {
    return queryText ? `章节：${queryText}` : "章节研究";
  }
  if (normalizedStage === "extend") {
    return queryText ? `搜索中：${queryText}` : "正在搜索";
  }
  if (normalizedStage === "search") {
    return queryText ? `检索：${queryText}` : "网页检索";
  }
  return queryText ? `深度搜索：${queryText}` : "深度搜索";
}

export function buildDeepSearchExtendMarkdown(queries: unknown): string {
  const normalizedQueries = normalizeDeepSearchQueries(queries);

  if (!normalizedQueries.length) {
    return "正在拆解搜索方向，请稍候查看检索结果。";
  }

  return [
    "## 正在搜索",
    "",
    "已完成查询分解，接下来会依次检索这些方向：",
    "",
    ...normalizedQueries.map((item) => `- ${item}`),
  ].join("\n");
}

export function shouldRenderDeepSearchPreview(stage: unknown): boolean {
  const normalizedStage = resolveDeepSearchStage(stage);
  return (
    normalizedStage === "extend" ||
    normalizedStage === "search" ||
    normalizedStage === "chapter_summary"
  );
}

export function shouldRenderDeepSearchWorkspace(stage: unknown): boolean {
  const normalizedStage = resolveDeepSearchStage(stage);
  return (
    normalizedStage === "search" ||
    normalizedStage === "chapter_summary"
  );
}

function formatCountLabel(count: number, unit: string): string {
  if (count <= 0) {
    return "";
  }
  return `${count} ${unit}`;
}

function normalizeDeepSearchDocs(value: unknown): MESSAGE.Doc[] {
  if (!Array.isArray(value)) {
    return [];
  }

  // 某些阶段按 query 分组返回二维数组，先展平再去重，避免 UI 绑定后端分组形态。
  return value.reduce<MESSAGE.Doc[]>((result, item) => {
    if (Array.isArray(item)) {
      item.forEach((doc) => {
        if (doc && typeof doc === "object") {
          result.push(doc as MESSAGE.Doc);
        }
      });
      return result;
    }

    if (item && typeof item === "object") {
      result.push(item as MESSAGE.Doc);
    }
    return result;
  }, []);
}

export function buildDeepSearchResultItems(value: unknown): DeepSearchCardItem[] {
  const docs = normalizeDeepSearchDocs(value);
  const seen = new Set<string>();

  // URL 和标题共同作为展示去重键；相同 URL 的不同标题仍保留，避免丢失来源上下文。
  return docs.reduce<DeepSearchCardItem[]>((result, doc, index) => {
    const url = String(doc.link || "").trim();
    const name = String(doc.title || doc.link || `搜索结果 ${index + 1}`).trim();
    const key = `${url}|${name}`;
    if (seen.has(key)) {
      return result;
    }
    seen.add(key);
    result.push({
      name,
      pageContent: String(doc.content || "").trim(),
      url,
      kind: "result",
      interactive: Boolean(url),
    });
    return result;
  }, []);
}

type DeepSearchResultMapLike = {
  messageType?: string;
  chapterSummary?: string;
  chapterTitle?: string;
  chapterContent?: string;
  chapterOrder?: number;
  chapterStreaming?: boolean;
  answer?: string;
  query?: string;
  isFinal?: boolean;
  searchResult?: {
    query?: string[];
    docs?: unknown;
  };
  chapters?: Record<string, MESSAGE.DeepSearchChapterState>;
};

export function resolveChapterSummary(resultMap?: DeepSearchResultMapLike | null): string {
  if (!resultMap) {
    return "";
  }
  return String(
    resultMap.chapterSummary ||
      (resolveDeepSearchStage(resultMap.messageType) === "chapter_summary"
        ? resultMap.answer
        : "") ||
      ""
  ).trim();
}

export function findChapterStateForQuery(
  resultMap: DeepSearchResultMapLike | undefined,
  query: string
): MESSAGE.DeepSearchChapterState | undefined {
  if (!resultMap?.chapters || !query) {
    return undefined;
  }
  const chapters = Object.values(resultMap.chapters);
  return chapters.find((chapter) => {
    if (!chapter) {
      return false;
    }
    if (chapter.chapterTitle === query) {
      return true;
    }
    return Array.isArray(chapter.queries) && chapter.queries.includes(query);
  });
}

export function buildDeepSearchPreviewModel(
  task: { messageType?: string; resultMap?: DeepSearchResultMapLike | null }
): DeepSearchPreviewModel | undefined {
  if (task.messageType !== "deep_search") {
    return undefined;
  }

  // extend/search/chapter_summary 生成轻量预览；report 不再渲染工作区报告。
  const stage = resolveDeepSearchStage(task.resultMap?.messageType);
  if (stage === "report") {
    return undefined;
  }

  const query =
    String(task.resultMap?.chapterTitle || "").trim() ||
    formatDeepSearchQueryText(task.resultMap?.searchResult?.query) ||
    "未命名搜索方向";
  const chapterSummary = resolveChapterSummary(task.resultMap);
  const resultItems = buildDeepSearchResultItems(task.resultMap?.searchResult?.docs);
  const resultCount = resultItems.length;
  const summaryStreaming = Boolean(task.resultMap?.chapterStreaming);

  if (stage === "extend" && !chapterSummary && resultCount === 0) {
    return {
      stage,
      query,
      statusLabel: "正在搜索",
      description: "已完成查询分解，正在检索这个搜索方向。",
      loading: true,
      interactive: false,
      resultCount: 0,
      hasSummary: false,
      summaryStreaming: false,
      sources: [],
    };
  }

  if (stage === "chapter_summary" || chapterSummary) {
    return {
      stage: chapterSummary ? "chapter_summary" : stage,
      query,
      statusLabel: summaryStreaming ? "正在总结章节" : "章节完成",
      description: resultCount
        ? `${formatCountLabel(resultCount, "条来源")}${summaryStreaming ? "，正在生成章节总结，点击查看右侧详情。" : "，含章节总结，点击查看右侧详情。"}`
        : summaryStreaming
          ? "章节总结生成中，点击查看右侧详情。"
          : "章节总结已生成，点击查看右侧详情。",
      loading: summaryStreaming,
      interactive: true,
      resultCount,
      hasSummary: true,
      summary: chapterSummary,
      summaryStreaming,
      sources: resultItems,
    };
  }

  return {
    stage: "search",
    query,
    statusLabel: "搜索完成",
    description: resultCount
      ? `${formatCountLabel(resultCount, "条来源")}，点击查看右侧详情。`
      : "暂无来源，点击查看右侧结果面板。",
    loading: false,
    interactive: true,
    resultCount,
    hasSummary: false,
    summaryStreaming: false,
    sources: resultItems,
  };
}

export function buildDeepSearchChapterWorkspaceModel(
  task: { messageType?: string; resultMap?: DeepSearchResultMapLike | null }
): DeepSearchChapterWorkspaceModel | undefined {
  if (task.messageType !== "deep_search") {
    return undefined;
  }
  const stage = resolveDeepSearchStage(task.resultMap?.messageType);
  if (stage === "report" || stage === "extend") {
    return undefined;
  }

  const summary = resolveChapterSummary(task.resultMap);
  const sources = buildDeepSearchResultItems(task.resultMap?.searchResult?.docs);
  if (!summary && sources.length === 0) {
    return undefined;
  }

  return {
    title:
      String(task.resultMap?.chapterTitle || "").trim() ||
      formatDeepSearchQueryText(task.resultMap?.searchResult?.query) ||
      "章节研究",
    content: String(task.resultMap?.chapterContent || "").trim() || undefined,
    summary,
    sources,
    order: task.resultMap?.chapterOrder,
    isStreaming: Boolean(task.resultMap?.chapterStreaming),
  };
}
