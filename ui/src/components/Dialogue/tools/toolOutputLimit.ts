/**
 * 工具输出的截断策略。
 *
 * 抽成独立模块（而非放在组件文件里）的原因同 `markdown/codeHighlightPolicy.ts`：
 * 1. 组件文件只导出组件，避免 fast-refresh 失效；
 * 2. 策略是纯函数，可以被直接断言。
 */

/** 结构化 / 文本输出的默认展示行数。 */
export const DEFAULT_OUTPUT_LINE_LIMIT = 200;

/** 超出上限时保留的尾部行数：命令输出的错误/汇总常在末尾。 */
export const OUTPUT_TAIL_LINE_COUNT = 20;

export type OutputWindow = {
  text: string;
  hiddenCount: number;
};

/**
 * 把文本裁成「头部 + 省略 + 尾部」。
 *
 * 对齐 ZCode `toolOutputSchema` 的 head+tail 截断：工具输出没有行数上限，一次 `cat`
 * 大文件就是几万个文本节点。ZCode 截完后还能靠 `truncated.ref` 按需拉全量，
 * 我们没有这个协议字段，所以只做「前端按需展开」——默认截头，超出行数由用户手动展开。
 *
 * 只截文本，不截 key：截断后仍丢给 `parseToolJson` 试解析，解析失败就按纯文本展示，
 * 与未截断时的降级路径一致。
 */
export function clampOutputLines(
  text: string,
  limit: number = DEFAULT_OUTPUT_LINE_LIMIT
): OutputWindow {
  const lines = text.split("\n");
  if (lines.length <= limit || limit <= 0) {
    return {
      text,
      hiddenCount: 0,
    };
  }
  const tail = Math.min(OUTPUT_TAIL_LINE_COUNT, Math.max(1, limit - 1));
  const head = Math.max(1, limit - tail);
  const kept = [...lines.slice(0, head), ...lines.slice(lines.length - tail)];
  return {
    text: kept.join("\n"),
    hiddenCount: lines.length - kept.length,
  };
}
