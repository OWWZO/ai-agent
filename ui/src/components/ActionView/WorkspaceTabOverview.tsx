import { useEffect, useMemo, useState } from "react";
import { ChevronsDownIcon, X } from "lucide-react";
import { Command as CommandPrimitive } from "cmdk";
import { cn } from "@/lib/utils";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandList,
} from "@/components/ui/command";
import { resolveWorkspaceTabDisplay } from "./workspaceTabDisplay";
import { WorkspaceTabIcon } from "./WorkspaceTabIcon";
import { formatTabRelativeTime, rankTabSearch } from "./workspaceTabsModel";
import {
  workspaceTabKey,
  workspaceTabOpenedAt,
  type WorkspaceTab,
} from "./workspaceFiles";

/**
 * 「标签页列表」浮层（移植 ZCode `SidePaneTabOverview.tsx`）。
 *
 * 解决的问题：tab 条一溢出，条上只剩图标 + 截断成 `pre…` 的标题，
 * 用户认不出是哪个 tab。这个浮层给**完整标题 + 搜索 + 打开时间**。
 *
 * 与 ZCode 一致的三处细节：
 * 1. 搜索是**自研 ranking**（`rankTabSearch`），不是 cmdk 的内置过滤 —— 内置过滤
 *    只按 value 匹配，搜不到「补丁」「子智能体」这类类型词，所以 `shouldFilter={false}`。
 * 2. 行内的关闭按钮必须 `stopPropagation`，否则会连带触发整行的激活；
 *    并且**关完浮层不关**（批量关 tab 是常见操作，关一次就收起太烦）。
 * 3. 浮层打开期间每 60s 刷新一次相对时间，否则「刚刚」会一直停在打开那一刻。
 *
 * 行内容用 `cmdk` 的 Item 原语而不是 `ui/command` 的 `CommandItem` 包装：
 * 后者硬编码了一个 `ml-auto` 的尾部 CheckIcon（16px 隐形占位），
 * 会把「时间 + 关闭按钮」从右缘顶开。
 */

type Props = {
  tabs: WorkspaceTab[];
  activeKey: string;
  onActivate: (key: string) => void;
  onCloseTab: (key: string) => void;
};

export function WorkspaceTabOverview({
  tabs,
  activeKey,
  onActivate,
  onCloseTab,
}: Props) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [now, setNow] = useState(() => Date.now());

  // 打开期间每分钟刷新相对时间；关闭时清空搜索词，下次打开回到完整列表。
  useEffect(() => {
    if (!open) {
      setQuery("");
      return;
    }
    setNow(Date.now());
    const timer = window.setInterval(() => setNow(Date.now()), 60_000);
    return () => window.clearInterval(timer);
  }, [open]);

  const displays = useMemo(
    () => tabs.map((tab) => resolveWorkspaceTabDisplay(tab)),
    [tabs]
  );
  const displayByKey = useMemo(() => {
    const map: Record<string, (typeof displays)[number]> = {};
    for (const item of displays) {
      map[item.key] = item;
    }
    return map;
  }, [displays]);
  const visible = useMemo(() => rankTabSearch(displays, query), [displays, query]);
  const openedAtByKey = useMemo(() => {
    const map: Record<string, number | undefined> = {};
    for (const tab of tabs) {
      map[workspaceTabKey(tab)] = workspaceTabOpenedAt(tab);
    }
    return map;
  }, [tabs]);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="flex size-7 items-center justify-center rounded-lg text-foreground-subtle transition-colors hover:bg-surface-hover hover:text-foreground"
          aria-label="标签页列表"
          title="标签页列表"
          data-testid="workspace-tab-overview-trigger"
        >
          <ChevronsDownIcon className="size-4" />
        </button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        side="bottom"
        sideOffset={6}
        // 让输入框的 autoFocus 生效：Radix 默认会把焦点收到内容容器上。
        onOpenAutoFocus={(event) => event.preventDefault()}
        className="w-72 gap-0 overflow-hidden rounded-xl p-0"
      >
        <Command shouldFilter={false} className="rounded-xl! bg-popover">
          <CommandInput
            placeholder="搜索标签页..."
            value={query}
            onValueChange={setQuery}
            autoFocus
            className="h-8"
          />
          <CommandList className="max-h-80">
            <CommandGroup heading="打开的标签页">
              {visible.map((entry) => {
                const display = displayByKey[entry.key];
                if (!display) {
                  return null;
                }
                const active = entry.key === activeKey;
                const relative = formatTabRelativeTime(
                  openedAtByKey[entry.key],
                  now
                );
                return (
                  <CommandPrimitive.Item
                    key={entry.key}
                    value={entry.key}
                    onSelect={() => {
                      onActivate(entry.key);
                      setOpen(false);
                    }}
                    data-testid="workspace-tab-overview-row"
                    className={cn(
                      "group/tab-row relative flex cursor-default select-none items-center gap-2 rounded-md px-2 py-1.5 text-ui-base outline-hidden",
                      "data-[selected=true]:bg-selected data-[selected=true]:text-foreground",
                      active && "bg-selected text-foreground"
                    )}
                  >
                    <WorkspaceTabIcon spec={display.icon} size="md" />
                    <span className="min-w-0 flex-1 truncate text-left">
                      {display.title}
                    </span>
                    {display.diffBadge ? (
                      <span className="shrink-0 text-ui-xs tabular-nums text-foreground-subtlest">
                        {display.diffBadge}
                      </span>
                    ) : null}
                    {relative ? (
                      <span className="shrink-0 text-ui-sm tabular-nums text-foreground-subtlest">
                        {relative}
                      </span>
                    ) : null}
                    <button
                      type="button"
                      // 双保险：pointerdown 也要拦，否则按住时整行已经进入激活流程。
                      onPointerDown={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                      }}
                      onClick={(event) => {
                        event.preventDefault();
                        event.stopPropagation();
                        onCloseTab(entry.key);
                      }}
                      className={cn(
                        "flex size-4 shrink-0 items-center justify-center rounded-sm text-foreground-subtle transition-opacity",
                        "hover:bg-surface-hover hover:text-foreground",
                        active
                          ? "opacity-100"
                          : "opacity-0 group-hover/tab-row:opacity-100 group-focus-within/tab-row:opacity-100"
                      )}
                      aria-label={`关闭 ${display.title}`}
                    >
                      <X className="size-3" />
                    </button>
                  </CommandPrimitive.Item>
                );
              })}
            </CommandGroup>
            <CommandEmpty className="px-4 py-5 text-left text-ui-base text-foreground-subtle">
              没有找到标签页。
            </CommandEmpty>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
