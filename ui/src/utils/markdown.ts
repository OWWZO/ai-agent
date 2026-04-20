/**
 * 轻量修正模型输出里常见的 Markdown 断行问题，避免标题和列表被粘在上一段句尾，
 * 导致前端只能把 `##`、`-` 之类符号当普通文本展示。
 */
export function normalizeMarkdownForDisplay(content?: string): string {
  if (!content) {
    return "";
  }

  const normalizedText = content
    .replace(/\uFEFF/g, "")
    .replace(/\r\n?/g, "\n");

  // 代码块内的内容不做格式修正，避免误改示例代码。
  return normalizedText
    .split(/(```[\s\S]*?```)/g)
    .map((segment) => {
      if (segment.startsWith("```")) {
        return segment;
      }
      return normalizeMarkdownSegment(segment);
    })
    .join("");
}

function normalizeMarkdownSegment(segment: string): string {
  return segment
    // 标题如果被接在上一段文本后面，补成独立段落。
    .replace(/([^\n])([ \t]*#{1,6}\s+)/g, "$1\n\n$2")
    // 标题前如果只有一个换行，补成空一行，避免结构过于粘连。
    .replace(/([^\n])\n(#{1,6}\s+)/g, "$1\n\n$2")
    // 列表项如果被接在中文句尾后面，补一个换行。
    .replace(/([\u3400-\u9fff。！？：:；;，,、）】」』])([ \t]*[-*]\s+)/g, "$1\n$2")
    // 有序列表同样处理。
    .replace(/([\u3400-\u9fff。！？：:；;，,、）】」』])([ \t]*\d+\.\s+)/g, "$1\n$2");
}
