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

export function buildDeepSearchPreviewModel(
  task: Pick<CHAT.Task, "messageType" | "resultMap">
): DeepSearchPreviewModel | undefined {
  if (task.messageType !== "deep_search") {
    return undefined;
  }

  // extend/search 只生成轻量预览，report 交给工作区完整结果渲染，防止同一任务出现两套主展示。
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
