/**
 * Markdown 展示规范化作用域。
 * `structured_summary` 只用于 REACT / PLAN_SOLVE 的最终总结，
 * 会在默认轻量修复的基础上，额外处理常见的“近似 Markdown”输出。
 */
export type MarkdownNormalizationScope = "default" | "structured_summary";

type NormalizeMarkdownOptions = {
  scope?: MarkdownNormalizationScope;
};

const CODE_FENCE_SEGMENT_PATTERN = /(```[\s\S]*?```)/g;
const CJK_SENTENCE_END_PATTERN =
  "[\\u3400-\\u9fff。！？：:；;，,、）】」』]";

/**
 * 轻量修正模型输出里常见的 Markdown 断行问题，避免标题和列表被粘在上一段句尾，
 * 导致前端只能把 `##`、`-` 之类符号当普通文本展示。
 */
export function normalizeMarkdownForDisplay(
  content?: string,
  options: NormalizeMarkdownOptions = {}
): string {
  if (!content) {
    return "";
  }

  const scope = options.scope || "default";
  const normalizedText = content
    .replace(/\uFEFF/g, "")
    .replace(/\r\n?/g, "\n");

  // 代码块内的内容不做格式修正，避免误改示例代码。
  return normalizedText
    .split(CODE_FENCE_SEGMENT_PATTERN)
    .map((segment) => {
      if (segment.startsWith("```")) {
        return segment;
      }
      return normalizeMarkdownSegment(segment, scope);
    })
    .join("");
}

function normalizeMarkdownSegment(
  segment: string,
  scope: MarkdownNormalizationScope
): string {
  const sourceSegment =
    scope === "structured_summary"
      ? normalizeStructuredSummarySegment(segment)
      : segment;

  return normalizeCommonMarkdownSegment(sourceSegment);
}

function normalizeStructuredSummarySegment(segment: string): string {
  return segment
    // 标题缺少空格时补齐，便于 `###1）` / `##你如果...` 正常识别。
    .replace(/(^|\n)([ \t]*#{1,6})(?=\S)/g, "$1$2 ")
    // 列表项缺少空格时补齐，避免 `-计划玩几天` 被当成普通文本。
    .replace(/(^|\n)([ \t]*[-*])(?![-*])(?=\S)/g, "$1$2 ")
    // 中英文句尾后若直接拼了列表项，也先补空格，后续再拆段。
    .replace(
      new RegExp(
        `(${CJK_SENTENCE_END_PATTERN})([ \\t]*[-*])(?![-*])(?=\\S)`,
        "g"
      ),
      "$1$2 "
    )
    // 常见中文序号列表如 `1）经典必去` 也补齐空格。
    .replace(/(^|\n)([ \t]*\d+[.)）])(?=\S)/g, "$1$2 ")
    .replace(
      new RegExp(
        `(${CJK_SENTENCE_END_PATTERN})([ \\t]*\\d+[.)）])(?=\\S)`,
        "g"
      ),
      "$1$2 "
    );
}

function normalizeCommonMarkdownSegment(segment: string): string {
  return segment
    // 标题如果被接在上一段文本后面，补成独立段落。
    // 这里排除 `#` 自身，避免把合法标题 `###` 误拆成 `# + ##`。
    .replace(/([^\n#])([ \t]*#{1,6}\s+)/g, "$1\n\n$2")
    // 标题前如果只有一个换行，补成空一行，避免结构过于粘连。
    .replace(/([^\n])\n(#{1,6}\s+)/g, "$1\n\n$2")
    // 列表项如果被接在中文句尾后面，补一个换行。
    .replace(
      new RegExp(`(${CJK_SENTENCE_END_PATTERN})([ \\t]*[-*]\\s+)`, "g"),
      "$1\n$2"
    )
    // 常规有序列表同样处理。
    .replace(
      new RegExp(`(${CJK_SENTENCE_END_PATTERN})([ \\t]*\\d+\\.\\s+)`, "g"),
      "$1\n$2"
    )
    // 兼容 `1）` / `1)` 这类常见中文序号写法。
    .replace(
      new RegExp(
        `(${CJK_SENTENCE_END_PATTERN})([ \\t]*\\d+[.)）]\\s+)`,
        "g"
      ),
      "$1\n$2"
    );
}
