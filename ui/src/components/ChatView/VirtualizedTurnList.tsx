import { useVirtualizer } from "@tanstack/react-virtual";
import type { ReactNode } from "react";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";

import {
  DEFAULT_ROW_HEIGHT_ESTIMATE_PX,
  TimelineRowHeightCache,
} from "@/lib/timelineRowHeightCache";

/** 视口外预渲染的行数。8 与 ZCode `ROW_OVERSCAN` 一致。 */
const ROW_OVERSCAN = 8;

/** tanstack 默认测量的竖向复刻：优先 ResizeObserver entry（不触发同步布局）。 */
function measureRowHeight(
  element: Element,
  entry: ResizeObserverEntry | undefined
): number {
  const boxSize = entry?.borderBoxSize?.[0];
  if (boxSize) {
    return Math.round(boxSize.blockSize);
  }
  return Math.round(element.getBoundingClientRect().height);
}

/** 虚拟行的位置信息，供外部（如导航轨）在不依赖 DOM 的情况下定位轮次。 */
export interface VirtualTurnPosition {
  /** 该行在 `items` 中的下标。 */
  index: number;
  /** 相对滚动内容顶部的起始偏移（px）。 */
  start: number;
  /** 行高（px）。 */
  size: number;
}

export interface VirtualizedTurnListProps<T> {
  /** 参与虚拟化的行（**不含**流式中的最后一轮）。 */
  items: readonly T[];
  /** 行的稳定 key；必须跨渲染稳定，否则测高缓存与滚动位置都会错乱。 */
  getItemKey: (item: T) => string;
  /** 渲染单行。行会被卸载 / 重挂，组件内 state 不保证保留。 */
  renderItem: (item: T) => ReactNode;
  /** 不参与虚拟化、常驻挂载的尾部内容（流式中的最后一轮）。 */
  liveTail?: ReactNode;
  /** 会话标识：切换时重置测高缓存（rowId 在不同会话间会重复）。 */
  sessionKey: string;
  /** 列表上方的非虚拟内容（「加载更早记录」等），其高度参与滚动偏移换算。 */
  headerSlot?: ReactNode;
  /**
   * 虚拟行位置变化的回调。
   *
   * 虚拟化后离屏行**没有 DOM**，导航轨不能再靠 `querySelectorAll` 量锚点位置，
   * 必须消费这里给出的 `start` / `size` 才能算出活跃项与跳转目标。
   */
  onVirtualItemsChange?: (
    positions: readonly VirtualTurnPosition[],
    totalSize: number
  ) => void;
  /**
   * 拿到「滚到第 N 行」的句柄。
   *
   * 虚拟列表外的组件（导航轨）不能自己 `scrollIntoView` —— 目标行可能没挂载，
   * 必须让 virtualizer 先把它纳入窗口再滚。
   */
  onReady?: (api: { scrollToIndex: (index: number) => void }) => void;
}

/**
 * 会话时间线的虚拟滚动容器。
 *
 * 移植自 ZCode `v4/ConversationTimeline.tsx` 的核心结构，按本项目条件简化：
 *
 * - **流式中的最后一轮不进虚拟列表**（`liveTail`）。这一轮高度持续增长，
 *   放进虚拟列表会让 virtualizer 每帧 remeasure + 调整滚动位置，是流式卡顿的主因。
 * - **动态测高 + 稳定 key 缓存**：行卸载重挂后仍用上次真实高度，避免滚动条跳动。
 * - 只负责「把 N 行里可见的几行摆到位」，滚动跟随 / 吸底仍由外层
 *   `use-stick-to-bottom` 负责 —— 两者通过同一个滚动容器协作。
 *
 * ⚠️ 必须挂在 `StickToBottom.Content` 的内容 div **内部**（它才是被
 * ResizeObserver 观测、并持有 `overflow:auto` 的元素），不要自己再套滚动容器。
 */
