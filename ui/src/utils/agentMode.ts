export function isPlanSolveConversation(
  agentType?: number,
  deepThink?: boolean
) {
  return Boolean(deepThink) || agentType === 3;
}

export function isStructuredConversation(
  agentType?: number,
  deepThink?: boolean
) {
  return isPlanSolveConversation(agentType, deepThink);
}
