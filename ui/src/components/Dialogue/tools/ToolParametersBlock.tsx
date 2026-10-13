import { ToolJsonBlock, type ToolJsonValue } from "./ToolJsonBlock";

/**
 * 工具入参段（对齐 ZCode `components/ai-elements/tool.tsx` 的 `ToolInput`）。
 *
 * 段标题固定 `Parameters`，容器与出参段同规格：淡底、无描边、上限 240px。
 * 解析不出 JSON 时退化为等宽纯文本 —— ZCode 那边 `input` 已经是对象，
 * 而 Reactor 拿到的是流式累积的 `argumentsText` 原始串，可能尚未成形。
 */
export function ToolParametersBlock({
  value,
  text,
}: {
  /** 已解析的 JSON 入参 */
  value?: ToolJsonValue;
  /** 原始入参文本（`value` 缺失时的兜底） */
  text?: string;
}) {
  const trimmedText = (text ?? "").trim();

  if (!value && !trimmedText) {
    return null;
  }

  return (
    <section className="kimi-tool-section">
      <h4 className="kimi-tool-section-title">Parameters</h4>
      <div className="kimi-tool-section-body" data-testid="tool-parameters">
        {value ? (
          <ToolJsonBlock data={value} />
        ) : (
          <div className="kimi-tool-section-text">{trimmedText}</div>
        )}
      </div>
    </section>
  );
}
