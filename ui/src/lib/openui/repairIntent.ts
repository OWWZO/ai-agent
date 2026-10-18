/**
 * openui 修复意图的执行端（plans/038 v4）：只认 SEARCH/REPLACE 块。
 *
 * 模型没有"重写整条语句"的自由 —— 它能动的范围被 SEARCH 的长度上限框死，
 * 碰不到块之外的任何字符。SEARCH 匹配本身就是防幻觉闸门：复述的原文对不上
 * 就找不到、不唯一就作废，最坏结果是"没修好"，不会"改坏"。
 */

export interface ReplaceBlock {
  searchText: string;
  replaceText: string;
}

export type BlockFailureReason =
  | 'empty_search'
  | 'too_long'
  | 'too_many'
  | 'not_found'
  | 'not_unique'
  | 'overlapping';

export interface BlockFailure {
  /** 在传入 blocks 数组中的下标 */
  index: number;
  reason: BlockFailureReason;
}

export interface ApplyIntentsResult {
  /** 应用成功后的程序；没有任何块应用成功时为 null（原文不动） */
  result: string | null;
  appliedCount: number;
  failures: BlockFailure[];
}

/**
 * SEARCH 长度上限（字符）。这是 v4 下唯一限制模型改动范围的机制 ——
 * 超过它就等于允许整段重写，语义漂移风险全部回来。按"一条语句"的规模从严取值：
 * 实测最长的计算器按键语句约 300 字符，取 2 倍余量。
 */
export const MAX_SEARCH_LENGTH = 600;

/** 替换块数量上限（plans/038 §5.1 第 3 条）。 */
export const MAX_BLOCKS = 3;

export function applyIntents(source: string, blocks: ReplaceBlock[]): ApplyIntentsResult {
  const failures: BlockFailure[] = [];
  const hits: { index: number; start: number; end: number; replaceText: string }[] = [];

  blocks.forEach((block, index) => {
    const searchText = block?.searchText ?? '';
    if (!searchText.trim()) {
      failures.push({
        index,
        reason: 'empty_search'
      });
      return;
    }
    if (searchText.length > MAX_SEARCH_LENGTH) {
      failures.push({
        index,
        reason: 'too_long'
      });
      return;
    }
    const start = source.indexOf(searchText);
    if (start < 0) {
      // 模型凭记忆复述的原文与实际对不上 —— 作废这条，不会改到别处。
      failures.push({
        index,
        reason: 'not_found'
      });
      return;
    }
    if (source.indexOf(searchText, start + 1) >= 0) {
      // 歧义替换是最阴的失败模式：替换到另一个位置，语法仍合法、验收照样过。
      failures.push({
        index,
        reason: 'not_unique'
      });
      return;
    }
    hits.push({
      index,
      start,
      end: start + searchText.length,
      replaceText: block.replaceText ?? ''
    });
  });

  hits.sort((a, b) => a.start - b.start);

  // 重叠校验：位置冲突时保留先出现的块，丢弃后出现的（确定性，不依赖输入顺序）。
  const accepted: typeof hits = [];
  for (const hit of hits) {
    const previous = accepted[accepted.length - 1];
    if (previous && hit.start < previous.end) {
      failures.push({
        index: hit.index,
        reason: 'overlapping'
      });
      continue;
    }
    accepted.push(hit);
  }

  if (accepted.length > MAX_BLOCKS) {
    for (const hit of accepted.slice(MAX_BLOCKS)) {
      failures.push({
        index: hit.index,
        reason: 'too_many'
      });
    }
    accepted.length = MAX_BLOCKS;
  }

  if (accepted.length === 0) {
    return {
      result: null,
      appliedCount: 0,
      failures
    };
  }

  // 从后往前应用：前一块的替换不会破坏后一块的定位。
  let result = source;
  for (let i = accepted.length - 1; i >= 0; i--) {
    const hit = accepted[i];
    result = result.slice(0, hit.start) + hit.replaceText + result.slice(hit.end);
  }
  return {
    result,
    appliedCount: accepted.length,
    failures
  };
}
