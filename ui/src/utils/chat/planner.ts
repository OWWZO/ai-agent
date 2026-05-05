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
    round.planMessageId = eventData.messageId;
    round.planTaskId = eventData.taskId;
  });
  syncLatestPlannerAlias(currentChat);
}

export function handlePlanThoughtMessage(
  eventData: MESSAGE.EventData,
  currentChat: CHAT.ChatItem
) {
  const plannerRoundId = resolveLegacyPlannerRoundId(eventData);
  if (!plannerRoundId) {
    return;
  }

  upsertPlannerRound(currentChat, plannerRoundId, (round) => {
    const currentThought = round.planThought || "";
    if (eventData.resultMap.isFinal) {
      round.planThought = eventData.resultMap.planThought;
    } else {
      round.planThought = `${currentThought}${eventData.resultMap.planThought || ""}`;
    }
    round.planThoughtMessageId = eventData.messageId;
    round.planThoughtTaskId = eventData.taskId;
  });
  syncLatestPlannerAlias(currentChat);
}
