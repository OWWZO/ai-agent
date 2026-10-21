/**
 * HITL（AskUserQuestion / PlanApproval / DesktopControl）刷新恢复时的续跑意图判定。
 *
 * 后端把 ANSWERED / RESUMING / RESUME_PENDING 三种持久化状态统一压扁成客户端
 * `answered` / `decided`，原始状态放在 `persistenceStatus`。只看压扁后的值会把
 * 「续跑正在飞」（RESUMING）误判成「还没续跑」而重复 POST resume，撞后端 409
 * （`AgentConcurrentRunException: 续跑已在进行中`），409 还会经 `applyGuardError`
 * 把当前 chat 改成 FAILED 并覆盖 conclusion。
 */

import { buildConversationTaskData } from "@/utils/chat";

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

/**
 * 页内派发去重窗口：快速切会话 / StrictMode 双调用会在毫秒级重复派发同一个
 * resumeRequestId。窗口不宜过长，避免某次派发失败后连刷新都无法重试。
 */
const RESUME_DISPATCH_TTL_MS = 15_000;

const dispatchedAt = new Map<string, number>();

/** 返回 true 表示本次可以派发（已登记占位）。key 为空时不做去重。 */
export function claimHitlResumeDispatch(key: string): boolean {
  if (!key) {
    return true;
  }
  const at = dispatchedAt.get(key);
  if (typeof at === "number" && Date.now() - at < RESUME_DISPATCH_TTL_MS) {
    return false;
  }
  dispatchedAt.set(key, Date.now());
  return true;
}

/** 供单测清理去重表 */
export function resetHitlResumeDispatch(key?: string): void {
  if (key) {
    dispatchedAt.delete(key);
    return;
  }
  dispatchedAt.clear();
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

/**
 * 复制 chatList，连同每组 tasks 一起浅层展开。
 *
 * 三个 HITL restore 都要「改某一轮但不碰原始会话」，所以先整体铺一层新引用。
 */
export function cloneChatListForHITL(
  conversation: CHAT.ConversationHistory
): CHAT.ChatItem[] {
  return conversation.chatList.map((chat) => ({
    ...chat,
    multiAgent: {
      ...(chat.multiAgent || { tasks: [] }),
      tasks: [...(chat.multiAgent?.tasks || [])].map((group) => [...(group || [])]),
    },
  }));
}

/**
 * 定位 pending 项该落到哪一轮。
 *
 * 三级回退：requestId 精确命中 → 唯一的 WAITING_INPUT 轮 → 最后一轮。
 * 历史分页里 requestId 常被裁掉，回退链不能省。
 */
export function resolveHITLChatIndex(
  chatList: CHAT.ChatItem[],
  requestId: string
): number {
  let index = requestId
    ? chatList.findIndex((chat) => chat.requestId === requestId)
    : -1;
  if (index < 0) {
    index = chatList.findIndex(
      (chat) => String(chat.metrics?.status || "").toUpperCase() === "WAITING_INPUT"
    );
  }
  if (index < 0) {
    index = chatList.length - 1;
  }
  return index;
}

/**
 * 把改过的 chat 写回列表，并重建派生层。
 *
 * Dialogue 渲染的是 chat.tasks，必须像历史 hydrate 一样从 multiAgent 重建；
 * tip / loading / metrics 在渲染层之外单独维护，不能被派生覆盖。
 */
export function commitHITLChat(
  chatList: CHAT.ChatItem[],
  index: number,
  chat: CHAT.ChatItem,
  deepThink?: boolean
): void {
  const derived = buildConversationTaskData(chat, deepThink);
  chatList[index] = {
    ...derived.currentChat,
    tip: chat.tip,
    loading: chat.loading,
    metrics: chat.metrics,
  };
}
