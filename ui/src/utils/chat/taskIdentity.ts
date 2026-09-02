import { isAgentDispatchTask } from "./subagent";
import {
  pickFirstText,
  resolveTaskToolCallId,
  resolveToolCallStreamKey,
} from "./toolCalls";

export type TaskLike =
  | Partial<CHAT.Task>
  | Partial<MESSAGE.Task>
  | Record<string, unknown>
  | undefined;

export type TaskIdentity = {
  toolCallId: string;
  streamToolKey: string;
  messageId: string;
  id: string;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readRawStreamToolKey(record: Record<string, unknown>): string {
  const resultMap = isRecord(record.resultMap) ? record.resultMap : {};
  const nested = isRecord(resultMap.resultMap) ? resultMap.resultMap : {};
  return pickFirstText(
    resultMap.streamToolKey,
    nested.streamToolKey,
    record.streamToolKey
  );
}

/** 统一身份：toolCallId > streamToolKey > messageId > id。streamToolKey 只读原始字段，不含 fallback。 */
export function readTaskIdentity(task?: TaskLike): TaskIdentity {
  if (!task) {
    return { toolCallId: "", streamToolKey: "", messageId: "", id: "" };
  }
  const record = task as Record<string, unknown>;
  const resultMap = isRecord(record.resultMap) ? record.resultMap : {};
  const nested = isRecord(resultMap.resultMap) ? resultMap.resultMap : {};
  return {
    toolCallId: resolveTaskToolCallId(task as Partial<CHAT.Task>),
    streamToolKey: readRawStreamToolKey(record),
    messageId: pickFirstText(
      record.messageId,
      resultMap.messageId,
      nested.messageId
    ),
    id: pickFirstText(record.id),
  };
}

export function identityRank(identity: TaskIdentity, key: string): number {
  if (!key) {
    return 0;
  }
  if (identity.toolCallId === key) {
    return 4;
  }
  if (identity.streamToolKey === key) {
    return 3;
  }
  if (identity.messageId === key) {
    return 2;
  }
  if (identity.id === key) {
    return 1;
  }
  return 0;
}

export function identityKeys(identity: TaskIdentity): string[] {
  return Array.from(
    new Set(
      [
        identity.toolCallId,
        identity.streamToolKey,
        identity.messageId,
        identity.id,
      ].filter(Boolean)
    )
  );
}

export function taskIdentityKeys(task?: TaskLike): string[] {
  return identityKeys(readTaskIdentity(task));
}

export function taskIdentityRank(task: TaskLike, key: string): number {
  return identityRank(readTaskIdentity(task), key);
}

export function identitiesOverlap(left: TaskLike, right: TaskLike): boolean {
  const leftKeys = new Set(taskIdentityKeys(left));
  if (!leftKeys.size) {
    return false;
  }
  return taskIdentityKeys(right).some((key) => leftKeys.has(key));
}

/** 两条真实 toolCallId 不能合并；流式占位（id 仍等于 streamToolKey）升级到真实 id 可以合并。 */
export function isDistinctToolCallId(
  existingCallId?: string,
  incomingCallId?: string,
  existingStreamKey?: string,
  incomingStreamKey?: string
): boolean {
  if (!existingCallId || !incomingCallId || existingCallId === incomingCallId) {
    return false;
  }
  const sameStream =
    !!existingStreamKey &&
    !!incomingStreamKey &&
    existingStreamKey === incomingStreamKey;
  if (
    sameStream &&
    (existingCallId === existingStreamKey || incomingCallId === incomingStreamKey)
  ) {
    return false;
  }
  return true;
}

export function pickBestTaskByKey<T>(
  items: Iterable<T>,
  key: string,
  identityOf: (item: T) => TaskLike = (item) => item as TaskLike,
  prefer?: (item: T) => boolean
): T | undefined {
  if (!key) {
    return undefined;
  }
  let best: T | undefined;
  let bestRank = 0;
  let bestPreferred = false;
  for (const item of items) {
    const rank = identityRank(readTaskIdentity(identityOf(item)), key);
    if (rank === 0) {
      continue;
    }
    const preferred = Boolean(prefer?.(item));
    if (rank > bestRank || (rank === bestRank && preferred && !bestPreferred)) {
      best = item;
      bestRank = rank;
      bestPreferred = preferred;
    }
  }
  return best;
}

function visitTaskTree(task: CHAT.Task | undefined, out: CHAT.Task[]) {
  if (!task) {
    return;
  }
  out.push(task);
  for (const child of task.children || []) {
    visitTaskTree(child, out);
  }
}

export function walkChatTasks(chat: CHAT.ChatItem): CHAT.Task[] {
  const out: CHAT.Task[] = [];
  for (const group of chat.tasks || []) {
    for (const container of group || []) {
      visitTaskTree(container as CHAT.Task, out);
    }
  }
  for (const group of chat.multiAgent?.tasks || []) {
    for (const task of group || []) {
      visitTaskTree(task as CHAT.Task, out);
    }
  }
  return out;
}

export function findBestAgentTask(
  chat: CHAT.ChatItem,
  key: string
): CHAT.Task | undefined {
  return pickBestTaskByKey(
    walkChatTasks(chat).filter((task) => isAgentDispatchTask(task)),
    key
  );
}

export function resolvedStreamKey(task?: TaskLike): string {
  return resolveToolCallStreamKey(task as Partial<CHAT.Task>);
}
