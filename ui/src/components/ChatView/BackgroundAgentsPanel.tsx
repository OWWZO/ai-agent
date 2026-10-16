import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BotIcon, EllipsisIcon, Minimize2Icon } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { findAgentTaskByToolCallId } from "@/utils/chat/taskIdentity";
import { projectDockTasks } from "@/utils/chat/agentRuntimeProjector";
import type { DockTaskItem } from "@/types/agentRuntime";
import {
  AGENTS_PANEL_VARIANT_LABELS,
  formatBackgroundAgentElapsed,
  type AgentsPanelVariant,
} from "./backgroundAgentsModel";

/** 运行中条目每秒走时的时钟；无运行中任务时不建 interval */
function useNowTicker(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) {
      return;
    }
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [active]);
  return now;
}

/** 视口 ≥1280px 才允许 auto 展开（与 ZCode `@min-[1280px]/conversation` 同阈值） */
function useIsWideViewport(): boolean {
  const [isWide, setIsWide] = useState(() =>
    typeof window === "undefined"
      ? true
      : window.matchMedia("(min-width: 1280px)").matches
  );
  useEffect(() => {
    const mql = window.matchMedia("(min-width: 1280px)");
    const onChange = (event: MediaQueryListEvent) => setIsWide(event.matches);
    mql.addEventListener("change", onChange);
    return () => mql.removeEventListener("change", onChange);
  }, []);
  return isWide;
}

type RowItem = DockTaskItem & {
  /** 条目对应的真实会话（跨 chatList 去重后保留宿主，点击下钻用） */
  hostChat: CHAT.ChatItem;
};

function collectAgentItems(
  chats: CHAT.ChatItem[],
  activeChat?: CHAT.ChatItem
): RowItem[] {
  const host =
    activeChat ??
    (chats.length ? chats[chats.length - 1] : undefined);
  const sourceChats = host ? [host, ...chats.filter((item) => item !== host)] : chats;
  const seen = new Set<string>();
  const items: RowItem[] = [];
  for (const chat of sourceChats) {
    for (const task of projectDockTasks(chat)) {
      if (!task.id || seen.has(task.id)) {
        continue;
      }
      seen.add(task.id);
      items.push({
        ...task,
        hostChat: chat,
      });
    }
  }
  // 运行中优先，其余按出现顺序
  return items.sort((a, b) => Number(b.state === "run") - Number(a.state === "run"));
}

function AgentRow({
  item,
  now,
  onOpen,
}: {
  item: RowItem;
  now: number;
  onOpen: (item: RowItem) => void;
}) {
  const running = item.state === "run";
  const failed = item.state === "fail";
  const startedAtMs = item.startedAtMs;
  const elapsedLabel = running
    ? startedAtMs != null
      ? formatBackgroundAgentElapsed(Math.max(0, now - startedAtMs))
      : "已运行 1 秒"
    : failed
      ? "已失败"
      : item.timing
        ? `已完成 · ${item.timing}`
        : "已完成";

  return (
    <button
      type="button"
      className={cn(
        "flex w-full items-start gap-2 rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-hover",
        !running && "opacity-75"
      )}
      onClick={() => onOpen(item)}
      data-testid="background-agent-row"
    >
      <BotIcon className="mt-0.5 size-4 shrink-0 text-foreground-subtle" />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span
          className={cn(
            "line-clamp-2 text-ui-base leading-5 text-foreground",
            !running && !failed && "line-through"
          )}
        >
          {item.name}
        </span>
        <span className="text-ui-xs leading-4 tabular-nums text-foreground-subtle">
          {elapsedLabel}
        </span>
      </span>
    </button>
  );
}

/**
 * 右上角「智能体」浮动面板（移植 ZCode `ConversationStatusPanel` 的 Subagent 部分）。
 *
 * 三种形态（variant）：
 * - `auto`：视口 ≥1280px 时展开为面板，窄屏收成胶囊（与 ZCode 一致）
 * - `panel`：展开面板（点头条胶囊 / 输入框徽标都是切到这个态）
 * - `mini`：收起为胶囊
 *
 * 数据源与旧底部 Dock 相同（`projectDockTasks`：run_in_background 的 Agent 派发卡）。
 * 不移植 ZCode 的「停止」按钮 —— 子 Agent 停止走 TaskStop 工具 / 父回合停止。
 */
