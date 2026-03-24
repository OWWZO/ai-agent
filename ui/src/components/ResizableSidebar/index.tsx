import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChevronLeftIcon, ChevronRightIcon, MessageSquarePlus, PlusIcon, Sparkles } from "lucide-react";
import classNames from "classnames";
import { motion, AnimatePresence } from "motion/react";
import { ThreadListSidebar } from "@/components/assistant-ui/threadlist-sidebar";
import type { LocalThreadListItem } from "@/components/assistant-ui/thread-list";

// shadcn 组件
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Separator } from "@/components/ui/separator";

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

// 根据字符串生成渐变色 - Editorial Tech 配色
const generateGradient = (str: string): string => {
  const gradients = [
    "from-rose-400/80 via-orange-300/80 to-amber-200/80",
    "from-violet-400/80 via-fuchsia-300/80 to-pink-200/80",
    "from-cyan-400/80 via-sky-300/80 to-blue-200/80",
    "from-emerald-400/80 via-teal-300/80 to-cyan-200/80",
    "from-amber-400/80 via-yellow-300/80 to-lime-200/80",
    "from-indigo-400/80 via-purple-300/80 to-pink-200/80",
    "from-fuchsia-400/80 via-rose-300/80 to-orange-200/80",
    "from-teal-400/80 via-emerald-300/80 to-green-200/80",
  ];
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  return gradients[Math.abs(hash) % gradients.length];
};

