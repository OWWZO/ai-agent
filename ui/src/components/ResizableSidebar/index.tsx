import React, { useCallback, useEffect, useRef, useState } from "react";
import { ChevronLeftIcon, ChevronRightIcon, PlusIcon } from "lucide-react";
import classNames from "classnames";
import { ThreadListSidebar } from "@/components/assistant-ui/threadlist-sidebar";
import type { LocalThreadListItem } from "@/components/assistant-ui/thread-list";

interface ResizableSidebarProps {
  items: LocalThreadListItem[];
  onCreate: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  isCollapsed: boolean;
  onCollapsedChange: (collapsed: boolean) => void;
  defaultWidth?: number;
  minWidth?: number;
  maxWidth?: number;
}

const ResizableSidebar: React.FC<ResizableSidebarProps> = ({
  items,
  onCreate,
  onSelect,
  onDelete,
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
    onCollapsedChange(!isCollapsed);
  }, [isCollapsed, onCollapsedChange]);

  if (isCollapsed) {
    return (
      <div className="flex h-full flex-col border-r border-[var(--chat-border)] bg-[var(--chat-surface-soft)]/80 backdrop-blur-xl">
        {/* 展开按钮 */}
        <div className="flex items-center justify-center p-4">
          <button
            onClick={toggleCollapse}
            className="flex h-10 w-10 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-all duration-300 hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)] hover:scale-105"
            title="展开侧边栏"
          >
            <ChevronRightIcon className="h-5 w-5" />
          </button>
        </div>

        {/* 新建对话按钮 */}
        <div className="flex items-center justify-center px-3 pb-3">
          <button
            onClick={onCreate}
            className="flex h-11 w-11 items-center justify-center rounded-full bg-[var(--chat-text)] text-[var(--chat-surface)] shadow-md transition-all duration-300 hover:scale-110 hover:shadow-lg hover:shadow-[var(--primary)]/10"
            title="新建对话"
          >
            <PlusIcon className="h-5 w-5" />
          </button>
        </div>

        {/* 分割线 */}
        <div className="mx-4 my-2 h-px bg-[var(--chat-border)]" />

        {/* 简化的历史列表 - 只显示几个最近对话的圆点 */}
        <div className="flex flex-1 flex-col items-center gap-2 overflow-y-auto py-3 px-3">
          {items.slice(0, 8).map((item, index) => (
            <button
              key={item.id}
              onClick={() => onSelect(item.id)}
              className={classNames(
                "group relative flex h-10 w-10 items-center justify-center rounded-full transition-all duration-300",
                "hover:scale-105",
                item.isActive
                  ? "bg-[var(--primary)] text-white shadow-md shadow-[var(--primary)]/20"
                  : "bg-[var(--chat-surface-muted)] text-[var(--chat-text-soft)] hover:bg-[var(--chat-border-strong)] hover:text-[var(--chat-text)]"
              )}
              style={{ animationDelay: `${index * 50}ms` }}
              title={item.title}
            >
              <span className="text-sm font-medium font-[var(--font-sans)]">
                {item.title.charAt(0).toUpperCase()}
              </span>
              {/* 悬停提示 */}
              <span className="absolute left-full ml-3 hidden whitespace-nowrap rounded-lg bg-[var(--chat-text)] px-3 py-1.5 text-xs text-[var(--chat-surface)] opacity-0 shadow-lg transition-all duration-200 group-hover:opacity-100 lg:block">
                {item.title}
              </span>
            </button>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div
      ref={sidebarRef}
      className="relative flex h-full flex-col border-r border-[var(--chat-border)] bg-[var(--chat-surface-soft)]/80 backdrop-blur-xl"
      style={{ width }}
    >
      {/* 头部：折叠按钮 + 新建对话 */}
      <div className="flex items-center justify-between p-4">
        <button
          onClick={toggleCollapse}
          className="flex h-9 w-9 items-center justify-center rounded-full text-[var(--chat-text-soft)] transition-all duration-300 hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)] hover:scale-105"
          title="收起侧边栏"
        >
          <ChevronLeftIcon className="h-4 w-4" />
        </button>

        <button
          onClick={onCreate}
          className="group flex items-center gap-2 rounded-full bg-[var(--chat-text)] px-5 py-2.5 text-sm font-medium text-[var(--chat-surface)] shadow-sm transition-all duration-300 hover:shadow-md hover:shadow-[var(--primary)]/10 hover:scale-[1.02]"
        >
          <PlusIcon className="h-4 w-4 transition-transform duration-300 group-hover:rotate-90" />
          <span className="font-[var(--font-sans)]">新建对话</span>
        </button>
      </div>

      {/* 分割线 */}
      <div className="mx-4 h-px bg-[var(--chat-border)]" />

      {/* 对话列表 */}
      <div className="flex-1 overflow-hidden">
        <ThreadListSidebar
          className="h-full p-4"
          items={items}
          onCreate={onCreate}
          onSelect={onSelect}
          onDelete={onDelete}
        />
      </div>

      {/* 拖拽手柄 */}
      <div
        onMouseDown={handleDragStart}
        className={classNames(
          "absolute right-0 top-0 z-50 h-full w-4 cursor-col-resize transition-colors",
          "flex items-center justify-center",
          "hover:bg-[var(--primary)]/5",
          isDragging && "bg-[var(--primary)]/10"
        )}
        style={{ transform: "translateX(50%)" }}
      >
        {/* 拖拽指示器 */}
        <div
          className={classNames(
            "h-10 w-0.5 rounded-full transition-all duration-300",
            isDragging ? "bg-[var(--primary)] w-1" : "bg-[var(--chat-border)]"
          )}
        />
      </div>
    </div>
  );
};

export default ResizableSidebar;
