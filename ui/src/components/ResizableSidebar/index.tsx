import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  HashIcon,
  LayoutGridIcon,
  PencilLineIcon,
  SearchIcon,
  StickyNoteIcon,
} from "lucide-react";
import classNames from "classnames";
import { motion } from "motion/react";
import type { LocalThreadListItem } from "@/components/assistant-ui/thread-list";
import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Separator } from "@/components/ui/separator";
import { groupConversationHistoryItems } from "@/utils/conversationHistoryGroups";

interface ResizableSidebarProps {
  items: LocalThreadListItem[];
  onCreate: () => void;
  onSearchOpen?: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  isCollapsed: boolean;
  onCollapsedChange: (collapsed: boolean) => void;
  defaultWidth?: number;
  minWidth?: number;
  maxWidth?: number;
}

const navItems = [
  { key: "new", label: "New Chat", icon: PencilLineIcon, action: "create" as const },
  { key: "search", label: "Search", icon: SearchIcon },
  { key: "notes", label: "Notes", icon: StickyNoteIcon },
  { key: "workspace", label: "Workspace", icon: LayoutGridIcon },
];

const folders = [
  { key: "finance", label: "Finance", emoji: "💵" },
  { key: "study", label: "Study", emoji: "📕" },
];

const ResizableSidebar: React.FC<ResizableSidebarProps> = ({
  items,
  onCreate,
  onSearchOpen,
  onSelect,
  onDelete: _onDelete,
  isCollapsed,
  onCollapsedChange,
  defaultWidth = 300,
  minWidth = 240,
  maxWidth = 420,
}) => {
  const [width, setWidth] = useState(defaultWidth);
  const [isDragging, setIsDragging] = useState(false);
  const sidebarRef = useRef<HTMLDivElement>(null);
  const dragStartXRef = useRef(0);
  const dragStartWidthRef = useRef(0);

  // 处理拖拽开始
  const handleDragStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setIsDragging(true);
    dragStartXRef.current = e.clientX;
    dragStartWidthRef.current = width;
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  }, [width]);

  // 处理拖拽中
  useEffect(() => {
    const handleDragMove = (e: MouseEvent) => {
      if (!isDragging) return;
      const delta = e.clientX - dragStartXRef.current;
      const newWidth = Math.max(minWidth, Math.min(maxWidth, dragStartWidthRef.current + delta));
      setWidth(newWidth);
    };

    const handleDragEnd = () => {
      if (isDragging) {
        setIsDragging(false);
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
      }
    };

    if (isDragging) {
      document.addEventListener("mousemove", handleDragMove);
      document.addEventListener("mouseup", handleDragEnd);
    }

    return () => {
      document.removeEventListener("mousemove", handleDragMove);
      document.removeEventListener("mouseup", handleDragEnd);
    };
  }, [isDragging, minWidth, maxWidth]);

  // 切换折叠状态
  const toggleCollapse = useCallback(() => {
    const next = !isCollapsed;
    onCollapsedChange(next);
    if (!next) {
      setWidth(defaultWidth);
    }
  }, [isCollapsed, onCollapsedChange, defaultWidth]);

  const groupedItems = useMemo(
    () => groupConversationHistoryItems(items),
    [items]
  );

  if (isCollapsed) {
    return (
      <TooltipProvider delayDuration={300}>
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
          className="flex h-full w-[72px] flex-col border-r border-[var(--chat-border)] bg-[var(--chat-surface)]/95"
        >
          <div className="flex flex-col items-center gap-2 p-3 pt-4">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={toggleCollapse}
                  className="h-10 w-10 rounded-xl text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
                >
                  <ChevronRightIcon className="h-[18px] w-[18px]" strokeWidth={1.5} />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="right" className="font-medium">
                展开侧边栏
              </TooltipContent>
            </Tooltip>

            {navItems.map((item) => (
              <Tooltip key={item.key}>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={
                      item.action === "create"
                        ? onCreate
                        : item.key === "search"
                          ? onSearchOpen
                          : undefined
                    }
                    className={classNames(
                      "h-10 w-10 rounded-xl transition-colors",
                      item.action === "create"
                        ? "bg-[var(--chat-text)] text-[var(--chat-surface)] hover:bg-[var(--chat-text)]/90"
                        : "text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
                    )}
                  >
                    <item.icon className="h-4.5 w-4.5" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="right">{item.label}</TooltipContent>
              </Tooltip>
            ))}
          </div>

          <Separator className="mx-auto my-2 w-8 bg-[var(--chat-border)]/60" />

          <div className="mt-auto flex flex-col items-center gap-2 p-3 pb-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] text-xs font-semibold text-[var(--chat-text-soft)]">
              {items.length > 99 ? "99+" : items.length}
            </div>
            <div className="text-[10px] text-[var(--chat-text-muted)]">Chats</div>
          </div>
        </motion.div>
      </TooltipProvider>
    );
  }

  return (
    <div
      ref={sidebarRef}
      className="relative flex h-full flex-col border-r border-[var(--chat-border)] bg-[var(--chat-surface)]"
      style={{ width }}
    >
      <div className="flex items-center justify-end px-4 pb-2 pt-4">
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleCollapse}
          className="h-9 w-9 rounded-lg text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)]"
        >
          <ChevronLeftIcon className="h-4 w-4" />
        </Button>
      </div>

      <div className="px-3 pb-1">
        {navItems.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={
              item.action === "create"
                ? onCreate
                : item.key === "search"
                  ? onSearchOpen
                  : undefined
            }
            className={classNames(
              "mb-1.5 flex h-10 w-full items-center gap-3 rounded-lg px-3 text-left text-[14px] text-[var(--chat-text-soft)] transition-colors",
              item.action === "create"
                ? "bg-[var(--chat-surface-soft)] text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)]"
                : "hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
            )}
          >
            <item.icon className="h-4.5 w-4.5" />
            <span>{item.label}</span>
          </button>
        ))}
      </div>

      <Separator className="mx-3 my-2 bg-[var(--chat-border)]" />

      <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
        <div className="mb-2 text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--chat-text-muted)]">
          Channels
        </div>
        <button
          type="button"
          className="mb-3 flex h-9 w-full items-center gap-2 rounded-lg px-2.5 text-[14px] text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
        >
          <HashIcon className="h-4 w-4" />
          <span>general</span>
        </button>

        <div className="mb-2 text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--chat-text-muted)]">
          Folders
        </div>
        {folders.map((folder) => (
          <button
            key={folder.key}
            type="button"
            className="mb-1.5 flex h-9 w-full items-center gap-2 rounded-lg px-2.5 text-[14px] text-[var(--chat-text-soft)] transition-colors hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
          >
            <span className="text-[14px]">{folder.emoji}</span>
            <span>{folder.label}</span>
          </button>
        ))}

        <div className="mb-2 text-[12px] font-semibold uppercase tracking-[0.08em] text-[var(--chat-text-muted)]">
          Chats
        </div>
        {items.length ? (
          groupedItems.map((group) =>
            group.items.length ? (
              <div key={group.key} className="mb-3 last:mb-0">
                <div className="mb-1 text-[12px] text-[var(--chat-text-muted)]">{group.label}</div>
                {group.items.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => onSelect(item.id)}
                    className={classNames(
                      "mb-1 flex w-full items-center rounded-lg px-2.5 py-2 text-left text-[13px] transition-colors last:mb-0",
                      item.isActive
                        ? "bg-[var(--chat-surface-soft)] text-[var(--chat-text)]"
                        : "text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]"
                    )}
                  >
                    <span className="truncate">{item.title}</span>
                  </button>
                ))}
              </div>
            ) : null
          )
        ) : (
          <div className="rounded-lg border border-dashed border-[var(--chat-border)] px-3 py-3 text-[12px] text-[var(--chat-text-muted)]">
            暂无历史对话
          </div>
        )}
      </div>

      <div
        onMouseDown={handleDragStart}
        className={classNames(
          "absolute right-0 top-0 z-50 flex h-full w-3 -translate-x-1/2 cursor-col-resize items-center justify-center transition-colors hover:bg-[var(--primary)]/5",
          isDragging && "bg-[var(--primary)]/10"
        )}
      >
        <div
          className={classNames(
            "h-8 w-[2px] rounded-full transition-all duration-300",
            isDragging ? "bg-[var(--primary)]" : "bg-[var(--chat-border)]/60"
          )}
        />
      </div>
    </div>
  );
};

export default ResizableSidebar;
