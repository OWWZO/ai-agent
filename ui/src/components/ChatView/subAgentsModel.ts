/**
 * 子智能体面板 / 目录的纯模型：形态枚举 + 文案格式化 + 走时时钟。
 * 与组件分离，避免 react-refresh 只能导出组件的限制。
 */

import { useEffect, useState } from "react";

/** 面板形态：auto=宽屏展开/窄屏胶囊；panel/mini=用户手动指定 */
export type AgentsPanelVariant = "auto" | "panel" | "mini";

export const AGENTS_PANEL_VARIANT_LABELS: Record<AgentsPanelVariant, string> = {
  auto: "自动",
  panel: "展开面板",
  mini: "收起胶囊",
};

/**
 * 运行中条目每秒走时的时钟；无运行中条目时不建 interval。
 * 面板与目录共用 —— 两处都要显示「已运行 X 秒」，各自建一个定时器会不同步。
 */
export function useNowTicker(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) {
      return;
    }
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [active]);
  return now;
}

/** ZCode `formatBackgroundTaskElapsedLabel`：分钟以上「已运行 X 分 Y 秒」，否则「已运行 X 秒」 */
export function formatSubAgentElapsed(elapsedMs: number): string {
  const totalSeconds = Math.max(1, Math.floor(elapsedMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0
    ? `已运行 ${minutes} 分 ${seconds} 秒`
    : `已运行 ${totalSeconds} 秒`;
}

/**
 * 目录行的相对时间（对齐 ZCode `formatTaskRelativeTime`）：
 * <1 分「刚刚」、<1 时「X 分」、<1 天「X 小时」、其余「X 天」。
 */
export function formatSubAgentRelativeTime(
  timestampMs: number | undefined,
  now: number
): string {
  if (timestampMs == null || !Number.isFinite(timestampMs)) {
    return "";
  }
  const diff = Math.max(0, now - timestampMs);
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) {
    return "刚刚";
  }
  if (minutes < 60) {
    return `${minutes} 分`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} 小时`;
  }
  return `${Math.floor(hours / 24)} 天`;
}

/** 目录行的状态文案（运行中 / 已完成 / 失败） */
export function resolveSubAgentStateLabel(state: "run" | "done" | "fail"): string {
  if (state === "run") {
    return "运行中";
  }
  return state === "fail" ? "失败" : "已完成";
}
