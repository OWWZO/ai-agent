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
  resolveBarVisualState,
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
 */
const ConversationTurnNavigator: FC<ConversationTurnNavigatorProps> = ({
  chats,
  minWidthPx = DEFAULT_MIN_WIDTH_PX,
  onBeforeJump,
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

  // 活跃项：DOM 测量 + 捕获子级滚动（scroll 不冒泡，必须 capture）
  useEffect(() => {
    const area = containerRef.current?.parentElement;
    if (!area || items.length === 0) {
      return;
    }
    const sync = () => {
      const next = resolveActiveItemIndex(collectAnchorOffsets(area));
      if (next !== undefined) {
        setActiveIndex(next);
      }
    };
    sync();
    area.addEventListener("scroll", sync, {
      passive: true,
      capture: true
    });
    const observer =
      typeof ResizeObserver === "undefined" ? null : new ResizeObserver(sync);
    observer?.observe(area);
    return () => {
      area.removeEventListener("scroll", sync, { capture: true });
      observer?.disconnect();
    };
  }, [items.length]);

  const handleJump = useCallback(
    (index: number) => {
      const area = containerRef.current?.parentElement;
      const anchors = area?.querySelectorAll<HTMLElement>(
        "[data-user-message-anchor]"
      );
      const target = anchors?.[index];
      if (!target) {
        return;
      }
      // 先解锁吸底，否则跳转后 StickToBottom 可能立刻把视口拉回底部。
      onBeforeJump?.();
      const reduceMotion =
        typeof window !== "undefined" &&
        window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
      target.scrollIntoView({
        behavior: reduceMotion ? "auto" : "smooth",
        block: "start",
      });
      setActiveIndex(index);
    },
    [onBeforeJump]
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