export function VirtualizedTurnList<T>({
  items,
  getItemKey,
  renderItem,
  liveTail,
  sessionKey,
  headerSlot,
  onVirtualItemsChange,
  onReady,
}: VirtualizedTurnListProps<T>) {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const heightCacheRef = useRef(new TimelineRowHeightCache());
  const itemsRef = useRef(items);
  itemsRef.current = items;

  /**
   * 滚动容器：`StickToBottom.Content` 的结构固定为「滚动 div > 内容 div」，
   * 本组件挂在内容 div 里，所以滚动 div 就是 `scrollRef` 的**祖父**。
   *
   * ⚠️ 不能用 `overflow-y` 样式来判定：滚动 div 的 `overflow: auto` 是
   * `StickToBottom` 在 layout effect 里**命令式**写上去的，本组件的 layout effect
   * 可能先于它执行，此刻读到的是 `visible`，于是错误地解析到内容 div ——
   * 视口高度等于内容高度，虚拟窗口退化成全量渲染。
   *
   * 结构约定由 `ai-elements/conversation.tsx` 保证，两边改动需同步。
   */
  const [scrollElement, setScrollElement] = useState<HTMLDivElement | null>(null);

  /**
   * `getScrollElement` 必须是**引用稳定**的函数：tanstack 用它判断是否要重新
   * 绑定滚动监听与 ResizeObserver。每次渲染新建箭头函数会让它反复重绑，
   * 测量结果始终停留在「首帧未就绪」，表现为虚拟化完全不生效（全量渲染）。
   */
  const scrollElementRef = useRef<HTMLDivElement | null>(null);
  const getScrollElement = useCallback(() => scrollElementRef.current, []);

  useLayoutEffect(() => {
    const contentDiv = scrollRef.current?.parentElement ?? null;
    const scrollDiv = contentDiv?.parentElement ?? null;
    const resolved = scrollDiv instanceof HTMLDivElement ? scrollDiv : null;
    scrollElementRef.current = resolved;
    setScrollElement(resolved);
  }, []);

  // 会话切换：测高缓存必须清空，否则新会话会沿用旧会话的行高。
  useEffect(() => {
    heightCacheRef.current.clear();
  }, [sessionKey]);

  /**
   * 带会话前缀的稳定行 key。
   *
   * 前缀不是装饰：tanstack 内部的 `itemSizeCache` 以 `getItemKey` 的返回值为索引，
   * 且没有公开的重置 API（只有内部 `itemSizeCacheVersion`）。行 key 由调用方提供，
   * 缺 `requestId` 的轮次会退化成空串 —— 跨会话直接命中上一条会话的行高，
   * 同会话内多行还会互相覆盖。加上 `sessionKey` 前缀后，跨会话天然不碰撞。
   */
  const getScopedItemKey = useCallback(
    (item: T) => `${sessionKey}\u0000${getItemKey(item)}`,
    [sessionKey, getItemKey]
  );

  const getItemKeyStable = useCallback(
    (index: number) => {
      const item = itemsRef.current[index];
      return item === undefined ? index : getScopedItemKey(item);
    },
    [getScopedItemKey]
  );

  // 测高缓存兜底：行卸载重挂时用上次真实测量代替固定估计。
  const estimateSize = useCallback(
    (index: number) => {
      const item = itemsRef.current[index];
      return heightCacheRef.current.estimate(
        item === undefined ? undefined : getScopedItemKey(item),
        DEFAULT_ROW_HEIGHT_ESTIMATE_PX
      );
    },
    [getScopedItemKey]
  );

  // 动态测高：virtualizer 对窗口内元素挂 ResizeObserver，行变高即回调此处；
  // 同时把真实高度写入以稳定 key 为索引的缓存。
  const measureElement = useCallback(
    (element: Element, entry: ResizeObserverEntry | undefined) => {
      const height = measureRowHeight(element, entry);
      const indexAttr = element.getAttribute("data-index");
      const item =
        indexAttr === null
          ? undefined
          : itemsRef.current[Number(indexAttr)];
      if (item !== undefined) {
        heightCacheRef.current.set(getScopedItemKey(item), height);
      }
      return height;
    },
    [getScopedItemKey]
  );

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement,
    estimateSize,
    overscan: ROW_OVERSCAN,
    getItemKey: getItemKeyStable,
    measureElement,
  });

  const virtualRows = virtualizer.getVirtualItems();
  const totalSize = virtualizer.getTotalSize();

  // 把虚拟行的位置上报给外部（导航轨）。用 ref 读回调，避免它每次渲染变化就重跑 effect。
  const onVirtualItemsChangeRef = useRef(onVirtualItemsChange);
  onVirtualItemsChangeRef.current = onVirtualItemsChange;
  useEffect(() => {
    if (!onVirtualItemsChangeRef.current) return;
    onVirtualItemsChangeRef.current(
      virtualRows.map((row) => ({
        index: row.index,
        start: row.start,
        size: row.size,
      })),
      totalSize
    );
  }, [virtualRows, totalSize]);

  // 对外暴露「滚到第 N 行」。用 ref 读回调，避免它变化就重跑 effect。
  const onReadyRef = useRef(onReady);
  onReadyRef.current = onReady;
  useEffect(() => {
    if (!onReadyRef.current) return;
    onReadyRef.current({
      scrollToIndex: (index: number) => {
        virtualizer.scrollToIndex(index, { align: "start" });
      },
    });
  }, [virtualizer]);

  /**
   * 还没拿到滚动容器时（首帧、SSR、无 DOM 环境）**全量渲染**。
   *
   * 虚拟窗口依赖真实滚动元素测量视口高度；拿不到就只能得到空列表 —— 那会让首屏
   * 与静态渲染直接白屏。全量渲染只是暂时多挂几行，下一帧拿到容器后自动收敛。
   */
  const virtualizable = scrollElement !== null;

  return (
    <div ref={scrollRef} data-virtualized-turns="true">
      {headerSlot ? (
        <div data-virtualized-header-slot="true">
          {headerSlot}
        </div>
      ) : null}

      {virtualizable ? (
        <div
          style={{
            height: totalSize,
            position: "relative",
            width: "100%"
          }}
        >
          {virtualRows.map((virtualRow) => {
            const item = items[virtualRow.index];
            if (item === undefined) return null;
            return (
              <div
                key={virtualRow.key}
                ref={virtualizer.measureElement}
                data-index={virtualRow.index}
                className="absolute left-0 top-0 w-full"
                style={{ transform: `translateY(${virtualRow.start}px)` }}
              >
                {renderItem(item)}
              </div>
            );
          })}
        </div>
      ) : (
        items.map((item) => (
          <div key={getItemKey(item)} data-virtualized-fallback="true">
            {renderItem(item)}
          </div>
        ))
      )}

      {/* 流式中的最后一轮：常驻挂载，不参与测高与虚拟窗口计算。 */}
      {liveTail}
    </div>
  );
}
