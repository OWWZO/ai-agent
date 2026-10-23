/**
 * 会话时间线的行高缓存。
 *
 * 移植自 ZCode `v4/timelineRowHeightCache.ts`（纯数据结构，无 DOM / React 依赖）。
 *
 * 为什么需要它：`@tanstack/react-virtual` 自身的测量缓存按 itemKey 存，行卸载重挂
 * 不会丢；但 virtualizer 重建（rows 数组整体替换、组件重挂、错误态切换）会把缓存
 * 清回 `estimateSize` 的固定值，长会话里表现为滚动条跳动与吸底抖动。
 * 这里以 turn 的稳定 key 为索引再存一层，保证「行卸载重挂仍保留真实高度」。
 */

/** 未测量行的兜底估计高度。 */
export const DEFAULT_ROW_HEIGHT_ESTIMATE_PX = 120;

/** 缓存上限：超长会话防内存膨胀；淘汰最久未写入的行。 */
const MAX_ROW_HEIGHT_CACHE_ENTRIES = 4000;

export type TimelineRowHeightCacheKey = string | number;

export class TimelineRowHeightCache {
  private readonly sizes = new Map<TimelineRowHeightCacheKey, number>();

  constructor(private readonly maxEntries: number = MAX_ROW_HEIGHT_CACHE_ENTRIES) {}

  get size(): number {
    return this.sizes.size;
  }

  /** 记录一次真实测量。重复写入会刷新淘汰顺序（活跃行不被淘汰）。 */
  set(key: TimelineRowHeightCacheKey, heightPx: number): void {
    if (!Number.isFinite(heightPx) || heightPx <= 0) {
      return;
    }
    // Map 迭代序 = 插入序；先删再插把该行移到「最新」端。
    this.sizes.delete(key);
    this.sizes.set(key, heightPx);
    while (this.sizes.size > this.maxEntries) {
      const oldest = this.sizes.keys().next();
      if (oldest.done) break;
      this.sizes.delete(oldest.value);
    }
  }

  get(key: TimelineRowHeightCacheKey): number | undefined {
    return this.sizes.get(key);
  }

  /** `estimateSize` 入口：有测量用测量，无测量回落估计值。 */
  estimate(
    key: TimelineRowHeightCacheKey | undefined,
    fallbackPx: number = DEFAULT_ROW_HEIGHT_ESTIMATE_PX
  ): number {
    if (key === undefined) return fallbackPx;
    return this.sizes.get(key) ?? fallbackPx;
  }

  /** 会话切换时整体重置（turnId 在不同 session 间不能假设全局唯一）。 */
  clear(): void {
    this.sizes.clear();
  }
}
