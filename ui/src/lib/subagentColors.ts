/**
 * 子智能体名字的着色（对齐 ZCode `lib/subagentColors.ts`）。
 *
 * ZCode 优先取用户在 subagents 配置里给 Agent 设的 `color`，拿不到时按名字做稳定散列。
 * Cute 的后端不下发这个字段，所以只保留散列兜底 —— 但**散列算法必须与 ZCode 一致**
 * （`hash * 31 + charCode`，无符号 32 位），否则同一个子智能体在两边颜色不同。
 *
 * 颜色值写在 global.css 的 `.kimi-agent-name.is-*` 里，不依赖 Tailwind 调色板是否被保留。
 */

export const SUBAGENT_COLORS = [
  "yellow",
  "red",
  "orange",
  "green",
  "cyan",
  "blue",
  "purple",
  "pink",
] as const;

export type SubagentColor = (typeof SUBAGENT_COLORS)[number];

export function isSubagentColor(value: string): value is SubagentColor {
  return (SUBAGENT_COLORS as readonly string[]).includes(value);
}

export function resolveSubagentColorFromName(name: string): SubagentColor {
  let hash = 0;
  for (let index = 0; index < name.length; index += 1) {
    hash = (hash * 31 + name.charCodeAt(index)) >>> 0;
  }
  return SUBAGENT_COLORS[hash % SUBAGENT_COLORS.length] ?? "blue";
}
