export function buildPlannerRoundsForDisplay(
  chat: CHAT.ChatItem,
  streamingThought?: string
): CHAT.PlannerRound[] {
  const rounds = Array.isArray(chat.multiAgent?.plannerRounds)
    ? chat.multiAgent.plannerRounds.map((item) => ({
      ...item,
      plan: item.plan
        ? {
          ...item.plan,
          notes: [...(item.plan.notes || [])],
          stages: [...(item.plan.stages || [])],
          steps: [...(item.plan.steps || [])],
          stepStatus: [...(item.plan.stepStatus || [])],
        }
        : item.plan,
    }))
    : [];

  if (!rounds.length) {
    const fallbackThought = streamingThought ?? chat.thought ?? chat.multiAgent?.plan_thought;
    const fallbackPlan = chat.plan || chat.multiAgent?.plan;
    if (!fallbackThought && !fallbackPlan) {
      return [];
    }
    return [{
      plannerRoundId: "latest",
      planThought: fallbackThought,
      plan: fallbackPlan,
    }];
  }

  if (streamingThought) {
    const latestIndex = rounds.length - 1;
    rounds[latestIndex] = {
      ...rounds[latestIndex],
      planThought: streamingThought,
    };
  }

  return rounds;
}

export function syncPlannerVersionCursor(
  currentCursor: number | undefined,
  previousRoundCount: number,
  nextRoundCount: number
) {
  const nextLatestIndex = Math.max(nextRoundCount - 1, 0);
  const previousLatestIndex = Math.max(previousRoundCount - 1, 0);

  if (currentCursor === undefined) {
    return nextLatestIndex;
  }

  if (currentCursor >= previousLatestIndex) {
    return nextLatestIndex;
  }

  return Math.min(Math.max(currentCursor, 0), nextLatestIndex);
}
