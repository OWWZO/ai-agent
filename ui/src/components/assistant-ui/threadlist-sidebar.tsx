import type { FC } from "react";
import { useMemo, useState } from "react";
import { HistoryIcon, MessageSquareDashedIcon, PanelLeftCloseIcon } from "lucide-react";

import {
  ThreadList,
  type LocalThreadListItem,
} from "@/components/assistant-ui/thread-list";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

type ThreadListSidebarProps = {
  items: LocalThreadListItem[];
  onCreate: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  className?: string;
};

export const ThreadListSidebar: FC<ThreadListSidebarProps> = ({
  items,
  onCreate,
  onSelect,
  onDelete,
  className,
}) => {
  const [query, setQuery] = useState("");

  const filteredItems = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return items;

    return items.filter((item) => {
      return [item.title, item.subtitle, item.timestamp]
        .join(" ")
        .toLowerCase()
        .includes(keyword);
    });
  }, [items, query]);

  const activeItem = items.find((item) => item.isActive);

  return (
    <div
      className={cn(
        "flex h-full min-h-0 flex-col bg-[linear-gradient(180deg,rgba(246,247,248,0.98)_0%,rgba(242,244,247,0.92)_100%)]",
        className
      )}
    >
      <div className="sticky top-0 z-20 px-3 pb-3 pt-3">
        <div className="overflow-hidden rounded-[28px] border border-black/5 bg-white/78 shadow-[0_24px_58px_-42px_rgba(15,23,42,0.38)] backdrop-blur-xl">
          <div className="flex items-center justify-between border-b border-black/5 px-4 py-4">
            <div className="flex items-center gap-3">
              <div className="flex size-10 items-center justify-center rounded-[18px] bg-[#111827] text-white shadow-[0_18px_34px_-24px_rgba(15,23,42,0.64)]">
                <MessageSquareDashedIcon className="size-4.5" />
              </div>
              <div>
                <div className="text-[14px] font-semibold tracking-[-0.02em] text-[#111827]">
                  Reactor
                </div>
                <div className="text-[11px] text-[#9ca3af]">Local history workspace</div>
              </div>
            </div>

            <div className="rounded-full border border-black/5 bg-white/82 p-1 text-[#9ca3af]">
              <PanelLeftCloseIcon className="size-4" />
            </div>
          </div>

          <div className="flex items-center justify-between px-4 py-3">
            <div>
              <div className="text-[12px] font-medium text-[#6b7280]">Saved chats</div>
              <div className="mt-1 text-[22px] font-semibold tracking-[-0.04em] text-[#111827]">
                {items.length}
              </div>
            </div>

            <div className="flex flex-col items-end gap-2">
              <Badge className="h-6 rounded-full bg-[#111827] px-2.5 text-[10.5px] font-medium text-white hover:bg-[#111827]">
                Active
              </Badge>
              <div className="max-w-[140px] truncate text-[11px] text-[#9ca3af]">
                {activeItem?.title ?? "No conversation selected"}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="min-h-0 flex-1 px-3 pb-3">
        <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-[30px] border border-black/5 bg-[rgba(255,255,255,0.56)] p-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.8)] backdrop-blur-xl">
          <div className="mb-3 flex items-center justify-between px-1">
            <div className="flex items-center gap-2 text-[12px] font-medium text-[#6b7280]">
              <HistoryIcon className="size-4" />
              Recent conversations
            </div>
            <div className="text-[11px] text-[#9ca3af]">{filteredItems.length} visible</div>
          </div>

          <ThreadList
            items={filteredItems}
            query={query}
            onQueryChange={setQuery}
            onCreate={onCreate}
            onSelect={onSelect}
            onDelete={onDelete}
          />
        </div>
      </div>

      <div className="px-3 pb-3">
        <div className="rounded-[24px] border border-black/5 bg-white/70 px-4 py-3 shadow-[0_16px_42px_-34px_rgba(15,23,42,0.36)] backdrop-blur-xl">
          <div className="text-[12px] font-medium text-[#111827]">History stays local</div>
          <div className="mt-1 text-[11px] leading-5 text-[#9ca3af]">
            Search, resume and manage your recent sessions in one place.
          </div>
        </div>
      </div>
    </div>
  );
};
