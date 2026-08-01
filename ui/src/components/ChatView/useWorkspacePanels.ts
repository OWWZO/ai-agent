import { useCallback, useEffect, useRef, useState } from "react";

const DEFAULT_LEFT_WIDTH = 50;
const MIN_LEFT_WIDTH = 24;
const MAX_LEFT_WIDTH = 56;

/** 沉浸模式：对话区窄列约 24%，工作区主导约 76%（对齐 ClawsGO） */
const IMMERSIVE_LEFT_WIDTH = 24;

/**
 * 统一收口聊天区 / 工作区的布局状态，避免主组件继续堆叠拖拽与折叠细节。
 * 默认对话区 / 工作区各占 50%，首次进入时分割线处于中间位置；
 * 沉浸模式：对话区保留为窄列，工作区主导（侧栏收起由 Home 协同）。
 */
export function useWorkspacePanels() {
  const [leftPanelWidth, setLeftPanelWidth] = useState(DEFAULT_LEFT_WIDTH);
  const [isDragging, setIsDragging] = useState(false);
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);
  const [isRightCollapsed, setIsRightCollapsed] = useState(false);
  const [isFocusMode, setIsFocusMode] = useState(false);
  const preFocusLeftWidthRef = useRef(DEFAULT_LEFT_WIDTH);
  const dragStartXRef = useRef(0);
  const dragStartWidthRef = useRef(DEFAULT_LEFT_WIDTH);
  const containerRef = useRef<HTMLDivElement>(null);

  const resetDragCursor = useCallback(() => {
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
  }, []);

  const handleDragStart = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    setIsDragging(true);
    dragStartXRef.current = event.clientX;
    dragStartWidthRef.current = leftPanelWidth;
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  }, [leftPanelWidth]);

  const handleDragMove = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
    if (!isDragging || !containerRef.current) {
      return;
    }

    event.preventDefault();
    const containerWidth = containerRef.current.offsetWidth;
    const deltaPixels = event.clientX - dragStartXRef.current;
    const deltaPercent = (deltaPixels / containerWidth) * 100;
    const nextWidth = Math.max(
      MIN_LEFT_WIDTH,
      Math.min(MAX_LEFT_WIDTH, dragStartWidthRef.current + deltaPercent)
    );
    setLeftPanelWidth(nextWidth);
  }, [isDragging]);

  const handleDragEnd = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    setIsDragging(false);
    resetDragCursor();
  }, [resetDragCursor]);

  useEffect(() => {
    return () => {
      resetDragCursor();
    };
  }, [resetDragCursor]);

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
    setIsFocusMode((previous) => {
      const next = !previous;
      if (next) {
        preFocusLeftWidthRef.current = leftPanelWidth;
        setIsLeftCollapsed(false);
        setIsRightCollapsed(false);
        setLeftPanelWidth(IMMERSIVE_LEFT_WIDTH);
      } else {
        setLeftPanelWidth(preFocusLeftWidthRef.current || DEFAULT_LEFT_WIDTH);
      }
      return next;
    });
  }, [leftPanelWidth]);

  const exitFocusMode = useCallback(() => {
    setIsFocusMode((previous) => {
      if (!previous) {
        return previous;
      }
      setLeftPanelWidth(preFocusLeftWidthRef.current || DEFAULT_LEFT_WIDTH);
      return false;
    });
  }, []);

  return {
    leftPanelWidth,
    isDragging,
    isLeftCollapsed,
    isRightCollapsed,
    isFocusMode,
    containerRef,
    handleDragStart,
    handleDragMove,
    handleDragEnd,
    setIsLeftCollapsed,
    setIsRightCollapsed,
    setIsFocusMode,
    toggleLeftPanel,
    toggleRightPanel,
    toggleFocusMode,
    exitFocusMode,
  };
}
