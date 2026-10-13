import { FlipMetricValue } from "./FlipMetricValue";

/**
 * 工具卡尾部 `+A −B` 变更计数（对齐 ZCode `renderers.tsx` 的 `renderDiffCount`）。
 *
 * - 数字走 `FlipMetricValue` 逐字符翻页，数值变化时只翻对应槽位；
 * - `+` 用新增色、`−` 用删除色，整体 font-mono + tabular-nums，避免位数变化时行宽抖动；
 * - 两侧计数为 0 时不渲染，保持与 ZCode 一致的「无变更就不占位」。
 */
export function ToolDiffCount({
  added,
  removed,
  animateInitial = false,
}: {
  added: number;
  removed: number;
  animateInitial?: boolean;
}) {
  if (added <= 0 && removed <= 0) {
    return null;
  }

  return (
    <span className="kimi-tool-diff">
      {added > 0 ? (
        <span
          aria-label={`+${added}`}
          className="kimi-tool-diff-added"
          role="text"
          title={`+${added}`}
        >
          +
          <FlipMetricValue value={String(added)} animateInitial={animateInitial} />
        </span>
      ) : null}
      {removed > 0 ? (
        <span
          aria-label={`-${removed}`}
          className="kimi-tool-diff-removed"
          role="text"
          title={`-${removed}`}
        >
          {/* ZCode `renderDiffCount` 用的是 ASCII 连字符 `-`，不是 U+2212 */}
          -
          <FlipMetricValue value={String(removed)} animateInitial={animateInitial} />
        </span>
      ) : null}
    </span>
  );
}
