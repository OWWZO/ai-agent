import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FC,
} from "react";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import {
  buildNavigatorItems,
  resolveActiveItemIndex,
  resolveActiveItemIndexFromVirtualRows,
  resolveBarVisualState,
  type VirtualRowPosition,
} from "@/lib/conversationNavigator";
import { cn } from "@/lib/utils";

type ConversationTurnNavigatorProps = {
  chats: CHAT.ChatItem[];
  /**
   * 对话区宽度小于该值时不显示。
   *
   * ZCode 是 864px（容器查询 `@min-[864px]/conversation`），但它的对话列本来就宽；
   * 本项目双栏时对话区 = `(窗口宽 - 侧栏 280px - 外壳留白) × 50%`，实测只有 500-820px，
   * 取 864 / 720 都等于**永远不显示**（720 需要 ≥1720px 的窗口才够）。
   *
   * 480px 是实测标定的：窗口 1280→对话区 496（显示）、1366→539、1440→576、
   * 1600→656、1920→816；沉浸模式（左栏 24%）即使 1920 也只有 ~390px，会自动让位。
   * （`plans/030` §5 决策 3）
   */
  minWidthPx?: number;
  /** 跳转前解锁吸底：`StickToBottom` 会把视口拉回底部（风险 R1）。 */
  onBeforeJump?: () => void;
  /**
   * 虚拟列表上报的行位置。
   *
   * 传了就按它算活跃项与跳转目标 —— 虚拟化后离屏轮次没有 DOM，
   * 靠 `querySelectorAll` 量锚点会漏掉大部分轮次。不传则回退到 DOM 测量。
   */
  virtualRows?: readonly VirtualRowPosition[];
  /** 请求跳到第 N 轮（虚拟化模式下由 ChatView 转给虚拟列表）。 */
  onJumpToIndex?: (index: number) => void;
  className?: string;
};

const DEFAULT_MIN_WIDTH_PX = 480;

/** 锚点与对话区顶部的偏移（px），用于挑活跃项。 */
function collectAnchorOffsets(area: HTMLElement): number[] {
  const anchors = area.querySelectorAll<HTMLElement>("[data-user-message-anchor]");
  const areaTop = area.getBoundingClientRect().top;
  return Array.from(anchors).map(
    (anchor) => anchor.getBoundingClientRect().top - areaTop
  );
}

/**
 * 左侧用户消息快速导航轨。
 *
 * 移植自 ZCode `v4/ConversationTurnNavigator.tsx`，但把「虚拟列表 start/size」
 * 换成 DOM 测量——本项目消息是 `deferredChatList.map` 全量渲染，
 * 不需要引入 `@tanstack/react-virtual`（`plans/030` §3.2）。
 *
 * ⚠️ 该结论已作废：会话时间线现在走虚拟列表（离屏行没有 DOM），调用方必须传
 * `virtualRows` + `onJumpToIndex`；只有拿不到虚拟位置时才回退到 DOM 测量。
 */
