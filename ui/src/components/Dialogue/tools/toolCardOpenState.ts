/**
 * 工具卡折叠状态的模块级持久表（对齐 ZCode `ToolLayout.tsx:16` 的 `toolLayoutOpenState`）。
 *
 * 存在的理由：工具卡在 turn 切换、虚拟列表重挂、子代理详情往返时会重新挂载。
 * 如果折叠态只活在组件内部 `useState`，用户手动展开的卡会被重置回默认态，
 * 长会话里表现为「刚点开的 diff 又自己收起来了」。
 *
 * 与 ZCode 的差异：ZCode 那张表按 toolId 只增不减；这里加了容量上限，
 * 避免超长会话把每条 toolId 的布尔值永久留在内存里。
 */
const MAX_TOOL_CARD_OPEN_ENTRIES = 2000;

const openState = new Map<string, boolean>();

export function getToolCardOpen(key: string): boolean | undefined {
  return openState.get(key);
}

export function setToolCardOpen(key: string, open: boolean): void {
  // 先删后写：Map 保持插入顺序，重复交互过的卡片会被挪到队尾，
  // 超限淘汰时优先丢掉最久没被碰过的条目。
  openState.delete(key);
  openState.set(key, open);

  if (openState.size <= MAX_TOOL_CARD_OPEN_ENTRIES) {
    return;
  }

  const overflow = openState.size - MAX_TOOL_CARD_OPEN_ENTRIES;
  let removed = 0;
  for (const existingKey of openState.keys()) {
    openState.delete(existingKey);
    removed += 1;
    if (removed >= overflow) {
      break;
    }
  }
}

/** 仅测试使用：清空持久表，避免用例之间互相污染。 */
export function resetToolCardOpenState(): void {
  openState.clear();
}
