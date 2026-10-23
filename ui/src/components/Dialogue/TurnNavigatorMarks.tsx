import { useState, type FC } from "react";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import { resolveBarVisualState } from "@/lib/conversationNavigator";
import { cn } from "@/lib/utils";

export type NavigatorItem = {
  key: string;
  requestId?: string;
  userPreview: string;
  assistantPreview: string;
  isRunning: boolean;
};

/**
 * 刻度条 + 悬停列表。
 *
 * 从 `ConversationTurnNavigator` 提出：这部分是纯展示，只依赖 items 与当前下标，
 * 不参与宽度测量、滚动同步与跳转实现 —— 留在原文件里会把「测量 / 定位 / 渲染」
 * 三种职责混在一个组件中。
 *
 * 悬停焦点（`interactionIndex`）由本组件自持：它只影响刻度视觉，且生命周期
 * 完全绑定在 HoverCard 的开合上，提到外层反而要让父组件多管一份无关 state。
 */
export const TurnNavigatorMarks: FC<{
  items: readonly NavigatorItem[];
  activeIndex: number;
  onJump: (index: number) => void;
}> = ({ items, activeIndex, onJump }) => {
  const [interactionIndex, setInteractionIndex] = useState<number | undefined>(
    undefined
  );
  const focusIndex = interactionIndex ?? activeIndex;

  return (
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
                onClick={() => onJump(index)}
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
              onClick={() => onJump(index)}
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
  );
};
