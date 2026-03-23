import type { FC } from "react";
import {
  Clock3Icon,
  MoreHorizontalIcon,
  PlusIcon,
  SearchIcon,
  Trash2Icon,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

export type LocalThreadListItem = {
  id: string;
  title: string;
  subtitle: string;
  timestamp: string;
  updatedAt: number;
  isActive: boolean;
};

type ThreadListProps = {
  items: LocalThreadListItem[];
  query: string;
  onQueryChange: (value: string) => void;
  onCreate: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
};

type ThreadGroup = {
  label: string;
  items: LocalThreadListItem[];
};

const MS_IN_DAY = 24 * 60 * 60 * 1000;

const getDayStart = (value: number) => {
  const date = new Date(value);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
};

const getGroupLabel = (updatedAt: number) => {
  const today = getDayStart(Date.now());
  const target = getDayStart(updatedAt);
  const diff = Math.round((today - target) / MS_IN_DAY);

  if (diff <= 0) return "Today";
  if (diff === 1) return "Yesterday";
  if (diff <= 7) return "Previous 7 days";
  if (diff <= 30) return "Previous 30 days";
  return "Earlier";
};

const groupItems = (items: LocalThreadListItem[]): ThreadGroup[] => {
  const groups = new Map<string, LocalThreadListItem[]>();

  items.forEach((item) => {
    const label = getGroupLabel(item.updatedAt);
    const current = groups.get(label) ?? [];
    current.push(item);
    groups.set(label, current);
  });

  return Array.from(groups.entries()).map(([label, groupItems]) => ({
    label,
    items: groupItems,
  }));
};

export const ThreadList: FC<ThreadListProps> = ({
  items,
  query,
  onQueryChange,
  onCreate,
  onSelect,
  onDelete,
}) => {
  const groups = groupItems(items);

  return (
    <div className="aui-root aui-thread-list-root flex h-full min-h-0 flex-col">
      <div className="space-y-2.5 px-1 pb-3">
        <Button
          onClick={onCreate}
          className="h-11 w-full justify-start gap-2.5 rounded-[20px] border border-black/5 bg-[#111827] px-4 text-[13px] font-medium text-white shadow-[0_20px_40px_-30px_rgba(15,23,42,0.65)] transition-all duration-200 hover:-translate-y-[1px] hover:bg-[#0f172a] hover:shadow-[0_22px_48px_-30px_rgba(15,23,42,0.72)]"
        >
          <PlusIcon className="size-4" />
          New chat
        </Button>

        <div className="relative">
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#9ca3af]" />
          <Input
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder="Search chats"
            className="h-10 rounded-[18px] border-black/5 bg-white/80 pl-9 pr-3 text-[13px] text-[#111827] placeholder:text-[#9ca3af] shadow-[0_14px_32px_-28px_rgba(15,23,42,0.28)] transition-all duration-200 focus-visible:border-black/10 focus-visible:ring-black/5"
          />
        </div>
      </div>

      <div className="scrollbar-hover min-h-0 flex-1 space-y-4 overflow-y-auto pr-1">
        {groups.length > 0 ? (
          groups.map((group) => (
            <section key={group.label} className="space-y-1.5">
              <div className="flex items-center gap-2 px-2 pb-1">
                <div className="h-px flex-1 bg-gradient-to-r from-black/10 to-transparent" />
                <span className="text-[11px] font-medium uppercase tracking-[0.18em] text-[#9ca3af]">
                  {group.label}
                </span>
              </div>

              <div className="space-y-1">
                {group.items.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    data-active={item.isActive}
                    onClick={() => onSelect(item.id)}
                    className={cn(
                      "group/item relative flex w-full flex-col rounded-[20px] px-3 py-3 text-left transition-all duration-200",
                      item.isActive
                        ? "bg-white shadow-[0_18px_42px_-32px_rgba(15,23,42,0.42)] ring-1 ring-black/6"
                        : "bg-transparent hover:bg-white/72"
                    )}
                  >
                    <div
                      className={cn(
                        "absolute inset-y-3 left-0 w-[3px] rounded-full bg-transparent transition-colors duration-200",
                        item.isActive && "bg-[#111827]"
                      )}
                    />

                    <div className="flex items-start gap-2 pl-2">
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-[13.5px] font-medium leading-5 text-[#111827]">
                          {item.title}
                        </div>
                        <div className="mt-2 flex items-center gap-2">
                          <Badge
                            variant="outline"
                            className={cn(
                              "h-6 rounded-full border px-2.5 text-[10.5px] font-medium tracking-[0.02em]",
                              item.isActive
                                ? "border-black/8 bg-[#f3f4f6] text-[#374151]"
                                : "border-black/5 bg-white/80 text-[#6b7280]"
                            )}
                          >
                            {item.subtitle}
                          </Badge>
                          <span className="inline-flex items-center gap-1 text-[11px] text-[#9ca3af]">
                            <Clock3Icon className="size-3.5" />
                            {item.timestamp}
                          </span>
                        </div>
                      </div>

                      <DropdownMenu>
                        <DropdownMenuTrigger
                          asChild
                          onClick={(event) => event.stopPropagation()}
                        >
                          <Button
                            variant="ghost"
                            size="icon-xs"
                            className={cn(
                              "mt-0.5 size-7 rounded-xl text-[#a1a1aa] opacity-0 transition-all duration-150 hover:bg-black/5 hover:text-[#111827]",
                              item.isActive &&
                                "opacity-100",
                              "group-hover/item:opacity-100 group-focus-visible/item:opacity-100 data-[state=open]:opacity-100"
                            )}
                            aria-label="More actions"
                          >
                            <MoreHorizontalIcon className="size-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="min-w-[128px]">
                          <DropdownMenuItem
                            variant="destructive"
                            onClick={(event) => {
                              event.stopPropagation();
                              onDelete(item.id);
                            }}
                          >
                            <Trash2Icon className="size-4" />
                            Delete
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </div>
                  </button>
                ))}
              </div>
            </section>
          ))
        ) : (
          <div className="flex min-h-[220px] flex-col items-center justify-center rounded-[24px] border border-dashed border-black/8 bg-white/45 px-6 text-center">
            <div className="rounded-full bg-white p-3 shadow-[0_12px_30px_-22px_rgba(15,23,42,0.28)]">
              <SearchIcon className="size-5 text-[#6b7280]" />
            </div>
            <div className="mt-4 text-[14px] font-medium text-[#111827]">
              No matching chats
            </div>
            <div className="mt-1 text-[12px] leading-5 text-[#9ca3af]">
              Try a different keyword or start a new conversation.
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
