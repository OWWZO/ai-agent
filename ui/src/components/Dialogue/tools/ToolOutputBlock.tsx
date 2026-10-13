import { ToolJsonBlock, parseToolJson } from "./ToolJsonBlock";

/**
 * 工具出参段（对齐 ZCode `components/ai-elements/tool.tsx` 的 `ToolOutput`）。
 *
 * 三条与 ZCode 一致的语义，都是刻意保留的：
 * 1. **有 `errorText` 就只渲染错误文本**，不再渲染结构化输出 —— 失败时
 *    「报错被一坨 JSON 淹没」比缺信息更糟。
 * 2. **没有输出就整段不渲染**（ZCode 是 `if (!(output || errorText)) return null`），
 *    不再给「暂无输出 / 等待输出…」占位。占位属于 execute 卡自己的空态。
 * 3. 段标题固定 `Result` / `Error`，容器是淡底**无描边**、上限 240px（ZCode `max-h-60`）。
 */
export function ToolOutputBlock({
  lines,
  errorText,
}: {
  /** 结构化 / 文本输出（按行拼接） */
  lines?: string[];
  /** 失败原因。存在时只渲染它，忽略 `lines`。 */
  errorText?: string;
}) {
  const trimmedError = (errorText ?? "").trim();

  if (trimmedError) {
    return (
      <section className="kimi-tool-section">
        <h4 className="kimi-tool-section-title">Error</h4>
        <div className="kimi-tool-section-body is-error" data-testid="tool-output-error">
          <div className="kimi-tool-section-error">{trimmedError}</div>
        </div>
      </section>
    );
  }

  const outputText = (lines ?? []).join("\n").trim();
  if (!outputText) {
    return null;
  }

  const jsonOutput = parseToolJson(outputText);

  return (
    <section className="kimi-tool-section">
      <h4 className="kimi-tool-section-title">Result</h4>
      <div className="kimi-tool-section-body" data-testid="tool-output">
        {jsonOutput ? (
          <ToolJsonBlock data={jsonOutput} />
        ) : (
          <div className="kimi-tool-section-text">{outputText}</div>
        )}
      </div>
    </section>
  );
}
