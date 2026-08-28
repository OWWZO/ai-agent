/**
 * HITL Dock 槽位：pending Question / Approval 互斥替换 Composer（对齐 kimi ChatDock）。
 */

import {
  findLatestPlanApproval,
  pickPlanApprovalFields,
} from "./planComposerModel";

export type HitlDockSlot = "ask" | "approval" | "composer";

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null
    ? (value as Record<string, unknown>)
    : {};
}

function readHitlStatus(tool?: CHAT.Task | null): string {
  if (!tool) return "";
  const resultMap = asRecord(tool.resultMap);
  const nested = asRecord(resultMap.resultMap);
  const toolAny = tool as unknown as Record<string, unknown>;
  return String(
    nested.status || resultMap.status || toolAny.status || ""
  )
    .trim()
    .toLowerCase();
}

function isHitlCardSettled(tool?: CHAT.Task | null): boolean {
  if (!tool) return false;
  const resultMap = asRecord(tool.resultMap);
  const nested = asRecord(resultMap.resultMap);
  return Boolean(
    tool.finish ||
      tool.isFinal ||
      resultMap.isFinal === true ||
      nested.isFinal === true
  );
}

/** 显式 pending，或无 status 且未 finish —— 已 finish 的缺省 status 不当 pending */
function isEffectivelyPending(tool?: CHAT.Task | null): boolean {
  if (!tool) return false;
  const status = readHitlStatus(tool);
  if (status === "pending") return true;
  if (status) return false;
  return !isHitlCardSettled(tool);
}

function isPendingStatus(status: string): boolean {
  return status === "pending" || !status;
}

function readHitlId(task: CHAT.Task): string {
  const resultMap = asRecord(task.resultMap);
  const nested = asRecord(resultMap.resultMap);
  const taskAny = task as unknown as Record<string, unknown>;
  return String(
    nested.questionId ||
      nested.approvalId ||
      resultMap.questionId ||
      resultMap.approvalId ||
      taskAny.questionId ||
      taskAny.approvalId ||
      task.messageId ||
      task.id ||
      ""
  );
}

function flattenCandidateTasks(
  chat?: CHAT.ChatItem | null,
  taskList?: CHAT.Task[]
): CHAT.Task[] {
  const out: CHAT.Task[] = [];
  const hitlIndexes = new Map<string, number>();
  const push = (task?: CHAT.Task | null) => {
    if (!task) return;
    if (
      task.messageType === "ask_user_question" ||
      task.messageType === "plan_approval"
    ) {
      const id = readHitlId(task);
      if (id) {
        const previousIndex = hitlIndexes.get(id);
        if (previousIndex != null) {
          const previous = out[previousIndex];
          if (
            previous &&
            isEffectivelyPending(previous) &&
            !isEffectivelyPending(task)
          ) {
            out[previousIndex] = task;
          }
          return;
        }
        hitlIndexes.set(id, out.length);
      }
    }
    out.push(task);
  };

  for (const group of chat?.multiAgent?.tasks || []) {
    for (const task of group || []) push(task as CHAT.Task);
  }

  for (const group of chat?.tasks || []) {
    for (const container of group || []) {
      const children = (container as CHAT.Task).children;
      if (Array.isArray(children) && children.length) {
        for (const child of children) push(child);
      } else {
        push(container as CHAT.Task);
      }
    }
  }

  // taskList 可能比聊天快照晚一个渲染帧，只作为没有更新事实时的补充来源。
  if (taskList?.length) {
    for (const task of taskList) push(task);
  }

  return out;
}

/** 从后往前找最新未决 ask_user_question */
export function findLatestPendingAskUser(
  chat?: CHAT.ChatItem | null,
  taskList?: CHAT.Task[]
): CHAT.Task | undefined {
  const tasks = flattenCandidateTasks(chat, taskList);
  for (let i = tasks.length - 1; i >= 0; i--) {
    const task = tasks[i];
    if (task?.messageType !== "ask_user_question") continue;
    if (isEffectivelyPending(task)) {
      return task;
    }
  }
  return undefined;
}

/** 最新未决 plan_approval（含正文优先，再回退任意 pending） */
export function findLatestPendingPlanApproval(
  chat?: CHAT.ChatItem | null,
  taskList?: CHAT.Task[]
): CHAT.Task | undefined {
  const tasks = flattenCandidateTasks(chat, taskList);
  let fallback: CHAT.Task | undefined;

  for (let i = tasks.length - 1; i >= 0; i--) {
    const task = tasks[i];
    if (task?.messageType !== "plan_approval") continue;
    const fields = pickPlanApprovalFields(task);
    // pickPlanApprovalFields 已对 finish/isFinal 缺 status 回落 decided
    if (fields.status !== "pending") continue;
    if (fields.planContent.trim()) {
      return task;
    }
    if (!fallback) {
      fallback = task;
    }
  }

  // 兼容旧路径：taskList / chat.tasks 投影
  if (!fallback) {
    const latest = findLatestPlanApproval(chat || undefined, taskList);
    if (latest) {
      const fields = pickPlanApprovalFields(latest);
      if (fields.status === "pending") {
        return latest;
      }
    }
  }

  return fallback;
}

/**
 * Dock 决策槽：ask > approval > composer
 * （与 kimi ChatDock v-if 顺序一致）
 */
export function resolveHitlDockSlot(
  chat?: CHAT.ChatItem | null,
  taskList?: CHAT.Task[]
): HitlDockSlot {
  if (findLatestPendingAskUser(chat, taskList)) {
    return "ask";
  }
  if (findLatestPendingPlanApproval(chat, taskList)) {
    return "approval";
  }
  return "composer";
}

export function isHitlPendingStatus(status?: string): boolean {
  return isPendingStatus(String(status || "").trim().toLowerCase());
}
