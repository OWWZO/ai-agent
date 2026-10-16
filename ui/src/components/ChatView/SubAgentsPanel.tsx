import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  BotIcon,
  ChevronRightIcon,
  EllipsisIcon,
  Minimize2Icon,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import {
  collectSubAgentRoster,
  resolveRosterAgentTool,
  selectEndedSubAgents,
  selectRunningSubAgents,
  type SubAgentRosterItem,
} from "@/utils/chat/subAgentRoster";
import {
  AGENTS_PANEL_VARIANT_LABELS,
  formatSubAgentElapsed,
  useNowTicker,
  type AgentsPanelVariant,
} from "./subAgentsModel";

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

function AgentRow({
  item,
  now,
  onOpen,
}: {
  item: SubAgentRosterItem;
  now: number;
  onOpen: (item: SubAgentRosterItem) => void;
}) {
  const startedAtMs = item.startedAtMs;
  const elapsedLabel =
    startedAtMs != null
      ? formatSubAgentElapsed(Math.max(0, now - startedAtMs))
      : "已运行 1 秒";

  return (
    <button
      type="button"
      className="flex w-full items-start gap-2 rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-hover"
      onClick={() => onOpen(item)}
      data-testid="sub-agent-row"
    >
      <BotIcon className="mt-0.5 size-4 shrink-0 text-foreground-subtle" />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="line-clamp-2 text-ui-base leading-5 text-foreground">
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
 * **同步与后台子 Agent 同列**（名册来自 `collectSubAgentRoster`，不再只收 run_in_background）：
 * 正文只放**运行中**条目（回答「现在有谁在跑」），已结束的收进页脚一行
 * 「已结束 N」，点开右侧工作区的「子智能体目录」tab —— 与 ZCode 同构，
 * 避免面板被历史条目撑长。
 *
 * 不移植 ZCode 的「停止」按钮 —— 子 Agent 停止走 TaskStop 工具 / 父回合停止。
 */
const SubAgentsPanel = memo(function SubAgentsPanel({
  chat,
  chats,
  variant,
  onVariantChange,
  onOpenAgent,
  onOpenDirectory,
}: {
  chat?: CHAT.ChatItem;
  chats?: CHAT.ChatItem[];
  variant: AgentsPanelVariant;
  onVariantChange: (variant: AgentsPanelVariant) => void;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
  onOpenDirectory?: () => void;
}) {
  const isWide = useIsWideViewport();
  const items = useMemo(
    () => collectSubAgentRoster(chats?.length ? chats : chat ? [chat] : [], chat),
    [chats, chat]
  );
  const runningItems = useMemo(() => selectRunningSubAgents(items), [items]);
  const endedItems = useMemo(() => selectEndedSubAgents(items), [items]);
  const hasRunning = runningItems.length > 0;
  const now = useNowTicker(hasRunning);
  const [menuOpen, setMenuOpen] = useState(false);
  // startedAtMs 缺失时（旧事件没有 timing）：首次见到的时间兜底，保证计时单调
  const fallbackStartedAtRef = useRef(new Map<string, number>());
  const resolvedItems = useMemo(
    () =>
      runningItems.map((item) => {
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
    [runningItems]
  );

  const expanded = variant === "panel" || (variant === "auto" && isWide);
  const countLabel = hasRunning
    ? `${runningItems.length} 运行`
    : `${items.length} 个智能体`;

  const handleOpen = useCallback(
    (item: SubAgentRosterItem) => {
      if (!onOpenAgent) {
        return;
      }
      onOpenAgent(resolveRosterAgentTool(item), item.hostChat);
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
          data-testid="sub-agents-pill"
        >
          <BotIcon className="size-4 shrink-0" />
          <span className="tabular-nums">{countLabel}</span>
        </button>
      </div>
    );
  }

  // 展开态：面板（截图 2）—— 头部「智能体 N 运行」+ … + 收起，随后是运行中条目，
  // 页脚一行「已结束 N」进目录（ZCode `EndedSubagentDirectoryRow`）。
  return (
    <div className="pointer-events-none absolute right-4 top-4 z-20">
      <div
        className="pointer-events-auto w-80 max-w-[calc(100%-2rem)] overflow-hidden rounded-2xl border border-border bg-popover shadow-md"
        data-testid="sub-agents-panel"
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

        {resolvedItems.length > 0 ? (
          <div className="flex max-h-[min(50vh,360px)] flex-col gap-0.5 overflow-y-auto px-1.5 pb-1">
            {resolvedItems.map((item) => (
              <AgentRow key={item.id} item={item} now={now} onOpen={handleOpen} />
            ))}
          </div>
        ) : null}

        {endedItems.length > 0 ? (
          <div className="px-1.5 pb-2 pt-0.5">
            <button
              type="button"
              className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-hover"
              onClick={onOpenDirectory}
              data-testid="sub-agents-ended-row"
            >
              <span className="min-w-0 flex-1 truncate text-ui-base text-foreground-subtle">
                已结束
              </span>
              <span className="shrink-0 text-ui-base tabular-nums text-foreground-subtle">
                {endedItems.length}
              </span>
              <ChevronRightIcon className="size-4 shrink-0 text-foreground-subtlest" />
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
});

export default SubAgentsPanel;
