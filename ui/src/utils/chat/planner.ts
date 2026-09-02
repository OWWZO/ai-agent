/**
 * 统一维护 planner round，避免 replan 相关状态散落在主文件里。
 */
export function ensurePlannerRounds(currentChat: CHAT.ChatItem) {
  if (!Array.isArray(currentChat.multiAgent.plannerRounds)) {
    currentChat.multiAgent.plannerRounds = [];
  }
  return currentChat.multiAgent.plannerRounds;
}

export function resolveLegacyPlannerRoundId(eventData: MESSAGE.EventData) {
  const resultMap = eventData?.resultMap;
  return (
    resultMap?.plannerRoundId ||
    eventData?.taskId ||
    eventData?.messageId ||
    ""
  );
}

function findPlannerRoundIndex(
  plannerRounds: CHAT.PlannerRound[],
  plannerRoundId: string
) {
  return plannerRounds.findIndex((item) => item.plannerRoundId === plannerRoundId);
}

export function upsertPlannerRound(
  currentChat: CHAT.ChatItem,
  plannerRoundId: string,
  updater: (round: CHAT.PlannerRound) => void
) {
  // plannerRound 是 replan 的隔离边界：更新时复制已有 round，再由 updater 修改，
  // 最后按 id 替换/追加，避免同一对象被多个流式事件共享导致 React 状态难以追踪。
  if (!plannerRoundId) {
    return undefined;
  }

  const plannerRounds = ensurePlannerRounds(currentChat);
  const index = findPlannerRoundIndex(plannerRounds, plannerRoundId);
  const nextRound =
    index === -1
      ? ({ plannerRoundId } as CHAT.PlannerRound)
      : ({ ...plannerRounds[index] } as CHAT.PlannerRound);

  updater(nextRound);

  if (index === -1) {
    plannerRounds.push(nextRound);
  } else {
    plannerRounds[index] = nextRound;
  }

  return nextRound;
}

export function syncLatestPlannerAlias(currentChat: CHAT.ChatItem) {
  // 旧 UI 仍读取 multiAgent.plan/plan_thought；round 列表是新事实结构，末轮别名只是
  // 向后兼容投影，不能反过来作为 planner round 的持久状态来源。
  const plannerRounds = currentChat.multiAgent.plannerRounds || [];
  const latestRound = plannerRounds[plannerRounds.length - 1];
  if (!latestRound) {
    return;
  }

  currentChat.multiAgent.plan_thought = latestRound.planThought;
  currentChat.multiAgent.plan = latestRound.plan;
  currentChat.thought = latestRound.planThought || "";
}

export function handlePlanMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  const plannerRoundId = resolveLegacyPlannerRoundId(eventData);
  if (!plannerRoundId) {
    return;
  }

  const nextPlan = {
    taskId: eventData.taskId,
    ...eventData?.resultMap,
  } as unknown as CHAT.Plan;

  upsertPlannerRound(currentChat, plannerRoundId, (round) => {
    round.plan = nextPlan;
    round.planThoughtFinal = true;
    round.planMessageId = eventData.messageId;
    round.planTaskId = eventData.taskId;
  });
  syncLatestPlannerAlias(currentChat);
}

export function handlePlanThoughtMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  // plan_thought 可能是增量片段，也可能是 final 快照；非 final 追加，final 覆盖，
  // 然后统一刷新旧别名，保证流式过程和历史完整帧使用同一合并规则。
  const plannerRoundId = resolveLegacyPlannerRoundId(eventData);
  if (!plannerRoundId) {
    return;
  }

  upsertPlannerRound(currentChat, plannerRoundId, (round) => {
    const currentThought = round.planThought || "";
    const isFinal = Boolean(eventData.resultMap.isFinal);
    if (isFinal) {
      round.planThought = eventData.resultMap.planThought;
    } else {
      round.planThought = `${currentThought}${eventData.resultMap.planThought || ""}`;
    }
    // 思考的终态属于 planner round，而不是整轮 Agent；后续工具执行时不能继续闪动。
    // 迟到的非 final 增量只补字，不能把已收口的思考重新拉开。
    round.planThoughtFinal = Boolean(round.planThoughtFinal) || isFinal;
    round.planThoughtMessageId = eventData.messageId;
    round.planThoughtTaskId = eventData.taskId;
  });
  syncLatestPlannerAlias(currentChat);
}
