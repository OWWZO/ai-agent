import { describe, expect, it } from "vitest";

import { findLatestSessionTasks } from "./sessionTaskModel";

describe("findLatestSessionTasks", () => {
  const task = (messageType: string, id: string) =>
    ({ messageType, messageId: id }) as unknown as CHAT.Task;

  it("返回最后一个 session_tasks，忽略其他类型", () => {
    const taskList = [
      task("tool_call", "t1"),
      task("session_tasks", "old"),
      task("tool_result", "t2"),
      task("session_tasks", "new"),
    ];

    expect(findLatestSessionTasks(undefined, taskList)?.messageId).toBe("new");
  });

  it("没有 session_tasks 时返回 undefined", () => {
    const taskList = [task("tool_call", "t1"), task("tool_result", "t2")];
    expect(findLatestSessionTasks(undefined, taskList)).toBeUndefined();
  });

  it("taskList 为空时回退到 chat.tasks 拍平结果", () => {
    const chat = {
      tasks: [
        [task("session_tasks", "from-chat")],
        [task("tool_call", "t1"), task("session_tasks", "from-chat-2")],
      ],
    } as unknown as CHAT.ChatItem;

    expect(findLatestSessionTasks(chat, [])?.messageId).toBe("from-chat-2");
  });

  it("两者都空时返回 undefined", () => {
    expect(findLatestSessionTasks(undefined, [])).toBeUndefined();
    expect(findLatestSessionTasks(undefined, undefined)).toBeUndefined();
  });
});