// 获取图标或首字母
const getInitial = (title: string): string => {
  return title.charAt(0).toUpperCase();
};

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
    const next = !isCollapsed;
    onCollapsedChange(next);
    if (!next) {
      setWidth(defaultWidth);
    }
  }, [isCollapsed, onCollapsedChange, defaultWidth]);

  // 缓存最近对话列表
  const recentItems = useMemo(() => items.slice(0, 10), [items]);

  if (isCollapsed) {
    return (
      <TooltipProvider delayDuration={300}>
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
          className="flex h-full w-[72px] flex-col bg-gradient-to-b from-[var(--chat-surface-soft)] via-[var(--chat-surface)] to-[var(--chat-surface-soft)] backdrop-blur-2xl"
        >
          {/* 顶部操作区 */}
          <div className="flex flex-col items-center gap-3 p-3 pt-5">
            {/* 展开按钮 */}
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={toggleCollapse}
                  className="h-10 w-10 rounded-xl text-[var(--chat-text-soft)] transition-all duration-300 hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)] hover:scale-105"
                >
                  <ChevronRightIcon className="h-[18px] w-[18px]" strokeWidth={1.5} />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="right" className="font-medium">
                展开侧边栏
              </TooltipContent>
            </Tooltip>

            {/* 新建对话按钮 - 强调色 */}
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  size="icon"
                  onClick={onCreate}
                  className="group relative h-12 w-12 rounded-2xl bg-gradient-to-br from-[var(--primary)] to-[var(--primary)]/80 text-white shadow-lg shadow-[var(--primary)]/25 transition-all duration-300 hover:shadow-xl hover:shadow-[var(--primary)]/30 hover:scale-105 hover:-translate-y-0.5"
                >
                  <MessageSquarePlus className="h-5 w-5 transition-transform duration-300 group-hover:scale-110" strokeWidth={1.5} />
                  <span className="absolute -top-1 -right-1 flex h-3 w-3">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[var(--primary)]/40 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-3 w-3 bg-[var(--primary)]/60"></span>
                  </span>
                </Button>
              </TooltipTrigger>
              <TooltipContent side="right" className="font-medium">
                新建对话
              </TooltipContent>
            </Tooltip>
          </div>

          <Separator className="mx-auto my-3 w-8 bg-[var(--chat-border)]/50" />

          {/* 最近对话头像列表 */}
          <div className="flex flex-1 flex-col items-center gap-2 overflow-y-auto overflow-x-hidden px-3 py-2 scrollbar-hide">
            <AnimatePresence mode="popLayout">
              {recentItems.map((item, index) => {
                const gradient = generateGradient(item.id + item.title);
                const initial = getInitial(item.title);

                return (
                  <Tooltip key={item.id}>
                    <TooltipTrigger asChild>
                      <motion.button
                        initial={{ opacity: 0, scale: 0.8, y: 10 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.8 }}
                        transition={{
                          duration: 0.3,
                          delay: index * 0.05,
                          ease: [0.16, 1, 0.3, 1],
                        }}
                        onClick={() => onSelect(item.id)}
                        className={classNames(
                          "group relative flex items-center justify-center rounded-2xl transition-all duration-300",
                          "h-12 w-12",
                          item.isActive
                            ? "ring-2 ring-[var(--primary)] ring-offset-2 ring-offset-[var(--chat-surface-soft)]"
                            : "hover:ring-1 hover:ring-[var(--chat-border-strong)]/50 hover:ring-offset-1 hover:ring-offset-[var(--chat-surface-soft)]"
                        )}
                      >
                        {/* 活跃状态指示器 */}
                        {item.isActive && (
                          <span
                            className="absolute -left-[14px] top-1/2 h-6 w-1 -translate-y-1/2 rounded-r-full bg-[var(--primary)]"
                          />
                        )}

                        {/* Avatar */}
                        <Avatar
                          className={classNames(
                            "h-11 w-11 rounded-2xl transition-all duration-300",
                            item.isActive
                              ? "shadow-lg shadow-[var(--primary)]/20"
                              : "shadow-md shadow-black/5 group-hover:shadow-lg group-hover:shadow-black/10",
                            "group-hover:scale-105"
                          )}
                        >
                          <AvatarImage src={undefined} />
                          <AvatarFallback
                            className={classNames(
                              "bg-gradient-to-br text-white font-medium text-sm",
                              gradient,
                              "rounded-2xl border border-white/20"
                            )}
                          >
                            {initial}
                          </AvatarFallback>
                        </Avatar>

                        {/* 未读/活跃 状态点 */}
                        {item.isActive && (
                          <span className="absolute -bottom-0.5 -right-0.5 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-[var(--chat-surface)]">
                            <Sparkles className="h-2.5 w-2.5 text-[var(--primary)]" />
                          </span>
                        )}
                      </motion.button>
                    </TooltipTrigger>
                    <TooltipContent
                      side="right"
                      sideOffset={12}
                      className="max-w-[200px] font-medium leading-relaxed"
                    >
                      {item.title}
                    </TooltipContent>
                  </Tooltip>
                );
              })}
            </AnimatePresence>

            {/* 空状态 */}
            {recentItems.length === 0 && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="flex flex-col items-center gap-2 pt-4 text-center"
              >
                <div className="h-10 w-10 rounded-2xl bg-[var(--chat-surface-muted)] flex items-center justify-center">
                  <span className="text-lg text-[var(--chat-text-muted)]">⋯</span>
                </div>
                <span className="text-[10px] text-[var(--chat-text-muted)] writing-vertical">
                  无记录
                </span>
              </motion.div>
            )}
          </div>

          {/* 底部装饰 */}
          <div className="flex flex-col items-center gap-2 p-4">
            <div className="h-8 w-8 rounded-full bg-gradient-to-br from-[var(--chat-surface-muted)] to-[var(--chat-surface)] flex items-center justify-center border border-[var(--chat-border)]/30">
              <span className="text-xs font-semibold text-[var(--chat-text-soft)]">
                {items.length > 99 ? "99+" : items.length}
              </span>
            </div>
            <span className="text-[10px] text-[var(--chat-text-muted)] font-medium tracking-wider uppercase">
              对话
            </span>
          </div>
        </motion.div>
      </TooltipProvider>
    );
  }

  return (
    <div
      ref={sidebarRef}
      className="relative flex h-full flex-col bg-[var(--chat-surface-soft)]/60 backdrop-blur-xl"
      style={{ width }}
    >
      {/* 头部：折叠按钮 + 新建对话 */}
      <div className="flex items-center justify-between p-4">
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleCollapse}
          className="h-9 w-9 rounded-full text-[var(--chat-text-soft)] transition-all duration-300 hover:bg-[var(--chat-surface-muted)] hover:text-[var(--chat-text)] hover:scale-105"
        >
          <ChevronLeftIcon className="h-4 w-4" />
        </Button>

        <Button
          onClick={onCreate}
          className="group flex items-center gap-2 rounded-full bg-[var(--chat-text)] px-5 py-2.5 text-sm font-medium text-[var(--chat-surface)] shadow-sm transition-all duration-300 hover:shadow-md hover:shadow-[var(--primary)]/10 hover:scale-[1.02]"
        >
          <PlusIcon className="h-4 w-4 transition-transform duration-300 group-hover:rotate-90" />
          <span className="font-[var(--font-sans)]">新建对话</span>
        </Button>
      </div>

      {/* 对话列表 */}
      <div className="flex-1 overflow-hidden pt-2">
        <ThreadListSidebar
          className="h-full px-4 pb-4"
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
          "absolute right-0 top-0 z-50 h-full w-3 cursor-col-resize transition-colors",
          "flex items-center justify-center",
          "hover:bg-[var(--primary)]/5",
          isDragging && "bg-[var(--primary)]/10"
        )}
        style={{ transform: "translateX(50%)" }}
      >
        <div
          className={classNames(
            "h-8 w-[2px] rounded-full transition-all duration-300",
            isDragging ? "bg-[var(--primary)]" : "bg-[var(--chat-border)]/50"
          )}
        />
      </div>
    </div>
  );
};

export default ResizableSidebar;
