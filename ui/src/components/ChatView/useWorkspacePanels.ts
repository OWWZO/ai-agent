import { useCallback, useEffect, useRef, useState } from "react";

const DEFAULT_LEFT_WIDTH = 38;
const MIN_LEFT_WIDTH = 28;
const MAX_LEFT_WIDTH = 60;

/**
 * 统一收口聊天区 / 工作区的布局状态，避免主组件继续堆叠拖拽与折叠细节。
 * 默认对话区 38%、工作区 62%，让产物预览拿到更多展示空间；
 * 同时支持专注模式（隐藏对话区，工作区全屏接管）。
 */
export function useWorkspacePanels() {
  const [leftPanelWidth, setLeftPanelWidth] = useState(DEFAULT_LEFT_WIDTH);
  const [isDragging, setIsDragging] = useState(false);
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);
  const [isRightCollapsed, setIsRightCollapsed] = useState(false);
  const [isFocusMode, setIsFocusMode] = useState(false);
  const dragStartXRef = useRef(0);
  const dragStartWidthRef = useRef(DEFAULT_LEFT_WIDTH);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleDragStart = useCallback((event: React.MouseEvent) => {
    event.preventDefault();
    setIsDragging(true);
    dragStartXRef.current = event.clientX;
    dragStartWidthRef.current = leftPanelWidth;
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  }, [leftPanelWidth]);

  useEffect(() => {
    const handleDragMove = (event: MouseEvent) => {
      if (!isDragging || !containerRef.current) {
        return;
      }

      const containerWidth = containerRef.current.offsetWidth;
      const deltaPixels = event.clientX - dragStartXRef.current;
      const deltaPercent = (deltaPixels / containerWidth) * 100;
      const nextWidth = Math.max(
        MIN_LEFT_WIDTH,
        Math.min(MAX_LEFT_WIDTH, dragStartWidthRef.current + deltaPercent)
      );
      setLeftPanelWidth(nextWidth);
    };

    const handleDragEnd = () => {
      if (!isDragging) {
        return;
      }

      setIsDragging(false);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };

    if (isDragging) {
      document.addEventListener("mousemove", handleDragMove);
      document.addEventListener("mouseup", handleDragEnd);
    }

    return () => {
      document.removeEventListener("mousemove", handleDragMove);
      document.removeEventListener("mouseup", handleDragEnd);
    };
  }, [isDragging]);

  const toggleLeftPanel = useCallback(() => {
    setIsLeftCollapsed((previous) => {
      const nextCollapsed = !previous;
      if (!nextCollapsed) {
        setLeftPanelWidth(DEFAULT_LEFT_WIDTH);
      }
      return nextCollapsed;
    });
  }, []);

  const toggleRightPanel = useCallback(() => {
    setIsRightCollapsed((previous) => !previous);
  }, []);

  const toggleFocusMode = useCallback(() => {
    setIsFocusMode((previous) => !previous);
  }, []);

  return {
    leftPanelWidth,
    isDragging,
    isLeftCollapsed,
    isRightCollapsed,
    isFocusMode,
    containerRef,
    handleDragStart,
    setIsLeftCollapsed,
    setIsRightCollapsed,
    setIsFocusMode,
    toggleLeftPanel,
    toggleRightPanel,
    toggleFocusMode,
  };
}
