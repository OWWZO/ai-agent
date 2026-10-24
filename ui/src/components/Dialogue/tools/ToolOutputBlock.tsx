import { useMemo, useState } from "react";
import { ToolJsonBlock, parseToolJson } from "./ToolJsonBlock";
import { clampOutputLines } from "./toolOutputLimit";

/**
 * 工具出参段。
 *
 * 三条与 ZCode 一致的语义，都是刻意保留的：
 * 1. **有 `errorText` 就只渲染错误文本**，不再渲染结构化输出 —— 失败时
 *    「报错被一坨 JSON 淹没」比缺信息更糟。
 * 2. **没有输出就整段不渲染**（ZCode 是 `if (!(output || errorText)) return null`），
 *    不再给「暂无输出 / 等待输出…」占位。占位属于 execute 卡自己的空态。
 * 3. 段标题固定 `Result` / `Error`，容器是淡底**无描边**、上限 240px（ZCode `max-h-60`）。
 *
 * 另外对齐 ZCode `toolOutputSchema.truncated`：超长输出默认只渲染前若干行，
 * 尾部保留一小段，中间用一个「展开」按钮补齐。
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
  const [expanded, setExpanded] = useState(false);

  const outputText = (lines ?? []).join("\n").trim();
  // 展开后传 limit=0：`clampOutputLines` 对 `limit <= 0` 原样返回，与手写空窗等价。
  const windowed = expanded
    ? clampOutputLines(outputText, 0)
    : clampOutputLines(outputText);

  /**
   * 按内容缓存解析结果，且必须在任何早退之前调用（hooks 不能条件执行）。
   *
   * 流式期间 `lines` 每次都是新数组（`resolveTaskToolOutput` 每帧重建），引用必变，
   * 但**内容常常没变**（工具输出只在真正产出时才增长）。对齐 ZCode
   * `hashMarkdownCacheKey`：内容没变就复用上一次的解析结果 ——
   * 省掉 `JSON.parse`，并让 `ToolJsonBlock`（已 memo）拿到**同一个对象引用**
   * 从而整个跳过 `ReactJsonPretty` 的全量序列化（实测 2000 条约 15ms）。
   *
   * ⚠️ 依赖是**原文本身**，不是内容哈希。哈希曾用 32 位 djb2，而它的失败模式
   * 不是「多解析一次」：碰撞时 `useMemo` 直接返回上一次的结果，卡片会把
   * **上一个工具**的 JSON 显示在当前工具名下，且完全静默。实测该哈希 217 个
   * 样本内即可撞到（`hashContent("o4r") === hashContent("o60")`）。
   * 字符串比较虽是 O(n)，但只在依赖数组求值时做一次，远小于省下的序列化成本。
   */
  const jsonOutput = useMemo(
    () => parseToolJson(windowed.text),
    [windowed.text]
  );

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

  if (!outputText) {
    return null;
  }

  return (
    <section className="kimi-tool-section">
      <h4 className="kimi-tool-section-title">Result</h4>
      <div className="kimi-tool-section-body" data-testid="tool-output">
        {jsonOutput ? (
          <ToolJsonBlock data={jsonOutput} />
        ) : (
          <div className="kimi-tool-section-text">{windowed.text}</div>
        )}
        {windowed.hiddenCount > 0 ? (
          <button
            type="button"
            className="kimi-tool-section-more"
            data-testid="tool-output-expand"
            onClick={() => setExpanded(true)}
          >
            展开剩余 {windowed.hiddenCount} 行
          </button>
        ) : null}
      </div>
    </section>
  );
}
