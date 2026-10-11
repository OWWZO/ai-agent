/**
 * 生成「预览区 → 聊天输入框」上下文条目的稳定 id。
 *
 * 单独成文件是为了避免 `types.ts` 与各 `*Context.ts` 之间出现运行时循环依赖
 * （`types.ts` 只做类型聚合，值导入会把环拉实）。
 */
export function createPromptContextId(prefix: string): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}