const BackgroundAgentsPanel = memo(function BackgroundAgentsPanel({
  chat,
  chats,
  variant,
  onVariantChange,
  onOpenAgent,
}: {
  chat?: CHAT.ChatItem;
  chats?: CHAT.ChatItem[];
  variant: AgentsPanelVariant;
  onVariantChange: (variant: AgentsPanelVariant) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
}) {
  const isWide = useIsWideViewport();
  const items = useMemo(
    () => collectAgentItems(chats?.length ? chats : chat ? [chat] : [], chat),
    [chats, chat]
  );
  const runningCount = items.filter((item) => item.state === "run").length;
  const hasRunning = runningCount > 0;
  const now = useNowTicker(hasRunning);
  const [menuOpen, setMenuOpen] = useState(false);
  // startedAtMs 缺失时（旧事件没有 timing）：首次见到的时间兜底，保证计时单调
  const fallbackStartedAtRef = useRef(new Map<string, number>());
  const resolvedItems = useMemo(
    () =>
      items.map((item) => {
        if (item.startedAtMs != null) {
          return item;
        }
        let fallback = fallbackStartedAtRef.current.get(item.id);
        if (fallback == null) {
          fallback = Date.now();
          fallbackStartedAtRef.current.set(item.id, fallback);
        }
        return {
          ...item,
          startedAtMs: fallback,
        };
      }),
    [items]
  );

  const expanded = variant === "panel" || (variant === "auto" && isWide);
  const countLabel = hasRunning
    ? `${runningCount} 运行`
    : `${items.length} 个智能体`;

  const handleOpen = useCallback(
    (item: RowItem) => {
      if (!onOpenAgent) {
        return;
      }
      const toolId = item.id;
      const tool =
        findAgentTaskByToolCallId(item.hostChat, toolId) ||
        ({
          messageType: "tool_call",
          messageId: item.id,
          toolCallId: item.id,
          resultMap: {
            toolName: "Agent",
            childSessionId: item.childSessionId,
            subAgentType: item.meta,
            subAgentDescription: item.name,
            runInBackground: true,
            input: {
              description: item.name,
              subagent_type: item.meta,
            },
          },
        } as unknown as CHAT.Task);
      onOpenAgent(tool, item.hostChat);
    },
    [onOpenAgent]
  );

  if (items.length === 0) {
    return null;
  }

  // 收起态：单颗胶囊（截图 1）：🤖 + 「N 运行」
  if (!expanded) {
    return (
      <div className="pointer-events-none absolute right-4 top-4 z-20">
        <button
          type="button"
          className={cn(
            "pointer-events-auto flex items-center gap-1.5 rounded-full border border-border bg-popover px-3 py-1.5 shadow-md transition-colors hover:bg-hover",
            "text-ui-base font-medium",
            hasRunning ? "text-foreground" : "text-foreground-subtle"
          )}
          onClick={() => onVariantChange("panel")}
          aria-label={`智能体 · ${countLabel}`}
          data-testid="background-agents-pill"
        >
          <BotIcon className="size-4 shrink-0" />
          <span className="tabular-nums">{countLabel}</span>
        </button>
      </div>
    );
  }

  // 展开态：面板（截图 2）—— 头部「智能体 N 运行」+ … + 收起，随后是运行/已结束条目
  return (
    <div className="pointer-events-none absolute right-4 top-4 z-20">
      <div
        className="pointer-events-auto w-80 max-w-[calc(100%-2rem)] overflow-hidden rounded-2xl border border-border bg-popover shadow-md"
        data-testid="background-agents-panel"
      >
        <div className="flex items-center gap-1.5 px-3.5 pb-1 pt-2.5">
          <span className="min-w-0 flex-1 truncate text-ui-base font-semibold text-foreground-subtle">
            智能体
          </span>
          <span className="shrink-0 text-ui-base font-semibold text-foreground-subtle tabular-nums">
            {countLabel}
          </span>
          <DropdownMenu open={menuOpen} onOpenChange={setMenuOpen}>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="flex size-7 items-center justify-center rounded-md text-foreground-subtle transition-colors hover:bg-hover hover:text-foreground"
                aria-label="智能体面板显示模式"
              >
                <EllipsisIcon className="size-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              align="end"
              side="bottom"
              sideOffset={6}
              className="w-[132px] rounded-lg border border-popover-border bg-popover p-1 shadow-md"
            >
              {(["auto", "panel", "mini"] as AgentsPanelVariant[]).map((key) => (
                <button
                  key={key}
                  type="button"
                  className={cn(
                    "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-ui-base transition-colors",
                    variant === key
                      ? "bg-menu-hover text-foreground"
                      : "text-foreground-subtle hover:bg-menu-hover hover:text-foreground"
                  )}
                  onClick={() => {
                    onVariantChange(key);
                    setMenuOpen(false);
                  }}
                >
                  {AGENTS_PANEL_VARIANT_LABELS[key]}
                </button>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
          <button
            type="button"
            className="flex size-7 items-center justify-center rounded-md text-foreground-subtle transition-colors hover:bg-hover hover:text-foreground"
            aria-label="收起智能体面板"
            onClick={() => onVariantChange("mini")}
          >
            <Minimize2Icon className="size-4" />
          </button>
        </div>
        <div className="flex max-h-[min(50vh,360px)] flex-col gap-0.5 overflow-y-auto px-1.5 pb-2">
          {resolvedItems.map((item) => (
            <AgentRow
              key={item.id}
              item={item}
              now={now}
              onOpen={handleOpen}
            />
          ))}
        </div>
      </div>
    </div>
  );
});

export default BackgroundAgentsPanel;
