import type { DockTaskItem } from "@/types/agentRuntime";
import { projectSubAgentItems } from "./agentRuntimeProjector";
import { findAgentTaskByToolCallId } from "./taskIdentity";

/**
 * 子智能体名册（跨会话聚合）。
 *
 * 右上角「智能体」面板、右侧工作区「子智能体目录」tab、输入框徽标三处共用这一份，
 * 避免各自 walk 一遍 chatList 后出现「面板里有、目录里没有」的割裂。
 *
 * `hostChat` 是条目所属的真实会话：跨 chatList 去重时保留首次出现的宿主，
 * 点击下钻时用它去找 Agent 父卡（父卡可能在历史轮次里，不在 activeChat）。
 */
export type SubAgentRosterItem = DockTaskItem & {
  hostChat: CHAT.ChatItem;
};

/**
 * 聚合全部子智能体条目（同步 + 后台）。
 *
 * 排序：**运行中优先**，其余保持出现顺序（同一会话内按时间线先后）。
 * 不做「已结束倒序」—— 目录的已结束分组自己按 `endedAtMs` 排，
 * 面板只关心运行中，两处诉求不同，排序策略留在各自消费点。
 */
export function collectSubAgentRoster(
  chats?: CHAT.ChatItem[] | null,
  activeChat?: CHAT.ChatItem
): SubAgentRosterItem[] {
  const host =
    activeChat ?? (chats && chats.length ? chats[chats.length - 1] : undefined);
  const sourceChats = host
    ? [host, ...(chats || []).filter((item) => item !== host)]
    : chats || [];

  const seen = new Set<string>();
  const items: SubAgentRosterItem[] = [];
  for (const chat of sourceChats) {
    for (const task of projectSubAgentItems(chat)) {
      if (!task.id || seen.has(task.id)) {
        continue;
      }
      seen.add(task.id);
      items.push({
        ...task,
        hostChat: chat,
      });
    }
  }
  return items.sort(
    (left, right) => Number(right.state === "run") - Number(left.state === "run")
  );
}

/** 名册里的运行中条目 */
export function selectRunningSubAgents(
  items: SubAgentRosterItem[]
): SubAgentRosterItem[] {
  return items.filter((item) => item.state === "run");
}

/** 名册里的已结束条目（失败也算结束），按结束时间倒序，缺时间戳的排最后 */
export function selectEndedSubAgents(
  items: SubAgentRosterItem[]
): SubAgentRosterItem[] {
  return items
    .filter((item) => item.state !== "run")
    .sort((left, right) => (right.endedAtMs ?? 0) - (left.endedAtMs ?? 0));
}

/**
 * 名册条目 → Agent 父卡（下钻详情用）。
 *
 * 优先取真实父卡（历史轮次里也可能有）；取不到时用名册条目合成一张
 * 只够渲染详情的卡 —— 后台子 Agent 的父卡可能已被历史裁剪，
 * 但 childSessionId 还在，此时详情面板仍能订阅真实子会话。
 */
export function resolveRosterAgentTool(item: SubAgentRosterItem): CHAT.Task {
  const found = findAgentTaskByToolCallId(item.hostChat, item.id);
  if (found) {
    return found;
  }
  return {
    messageType: "tool_call",
    messageId: item.id,
    toolCallId: item.id,
    resultMap: {
      toolName: "Agent",
      childSessionId: item.childSessionId,
      subAgentType: item.meta,
      subAgentDescription: item.name,
      runInBackground: Boolean(item.runInBackground),
      input: {
        description: item.name,
        subagent_type: item.meta,
        run_in_background: Boolean(item.runInBackground),
      },
    },
  } as unknown as CHAT.Task;
}
