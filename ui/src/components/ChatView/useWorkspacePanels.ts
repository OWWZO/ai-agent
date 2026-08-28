import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import {
  WORKSPACE_RESIZE_END_EVENT,
  WORKSPACE_RESIZE_START_EVENT,
} from "@/utils/workspaceResize";

const DEFAULT_LEFT_WIDTH = 50;
const MIN_LEFT_WIDTH = 24;
const MAX_LEFT_WIDTH = 56;
const LEFT_WIDTH_CSS_VAR = "--workspace-left-width";

export const clampWorkspaceLeftWidth = (width: number) =>
  Math.max(MIN_LEFT_WIDTH, Math.min(MAX_LEFT_WIDTH, width));

export const calculateWorkspaceLeftWidth = ({
  clientX,
  startX,
  startWidth,
  containerWidth,
}: {
  clientX: number;
  startX: number;
  startWidth: number;
  containerWidth: number;
}) => {
  if (!containerWidth) return clampWorkspaceLeftWidth(startWidth);
  return clampWorkspaceLeftWidth(
    startWidth + ((clientX - startX) / containerWidth) * 100
  );
};

const useIsomorphicLayoutEffect =
  typeof window === "undefined" ? useEffect : useLayoutEffect;

/** 沉浸模式：对话区窄列约 24%，工作区主导约 76%（对齐 ClawsGO） */
const IMMERSIVE_LEFT_WIDTH = 24;

/**
 * 统一收口聊天区 / 工作区的布局状态，避免主组件继续堆叠拖拽与折叠细节。
 * 默认对话区 / 工作区各占 50%，首次进入时分割线处于中间位置；
 * 沉浸模式：对话区保留为窄列，工作区主导（侧栏收起由 Home 协同）。
 */
export function useWorkspacePanels() {
  const [leftPanelWidth, setLeftPanelWidth] = useState(DEFAULT_LEFT_WIDTH);
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);
  const [isRightCollapsed, setIsRightCollapsed] = useState(false);
  const [isFocusMode, setIsFocusMode] = useState(false);
  const preFocusLeftWidthRef = useRef(DEFAULT_LEFT_WIDTH);
  const dragStartXRef = useRef(0);
  const dragStartWidthRef = useRef(DEFAULT_LEFT_WIDTH);
  const dragContainerWidthRef = useRef(0);
  const dragWidthRef = useRef(DEFAULT_LEFT_WIDTH);
  const pendingClientXRef = useRef(0);
  const dragFrameRef = useRef<number | null>(null);
  const isDraggingRef = useRef(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const leftPanelRef = useRef<HTMLDivElement>(null);

  const writeLeftPanelWidth = useCallback((width: number) => {
    const panel = leftPanelRef.current;
    if (!panel) return;

    const nextValue = `${clampWorkspaceLeftWidth(width)}%`;
    if (panel.style.getPropertyValue(LEFT_WIDTH_CSS_VAR) === nextValue) {
      return;
    }
    panel.style.setProperty(LEFT_WIDTH_CSS_VAR, nextValue);
  }, []);

  const resetDragCursor = useCallback(() => {
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
  }, []);

  const handleDragStart = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    const container = containerRef.current;
    isDraggingRef.current = true;
    dragStartXRef.current = event.clientX;
    dragStartWidthRef.current = leftPanelWidth;
    dragContainerWidthRef.current = container?.getBoundingClientRect().width || 0;
    dragWidthRef.current = leftPanelWidth;
    writeLeftPanelWidth(leftPanelWidth);
    container?.setAttribute("data-workspace-resizing", "true");
    container?.dispatchEvent(
      new Event(WORKSPACE_RESIZE_START_EVENT, { bubbles: true })
    );
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  }, [leftPanelWidth, writeLeftPanelWidth]);

  const updateDragWidth = useCallback((clientX: number) => {
    const containerWidth = dragContainerWidthRef.current;
    if (!containerWidth) return;

    const nextWidth = calculateWorkspaceLeftWidth({
      clientX,
      startX: dragStartXRef.current,
      startWidth: dragStartWidthRef.current,
      containerWidth,
    });
    if (nextWidth === dragWidthRef.current) return;
    dragWidthRef.current = nextWidth;
    writeLeftPanelWidth(nextWidth);
  }, [writeLeftPanelWidth]);

  const cancelDragFrame = useCallback(() => {
    if (dragFrameRef.current === null) return;
    cancelAnimationFrame(dragFrameRef.current);
    dragFrameRef.current = null;
  }, []);

  const handleDragMove = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
    if (!isDraggingRef.current || !containerRef.current) {
      return;
    }

    event.preventDefault();
    pendingClientXRef.current = event.clientX;
    if (dragFrameRef.current !== null) return;

    dragFrameRef.current = requestAnimationFrame(() => {
      dragFrameRef.current = null;
      if (isDraggingRef.current) {
        updateDragWidth(pendingClientXRef.current);
      }
    });
  }, [updateDragWidth]);

  const handleDragEnd = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
    const wasDragging = isDraggingRef.current;
    if (wasDragging) {
      cancelDragFrame();
      updateDragWidth(event.clientX);
      setLeftPanelWidth(dragWidthRef.current);
    }
    isDraggingRef.current = false;
    dragContainerWidthRef.current = 0;
    const container = containerRef.current;
    container?.removeAttribute("data-workspace-resizing");
    if (wasDragging) {
      container?.dispatchEvent(
        new Event(WORKSPACE_RESIZE_END_EVENT, { bubbles: true })
      );
    }
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    resetDragCursor();
  }, [cancelDragFrame, resetDragCursor, updateDragWidth]);

  useIsomorphicLayoutEffect(() => {
    writeLeftPanelWidth(leftPanelWidth);
  }, [leftPanelWidth, writeLeftPanelWidth]);

  useEffect(() => {
    const container = containerRef.current;
    return () => {
      const wasDragging = isDraggingRef.current;
      cancelDragFrame();
      isDraggingRef.current = false;
      dragContainerWidthRef.current = 0;
      container?.removeAttribute("data-workspace-resizing");
      if (wasDragging) {
        container?.dispatchEvent(
          new Event(WORKSPACE_RESIZE_END_EVENT, { bubbles: true })
        );
      }
      resetDragCursor();
    };
  }, [cancelDragFrame, resetDragCursor]);

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
    isLeftCollapsed,
    isRightCollapsed,
    isFocusMode,
    containerRef,
    leftPanelRef,
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
