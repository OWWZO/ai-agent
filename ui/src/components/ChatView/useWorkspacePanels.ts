import { useCallback, useEffect, useRef, useState } from "react";
import type { GroupImperativeHandle } from "react-resizable-panels";
import {
  WORKSPACE_RESIZE_END_EVENT,
  WORKSPACE_RESIZE_START_EVENT,
} from "@/utils/workspaceResize";

const DEFAULT_LEFT_WIDTH = 50;
export const MIN_LEFT_WIDTH = 24;
export const MAX_LEFT_WIDTH = 56;

/** 沉浸模式：对话区窄列约 24%，工作区主导约 76%（对齐 ClawsGO） */
export const IMMERSIVE_LEFT_WIDTH = 24;

export const clampWorkspaceLeftWidth = (width: number) =>
  Math.max(MIN_LEFT_WIDTH, Math.min(MAX_LEFT_WIDTH, width));

/**
 * 把百分比常量转成 `Panel` 的尺寸 prop。
 *
 * `react-resizable-panels` 把裸数字解释为**像素**，只有字符串无单位或带 `%`
 * 才是百分比；直接传 `24` 会把对话区压成 24px，工作区吃掉整屏。
 */
export const panelPercent = (percent: number) => `${percent}%`;

/**
 * `Panel` 的 `defaultSize` 必须是**常量**，不能绑定拖拽中变化的宽度 state。
 *
 * 库会把 `defaultSize` 的变化当成「重新初始化布局」：拖拽中宽度 state 一变，
 * 新 defaultSize 立刻把布局重置回原值，于是分隔条只能挪动一两像素就被拽回 ——
 * 表现为「线拖不动」。宽度回写一律走 `groupRef.setLayout()`。
 */
export const CHAT_PANEL_DEFAULT_SIZE = panelPercent(DEFAULT_LEFT_WIDTH);

/** `Group` 的 layout：panel id → 百分比。 */
type WorkspaceLayout = Record<string, number> | undefined;

/**
 * 对话区 / 工作区的布局状态。
 *
 * 拖拽与尺寸约束交给 `react-resizable-panels` 的 `Group` / `Panel` / `Separator`，
 * 这里只保留「谁是左栏、折叠、沉浸模式」这些业务状态，并把库算出的宽度
 * 镜像回本地 —— 这样切换折叠 / 沉浸模式时才知道要改回多少。
 */
export function useWorkspacePanels() {
  const [leftPanelWidth, setLeftPanelWidth] = useState(DEFAULT_LEFT_WIDTH);
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);
  const [isRightCollapsed, setIsRightCollapsed] = useState(false);
  const [isFocusMode, setIsFocusMode] = useState(false);
  const preFocusLeftWidthRef = useRef(DEFAULT_LEFT_WIDTH);

  /**
   * 拖拽态挂载点（`Group` 的容器元素）。
   *
   * `react-resizable-panels` 不对外暴露拖拽态，所以靠往这个容器上写
   * `data-workspace-resizing` 来表达：图表用它跳过拖拽中的持续 resize，
   * `WORKSPACE_RESIZING_SELECTOR` 也指向它。
   */
  const workspaceRef = useRef<HTMLDivElement | null>(null);
  /**
   * `Group` 的命令式句柄。
   *
   * `defaultSize` 只在首次挂载时被读取（库内部用 `mutableState.layouts[id]` 缓存，
   * 有缓存就不再回看 defaultSize），所以折叠 / 沉浸模式切换必须走 `setLayout`，
   * 否则左栏宽度不会跟着变。
   */
  const groupRef = useRef<GroupImperativeHandle | null>(null);

  const applyLeftWidth = useCallback((percent: number) => {
    groupRef.current?.setLayout({
      chat: percent,
      workspace: 100 - percent,
    });
  }, []);

  const handleLayoutChange = useCallback((layout: WorkspaceLayout) => {
    const next = layout?.chat;
    if (typeof next === "number") {
      setLeftPanelWidth(clampWorkspaceLeftWidth(next));
    }
    workspaceRef.current?.setAttribute("data-workspace-resizing", "true");
    document.dispatchEvent(new Event(WORKSPACE_RESIZE_START_EVENT));
  }, []);

  /** 拖拽结束：清掉拖拽态标记，并让图表重新量一次容器。 */
  const notifyResizeEnd = useCallback(() => {
    workspaceRef.current?.removeAttribute("data-workspace-resizing");
    document.dispatchEvent(new Event(WORKSPACE_RESIZE_END_EVENT));
  }, []);

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

  // 折叠 / 沉浸模式切换后：
  // 1. 把新宽度推给 Group（defaultSize 不会二次生效）；
  // 2. 让图表重新量一次容器。
  useEffect(() => {
    if (!isLeftCollapsed) {
      applyLeftWidth(leftPanelWidth);
    }
    notifyResizeEnd();
  }, [isLeftCollapsed, isRightCollapsed, isFocusMode, leftPanelWidth, applyLeftWidth, notifyResizeEnd]);

  return {
    leftPanelWidth,
    isLeftCollapsed,
    isRightCollapsed,
    isFocusMode,
    setIsLeftCollapsed,
    setIsRightCollapsed,
    setIsFocusMode,
    toggleLeftPanel,
    toggleRightPanel,
    toggleFocusMode,
    exitFocusMode,
    handleLayoutChange,
    workspaceRef,
    groupRef,
    notifyResizeEnd,
  };
}
