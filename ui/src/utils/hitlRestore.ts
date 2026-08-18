import { restoreAskUserQuestionsForSession } from "@/utils/askUserRestore";
import { restorePlanApprovalsForSession } from "@/utils/planApprovalRestore";

/** 刷新后恢复 AskUser + PlanApproval pending，并自动 resume 已决策未续跑项 */
export async function restoreHitlForSession(
  conversation: CHAT.ConversationHistory
): Promise<CHAT.ConversationHistory> {
  const withAsk = await restoreAskUserQuestionsForSession(conversation);
  return restorePlanApprovalsForSession(withAsk);
}
