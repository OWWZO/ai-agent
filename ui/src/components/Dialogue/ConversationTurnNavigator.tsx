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
   * 对话区宽度小于该值时不显示。ZCode 是 864px，但本项目双栏下对话区
   * 常被挤到 500-700px，取 720px 起步（`plans/030` §5 决策 3）。
   */
  minWidthPx?: number;
  /** 跳转前解锁吸底：`StickToBottom` 会把视口拉回底部（风险 R1）。 */
  onBeforeJump?: () => void;
  className?: string;
};

const DEFAULT_MIN_WIDTH_PX = 720;

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

  // 显隐：按对话区实际宽度（双栏拖拽会改变它）
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
    area.addEventListener("scroll", sync, { passive: true, capture: true });
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

  // 只有一条消息时导航没有意义
  if (items.length < 2) {
    return null;
  }

  const focusIndex = interactionIndex ?? activeIndex;

  return (
    <nav
      ref={containerRef}
      aria-label="会话导航"
      data-testid="conversation-turn-navigator"
      className={cn(
        "pointer-events-none absolute inset-y-0 left-0 z-10 w-12 transition-opacity duration-200",
        visible ? "visible opacity-100" : "invisible opacity-0",
        className
      )}
    >
      <div className="pointer-events-auto absolute left-3 top-1/2 flex max-h-[calc(100%-6rem)] w-9 -translate-y-1/2 flex-col items-start gap-1.5 overflow-y-auto">
        {items.map((item, index) => {
          const state = resolveBarVisualState(index, focusIndex);
          return (
            <HoverCard
              key={item.key}
              openDelay={120}
              closeDelay={80}
              onOpenChange={(open) =>
                setInteractionIndex((current) =>
                  open ? index : current === index ? undefined : current
                )
              }
            >
              <HoverCardTrigger asChild>
                <button
                  type="button"
                  className="flex h-2.5 w-9 shrink-0 items-center"
                  aria-label={`跳转到第 ${index + 1} 条消息`}
                  onClick={() => handleJump(index)}
                >
                  <span
                    className={cn(
                      "block h-0.5 w-3 origin-left rounded-full transition-[transform,opacity,background-color] duration-150 ease-out motion-reduce:transition-none",
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
              </HoverCardTrigger>
              <HoverCardContent
                side="right"
                align="start"
                sideOffset={8}
                className="w-80 p-3"
              >
                <div className="line-clamp-2 text-ui-sm font-medium text-[var(--chat-text)]">
                  {item.userPreview}
                </div>
                <div
                  className={cn(
                    "mt-2 line-clamp-3 whitespace-pre-line text-ui-xs",
                    item.isRunning
                      ? "text-[var(--color-warning)]"
                      : "text-[var(--chat-text-muted)]"
                  )}
                >
                  {item.assistantPreview}
                </div>
              </HoverCardContent>
            </HoverCard>
          );
        })}
      </div>
    </nav>
  );
};

export default ConversationTurnNavigator;
