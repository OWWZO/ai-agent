import { useCallback, useEffect, useRef, useState } from "react";

/**
 * 统一收口聊天区 / 工作区的布局状态，避免主组件继续堆叠拖拽与折叠细节。
 */
export function useWorkspacePanels() {
  const [leftPanelWidth, setLeftPanelWidth] = useState(50);
  const [isDragging, setIsDragging] = useState(false);
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);
  const [isRightCollapsed, setIsRightCollapsed] = useState(false);
  const dragStartXRef = useRef(0);
  const dragStartWidthRef = useRef(50);
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
      const nextWidth = Math.max(30, Math.min(70, dragStartWidthRef.current + deltaPercent));
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
        setLeftPanelWidth(50);
      }
      return nextCollapsed;
    });
  }, []);

  const toggleRightPanel = useCallback(() => {
    setIsRightCollapsed((previous) => !previous);
  }, []);

  return {
    leftPanelWidth,
    isDragging,
    isLeftCollapsed,
    isRightCollapsed,
    containerRef,
    handleDragStart,
    setIsLeftCollapsed,
    setIsRightCollapsed,
    toggleLeftPanel,
    toggleRightPanel,
  };
}
