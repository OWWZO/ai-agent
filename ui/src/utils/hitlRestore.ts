import { restoreAskUserQuestionsForSession } from "@/utils/askUserRestore";
import { restoreDesktopControlsForSession } from "@/utils/desktopControlRestore";
import { restorePlanApprovalsForSession } from "@/utils/planApprovalRestore";

/** 刷新后恢复 AskUser + DesktopControl + PlanApproval pending，并自动 resume 已决策未续跑项 */
export async function restoreHitlForSession(
  conversation: CHAT.ConversationHistory
): Promise<CHAT.ConversationHistory> {
  const withAsk = await restoreAskUserQuestionsForSession(conversation);
  const withDesktop = await restoreDesktopControlsForSession(withAsk);
  return restorePlanApprovalsForSession(withDesktop);
}
