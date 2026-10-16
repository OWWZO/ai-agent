/**
 * 工作区 tab 条的纯模型：搜索排序 + 相对时间。
 *
 * 与组件分离，便于单测；也避免「条上的图标/标题」与「标签页列表里的图标/标题」
 * 两处各写一份（显示解析统一在 `workspaceTabDisplay.tsx`）。
 */

/**
 * 标签页搜索的候选条目。
 *
 * 权重表对齐 ZCode `sidePaneTabSearch.ts:44-67`：
 * 标题完全匹配 > 词首 > 包含 > 副标题/路径 > 类型词。
 * 之所以要「类型词」这一档：用户常按「补丁」「子智能体」这类类别找 tab，
 * 而不是记得文件名。
 */
export type TabSearchEntry = {
  key: string;
  title: string;
  /** 副标题 / 路径（权重 40） */
  hint?: string;
  /** 类型词（权重 20）：文件扩展名、「补丁」「子智能体」「目录」… */
  typeHint?: string;
};

const SCORE_TITLE_EXACT = 120;
const SCORE_TITLE_PREFIX = 90;
const SCORE_TITLE_CONTAINS = 70;
const SCORE_HINT_CONTAINS = 40;
const SCORE_TYPE_CONTAINS = 20;

/** 单条候选的匹配得分；0 表示不匹配（会被过滤掉）。 */
export function scoreTabSearch(entry: TabSearchEntry, query: string): number {
  const q = query.trim().toLowerCase();
  if (!q) {
    return 0;
  }
  const title = (entry.title || "").toLowerCase();
  if (title) {
    if (title === q) return SCORE_TITLE_EXACT;
    if (title.startsWith(q)) return SCORE_TITLE_PREFIX;
    if (title.includes(q)) return SCORE_TITLE_CONTAINS;
  }
  if ((entry.hint || "").toLowerCase().includes(q)) {
    return SCORE_HINT_CONTAINS;
  }
  if ((entry.typeHint || "").toLowerCase().includes(q)) {
    return SCORE_TYPE_CONTAINS;
  }
  return 0;
}

/**
 * 过滤 + 排序。
 *
 * 空查询**原样返回**（保持打开顺序）—— 列表首先是「有哪些 tab 开着」的概览，
 * 不应该一打开就被重排。同分保持原序（stable），避免每次输入时行位置乱跳。
 */
export function rankTabSearch<T extends TabSearchEntry>(
  entries: T[],
  query: string
): T[] {
  const q = query.trim();
  if (!q) {
    return entries;
  }
  return entries
    .map((entry, index) => ({
      entry,
      index,
      score: scoreTabSearch(entry, q),
    }))
    .filter((item) => item.score > 0)
    .sort((left, right) => right.score - left.score || left.index - right.index)
    .map((item) => item.entry);
}

/**
 * 相对时间四档（对齐 ZCode `taskListItemPresentation.ts:33-53` 与 zh-CN 文案）：
 * <1 分「刚刚」、<1 时「X 分」、<1 天「X 小时」、其余「X 天」。
 * 时间戳缺失时返回空串，调用方据此不渲染这一段。
 */
export function formatTabRelativeTime(
  timestampMs: number | undefined,
  now: number
): string {
  if (timestampMs == null || !Number.isFinite(timestampMs)) {
    return "";
  }
  const diff = Math.max(0, now - timestampMs);
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) {
    return "刚刚";
  }
  if (minutes < 60) {
    return `${minutes} 分`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} 小时`;
  }
  return `${Math.floor(hours / 24)} 天`;
}