const ConversationTurnNavigator: FC<ConversationTurnNavigatorProps> = ({
  chats,
  minWidthPx = DEFAULT_MIN_WIDTH_PX,
  onBeforeJump,
  virtualRows,
  onJumpToIndex,
  className,
}) => {
  const containerRef = useRef<HTMLElement | null>(null);
  const [visible, setVisible] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [interactionIndex, setInteractionIndex] = useState<number | undefined>(
    undefined
  );
  const items = useMemo(() => buildNavigatorItems(chats), [chats]);
  /** 会话历史是异步到达的：不足 2 条时 nav 仍要渲染（见下），只是不出刻度。 */
  const hasEnoughItems = items.length >= 2;

  // 显隐：按对话区实际宽度（双栏拖拽会改变它）。
  // ⚠️ 这里的 containerRef 依赖「nav 始终被渲染」—— 曾经把 items<2 早退成 null，
  // 导致会话历史异步到达后 effect 因 ref 为 null 而永远没跑过（visible 恒 false，
  // 用户看到的就是「导航不见了」）。nav 必须无条件渲染，宽度测量才能在首次提交后执行。
  useEffect(() => {
    const area = containerRef.current?.parentElement;
    if (!area) {
      return;
    }
    const sync = () => setVisible(area.clientWidth >= minWidthPx);
    sync();
    if (typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(sync);
    observer.observe(area);
    return () => observer.disconnect();
  }, [minWidthPx]);

  /**
   * 活跃项。
   *
   * 虚拟化模式（`virtualRows` 有值）：离屏行没有 DOM，只能按「行的 start/size
   * + 当前滚动偏移」算。DOM 测量模式：仍走锚点测量。
   */
  const virtualRowsRef = useRef(virtualRows);
  virtualRowsRef.current = virtualRows;

  useEffect(() => {
    const area = containerRef.current?.parentElement;
    if (!area || items.length === 0) {
      return;
    }

    const syncVirtual = () => {
      const rows = virtualRowsRef.current;
      // 滚动容器是 nav 父级（对话区）内的滚动 div，从这里取视口与偏移。
      const scrollArea =
        area.querySelector<HTMLElement>("[data-virtualized-turns]") ?? null;
      const scroller = scrollArea?.parentElement?.parentElement ?? area;
      const next = resolveActiveItemIndexFromVirtualRows(
        rows ?? [],
        scroller.scrollTop,
        scroller.clientHeight,
        // 流式最后一轮常驻在虚拟区之后，索引 = items.length - 1。
        { liveTailIndex: items.length - 1 }
      );
      if (next !== undefined) {
        setActiveIndex(next);
      }
    };

    const syncDom = () => {
      const next = resolveActiveItemIndex(collectAnchorOffsets(area));
      if (next !== undefined) {
        setActiveIndex(next);
      }
    };

    const sync = () => {
      if (virtualRowsRef.current && virtualRowsRef.current.length > 0) {
        syncVirtual();
        return;
      }
      // 首帧虚拟位置还没上报时先用 DOM 测量，避免导航短暂失效。
      syncDom();
    };

    /**
     * 合批到每帧一次。
     *
     * `sync` 内部要读 `scrollTop` / `clientHeight` / `getBoundingClientRect`
     * —— 这些都是**同步强制布局**。一个 scroll 事件序列在一帧里可能触发多次
     * （平滑滚动、惯性滚动尤甚），逐个同步读会让每帧付出多次强制布局的代价。
     *
     * 这里只在事件里登记「需要同步」，真正的读取放到下一帧的 rAF 里做一次。
     * 注意：仍然由 rAF 而非 `setTimeout` 驱动，所以读到的布局是最新的
     * （rAF 在布局提交之后、绘制之前执行），不存在读到上一帧旧值的问题。
     */
    let pendingFrame: number | null = null;
    const requestSync = () => {
      if (pendingFrame !== null) {
        return;
      }
      pendingFrame = requestAnimationFrame(() => {
        pendingFrame = null;
        sync();
      });
    };

    sync();
    // scroll 不冒泡到父级，必须 capture。
    area.addEventListener("scroll", requestSync, {
      passive: true,
      capture: true,
    });
    const observer =
      typeof ResizeObserver === "undefined" ? null : new ResizeObserver(requestSync);
    observer?.observe(area);
    return () => {
      if (pendingFrame !== null) {
        cancelAnimationFrame(pendingFrame);
      }
      area.removeEventListener("scroll", requestSync, { capture: true });
      observer?.disconnect();
    };
  }, [items.length]);

  const handleJump = useCallback(
    (index: number) => {
      // 先解锁吸底，否则跳转后 StickToBottom 可能立刻把视口拉回底部。
      onBeforeJump?.();
      setActiveIndex(index);

      // 虚拟化模式：目标轮次很可能没挂载，必须让虚拟列表滚动到那一行。
      if (onJumpToIndex) {
        onJumpToIndex(index);
        return;
      }

      const area = containerRef.current?.parentElement;
      const anchors = area?.querySelectorAll<HTMLElement>(
        "[data-user-message-anchor]"
      );
      const target = anchors?.[index];
      if (!target) {
        return;
      }
      const reduceMotion =
        typeof window !== "undefined" &&
        window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
      target.scrollIntoView({
        behavior: reduceMotion ? "auto" : "smooth",
        block: "start",
      });
    },
    [onBeforeJump, onJumpToIndex]
  );

  const focusIndex = interactionIndex ?? activeIndex;

  // nav 无条件渲染（ref 必须始终挂载，宽度测量才有效）；不足 2 条时不渲染刻度
  // 且强制隐藏 —— 消息异步到达后 hasEnoughItems 翻转，刻度自然出现。
  return (
    <nav
      ref={containerRef}
      aria-label="会话导航"
      data-testid="conversation-turn-navigator"
      className={cn(
        // 右边缘贴中线：紧贴 `reactor-workspace-resizer`（ZCode 对齐）。
        // `pointer-events-none` 让出聊天区点击，`pointer-events-auto` 在内部触发区域恢复。
        "pointer-events-none absolute inset-y-0 right-0 z-10 w-12 transition-opacity duration-200",
        hasEnoughItems && visible
          ? "visible opacity-100"
          : "invisible opacity-0",
        className
      )}
    >
      {hasEnoughItems ? (
        <HoverCard
          openDelay={150}
          closeDelay={120}
          onOpenChange={(open) =>
            setInteractionIndex(open ? activeIndex : undefined)
          }
        >
          <HoverCardTrigger asChild>
            <div className="pointer-events-auto absolute right-0 top-1/2 flex max-h-[calc(100%-6rem)] w-9 -translate-y-1/2 flex-col items-end justify-center gap-1.5 overflow-y-auto">
              {items.map((item, index) => {
                const state = resolveBarVisualState(index, focusIndex);
                return (
                  <button
                    key={item.key}
                    type="button"
                    className="flex h-2.5 w-9 shrink-0 items-center justify-end"
                    aria-label={`跳转到第 ${index + 1} 条消息`}
                    onClick={() => handleJump(index)}
                  >
                    <span
                      className={cn(
                        "block h-0.5 w-3 origin-right rounded-full transition-[transform,opacity,background-color] duration-150 ease-out motion-reduce:transition-none",
                        state.colorTone === "focus"
                          ? "bg-[var(--chat-text)]"
                          : "bg-[var(--chat-text-muted)]"
                      )}
                      style={{
                        opacity: state.opacity,
                        transform: `scaleX(${state.scaleX})`,
                      }}
                    />
                  </button>
                );
              })}
            </div>
          </HoverCardTrigger>
          {/*
          悬停弹出列表：所有 prompt 一屏可见，活跃项高亮。
          `side="left"` 让浮层出现在触发器左侧（深入聊天区），不挡住 resizer。
        */}
          <HoverCardContent
            side="left"
            align="center"
            sideOffset={12}
            className="w-96 max-h-[70vh] overflow-y-auto scrollbar-thin p-1.5"
          >
            {items.map((item, index) => {
              const isActive = index === activeIndex;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => handleJump(index)}
                  className={cn(
                    "group block w-full rounded-md px-3 py-2 text-left transition-colors",
                    isActive
                      ? "bg-[var(--color-info,#3b82f6)]/10"
                      : "hover:bg-[var(--chat-nav)]"
                  )}
                >
                  <div
                    className={cn(
                      "line-clamp-2 text-ui-sm",
                      isActive
                        ? "font-medium text-[var(--color-info,#3b82f6)]"
                        : "text-[var(--chat-text)]"
                    )}
                  >
                    {item.userPreview}
                  </div>
                  {item.assistantPreview ? (
                    <div
                      className={cn(
                        "mt-1 line-clamp-2 whitespace-pre-line text-ui-xs",
                        item.isRunning
                          ? "text-[var(--color-warning)]"
                          : isActive
                            ? "text-[var(--chat-text-muted)]"
                            : "text-[var(--chat-text-muted)]/80"
                      )}
                    >
                      {item.assistantPreview}
                    </div>
                  ) : null}
                </button>
              );
            })}
          </HoverCardContent>
        </HoverCard>
      ) : null}
    </nav>
  );
};

export default ConversationTurnNavigator;
