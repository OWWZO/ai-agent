/**
 * 前端历史上曾经使用 0/1/2 这套本地枚举，
 * 后端现在返回的是真实 AgentType：WORKFLOW=2、PLAN_SOLVE=3、REACT=5。
 * 这里统一做一层兼容，避免组件层继续散落魔法数字。
 */

const LEGACY_PLAN_SOLVE_TYPES = new Set([1]);
const LEGACY_STRUCTURED_TYPES = new Set([1, 2]);
const BACKEND_PLAN_SOLVE_TYPES = new Set([3]);
const BACKEND_STRUCTURED_TYPES = new Set([2, 3, 5]);
const PLAN_SOLVE_TYPES = new Set([
  ...LEGACY_PLAN_SOLVE_TYPES,
  ...BACKEND_PLAN_SOLVE_TYPES,
]);
const STRUCTURED_TYPES = new Set([
  ...LEGACY_STRUCTURED_TYPES,
  ...BACKEND_STRUCTURED_TYPES,
]);

/**
 * 统一兼容 deepThink 和前后端枚举差异，避免每个判定函数重复写同一套分支。
 */
function matchesConversationType(
  agentType: number | undefined,
  deepThink: boolean | undefined,
  supportedTypes: Set<number>
) {
  if (deepThink) {
    return true;
  }

  return agentType != null && supportedTypes.has(agentType);
}

export function isPlanSolveConversation(
  agentType?: number,
  deepThink?: boolean
) {
  return matchesConversationType(agentType, deepThink, PLAN_SOLVE_TYPES);
}

export function isStructuredConversation(
  agentType?: number,
  deepThink?: boolean
) {
  return matchesConversationType(agentType, deepThink, STRUCTURED_TYPES);
}
