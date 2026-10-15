/**
 * HITL（AskUserQuestion / PlanApproval / DesktopControl）刷新恢复时的续跑意图判定。
 *
 * 后端把 ANSWERED / RESUMING / RESUME_PENDING 三种持久化状态统一压扁成客户端
 * `answered` / `decided`，原始状态放在 `persistenceStatus`。只看压扁后的值会把
 * 「续跑正在飞」（RESUMING）误判成「还没续跑」而重复 POST resume，撞后端 409
 * （`AgentConcurrentRunException: 续跑已在进行中`），409 还会经 `applyGuardError`
 * 把当前 chat 改成 FAILED 并覆盖 conclusion。
 */

/** submit＝确实需要补发 resume；observe＝续跑在飞，只挂观察；none＝已终态，什么都不做 */
export type HITLResumeIntent = "submit" | "observe" | "none";

export function resolveHitlResumeIntent(
  persistenceStatus?: string
): HITLResumeIntent {
  const status = String(persistenceStatus || "").toUpperCase();
  // 空＝老数据/答案已落库但续跑未发出，两种情况都只能补发。
  if (!status || status === "RESUME_PENDING") {
    return "submit";
  }
  if (status === "RESUMING") {
    return "observe";
  }
  return "none";
}

/** 把「运行中」标记打到指定 chat 上（loading + RUNNING + 推进提示） */
export function markChatResuming(chat: CHAT.ChatItem): CHAT.ChatItem {
  return {
    ...chat,
    loading: true,
    tip: "正在推进任务…",
    metrics: {
      ...(chat.metrics || {}),
      status: "RUNNING",
    },
  };
}

/** 收口：HITL 已答且无需续跑，或续跑已终态 */
export function markChatSettled(chat: CHAT.ChatItem): CHAT.ChatItem {
  return {
    ...chat,
    loading: false,
    tip: "",
    metrics: {
      ...(chat.metrics || {}),
      status: "SUCCESS",
    },
  };
}
