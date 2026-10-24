import { describe, expect, it } from "vitest";

import { combineData, handleTaskData } from "@/utils/chat";

/**
 * 回归：任务事实是**原地**改写的（`tasks.push`），派生重建的判据必须是内容签名，
 * 不能用 `multiAgent` 的引用比对。
 *
 * 曾经用引用比对，同一轮里第二个及以后的工具卡全部被跳过：
 * 实测 facts 1→2 而 view 1→1，时间线永久停在第 1 张卡。
 */
function makeChat(): CHAT.ChatItem {
  return {
    sessionId: "s",
    requestId: "r",
    query: "q",
    files: [],
    multiAgent: { tasks: [] },
    loading: true,
    response: "",
  } as unknown as CHAT.ChatItem;
}

function toolCall(id: string, tool: string): MESSAGE.EventData {
  return {
    messageType: "task",
    taskId: "t1",
    resultMap: {
      messageType: "tool_call",
      toolName: tool
    },
    messageId: id,
    taskIndex: 0,
  } as unknown as MESSAGE.EventData;
}

/**
 * 派生视图是「组 → 容器 → 子卡片」三层，这里数到最内层的子卡片。
 *
 * 数错层级会得到恒为 1 的假结论（容器数），掩盖「第二张卡没进时间线」。
 */
function countCards(groups: unknown[][] | undefined): number {
  let total = 0;
  for (const group of groups || []) {
    for (const container of group || []) {
      const children = (container as { children?: unknown[] })?.children;
      total += children?.length || 0;
    }
  }
  return total;
}

describe("任务事实签名", () => {
  it("原地 push 会改变签名（引用比对发现不了）", () => {
    const chat = makeChat();
    const multiAgentBefore = chat.multiAgent;

    combineData(toolCall("m1", "workspace_read"), chat);
    const first = chat.taskFactsSignature;

    combineData(toolCall("m2", "workspace_grep"), chat);
    const second = chat.taskFactsSignature;

    // 前提：引用确实没变 —— 这正是引用比对失效的原因。
    expect(chat.multiAgent).toBe(multiAgentBefore);
    expect(first).not.toBe(second);
  });

  it("没有任务事件时签名保持不变", () => {
    const chat = makeChat();
    combineData(toolCall("m1", "workspace_read"), chat);
    const before = chat.taskFactsSignature;

    // 纯正文增量不进任务时间线，签名必须不变，否则派生层会白跑。
    combineData(
      {
        messageType: "task",
        taskId: "t1",
        messageId: "m1",
        resultMap: {
          messageType: "agent_stream",
          result: "正文增量"
        },
      } as unknown as MESSAGE.EventData,
      chat
    );

    expect(chat.taskFactsSignature).toBe(before);
  });

  it("按签名重建时，每张新卡都进入派生视图", () => {
    const chat = makeChat();
    let lastFacts: string | undefined;

    const rebuildIfChanged = () => {
      if (chat.taskFactsSignature === lastFacts) {
        return false;
      }
      lastFacts = chat.taskFactsSignature;
      handleTaskData(chat, true, chat.multiAgent);
      return true;
    };

    combineData(toolCall("m1", "workspace_read"), chat);
    expect(rebuildIfChanged()).toBe(true);
    expect(countCards(chat.tasks as unknown[][])).toBe(1);

    combineData(toolCall("m2", "workspace_grep"), chat);
    expect(rebuildIfChanged()).toBe(true);
    // 关键断言：第二张卡必须出现（旧实现这里是 1）。
    expect(countCards(chat.tasks as unknown[][])).toBe(2);
  });
});
