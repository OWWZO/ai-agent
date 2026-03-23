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
  defaultWidth = 280,
  minWidth = 220,
  maxWidth = 400,
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
      <div className="flex h-full flex-col border-r border-[#e8e8ed] bg-[#f5f5f7]/80 backdrop-blur-xl">
        {/* 展开按钮 */}
        <div className="flex items-center justify-center p-3">
          <button
            onClick={toggleCollapse}
            className="flex h-9 w-9 items-center justify-center rounded-full text-[#86868b] transition-all duration-200 hover:bg-[#e8e8ed] hover:text-[#1d1d1f]"
            title="展开侧边栏"
          >
            <ChevronRightIcon className="h-5 w-5" />
          </button>
        </div>

        {/* 新建对话按钮 */}
        <div className="flex items-center justify-center p-2">
          <button
            onClick={onCreate}
            className="flex h-10 w-10 items-center justify-center rounded-full bg-[#1d1d1f] text-white shadow-md transition-all duration-200 hover:scale-105 hover:shadow-lg"
            title="新建对话"
          >
            <PlusIcon className="h-5 w-5" />
          </button>
        </div>

        {/* 分割线 */}
        <div className="mx-3 my-2 h-px bg-[#e8e8ed]" />

        {/* 简化的历史列表 - 只显示几个最近对话的圆点 */}
        <div className="flex flex-1 flex-col items-center gap-2 overflow-y-auto py-2">
          {items.slice(0, 8).map((item) => (
            <button
              key={item.id}
              onClick={() => onSelect(item.id)}
              className={classNames(
                "group relative flex h-10 w-10 items-center justify-center rounded-full transition-all duration-200",
                item.isActive
                  ? "bg-[#0071e3] text-white"
                  : "bg-[#e8e8ed] text-[#86868b] hover:bg-[#d2d2d7] hover:text-[#1d1d1f]"
              )}
              title={item.title}
            >
              <span className="text-xs font-medium">
                {item.title.charAt(0).toUpperCase()}
              </span>
              {/* 悬停提示 */}
              <span className="absolute left-full ml-2 hidden whitespace-nowrap rounded-lg bg-[#1d1d1f] px-3 py-1.5 text-xs text-white opacity-0 shadow-lg transition-opacity group-hover:opacity-100 lg:block">
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
      className="relative flex h-full flex-col border-r border-[#e8e8ed] bg-[#f5f5f7]/80 backdrop-blur-xl"
      style={{ width }}
    >
      {/* 头部：折叠按钮 + 新建对话 */}
      <div className="flex items-center justify-between p-3">
        <button
          onClick={toggleCollapse}
          className="flex h-8 w-8 items-center justify-center rounded-full text-[#86868b] transition-all duration-200 hover:bg-[#e8e8ed] hover:text-[#1d1d1f]"
          title="收起侧边栏"
        >
          <ChevronLeftIcon className="h-4 w-4" />
        </button>

        <button
          onClick={onCreate}
          className="flex items-center gap-2 rounded-full bg-[#1d1d1f] px-4 py-2 text-sm font-medium text-white shadow-sm transition-all duration-200 hover:shadow-md"
        >
          <PlusIcon className="h-4 w-4" />
          <span>新建对话</span>
        </button>
      </div>

      {/* 分割线 */}
      <div className="mx-3 h-px bg-[#e8e8ed]" />

      {/* 对话列表 */}
      <div className="flex-1 overflow-hidden">
        <ThreadListSidebar
          className="h-full p-3"
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
          "hover:bg-[#0071e3]/5",
          isDragging && "bg-[#0071e3]/10"
        )}
        style={{ transform: "translateX(50%)" }}
      >
        {/* 拖拽指示器 */}
        <div
          className={classNames(
            "h-12 w-1 rounded-full transition-all duration-200",
            isDragging ? "bg-[#0071e3]" : "bg-[#e8e8ed] group-hover:bg-[#d2d2d7]"
          )}
        />
      </div>
    </div>
  );
};

export default ResizableSidebar;
