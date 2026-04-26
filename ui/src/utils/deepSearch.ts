import type { DeepSearchCardItem, DeepSearchPreviewModel } from "@/types/deepSearch";

export type DeepSearchStage = "extend" | "search" | "report";

const DEEP_SEARCH_STAGES: DeepSearchStage[] = ["extend", "search", "report"];

export function isDeepSearchStage(value: unknown): value is DeepSearchStage {
  return typeof value === "string" && DEEP_SEARCH_STAGES.includes(value as DeepSearchStage);
}

export function resolveDeepSearchStage(
  stage: unknown,
  fallbackStage?: unknown
): DeepSearchStage {
  if (isDeepSearchStage(stage)) {
    return stage;
  }
  if (isDeepSearchStage(fallbackStage)) {
    return fallbackStage;
  }
  return "search";
}

export function normalizeDeepSearchQueries(value: unknown): string[] {
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
    return queryText || "深度搜索";
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
  return normalizedStage === "extend" || normalizedStage === "search";
}

export function shouldRenderDeepSearchWorkspace(stage: unknown): boolean {
  const normalizedStage = resolveDeepSearchStage(stage);
  return normalizedStage === "search" || normalizedStage === "report";
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

export function buildDeepSearchPreviewModel(
  task: Pick<CHAT.Task, "messageType" | "resultMap">
): DeepSearchPreviewModel | undefined {
  if (task.messageType !== "deep_search") {
    return undefined;
  }

  const stage = resolveDeepSearchStage(task.resultMap?.messageType);
  if (stage !== "extend" && stage !== "search") {
    return undefined;
  }

  const query =
    formatDeepSearchQueryText(task.resultMap?.searchResult?.query) ||
    "未命名搜索方向";

  if (stage === "extend") {
    return {
      stage,
      query,
      statusLabel: "正在搜索",
      description: "已完成查询分解，正在检索这个搜索方向。",
      loading: true,
      interactive: false,
      resultCount: 0,
    };
  }

  const resultItems = buildDeepSearchResultItems(task.resultMap?.searchResult?.docs);
  const resultCount = resultItems.length;

  return {
    stage,
    query,
    statusLabel: "搜索完成",
    description: resultCount
      ? `${formatCountLabel(resultCount, "条来源")}，点击查看右侧详情。`
      : "暂无来源，点击查看右侧结果面板。",
    loading: false,
    interactive: true,
    resultCount,
  };
}
