/**
 * 右上角「智能体」面板的纯模型：形态枚举 + 运行时长文案。
 * 与组件分离，避免 react-refresh 只能导出组件的限制。
 */

/** 面板形态：auto=宽屏展开/窄屏胶囊；panel/mini=用户手动指定 */
export type AgentsPanelVariant = "auto" | "panel" | "mini";

export const AGENTS_PANEL_VARIANT_LABELS: Record<AgentsPanelVariant, string> = {
  auto: "自动",
  panel: "展开面板",
  mini: "收起胶囊",
};

/** ZCode `formatBackgroundTaskElapsedLabel`：分钟以上「已运行 X 分 Y 秒」，否则「已运行 X 秒」 */
export function formatBackgroundAgentElapsed(elapsedMs: number): string {
  const totalSeconds = Math.max(1, Math.floor(elapsedMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0
    ? `已运行 ${minutes} 分 ${seconds} 秒`
    : `已运行 ${totalSeconds} 秒`;
}
