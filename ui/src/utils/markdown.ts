/**
 * 思考文本只作为过程展示，不应把模型偶尔生成的连续星号分隔符原样展示出来。
 * 仅处理四个及以上连续星号，保留正常的粗体/斜体 Markdown 语义。
 */
export function normalizeThinkingText(content?: string): string {
  if (!content) {
    return "";
  }

  return content
    .replace(/\*{4,}/g, "\n\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}
