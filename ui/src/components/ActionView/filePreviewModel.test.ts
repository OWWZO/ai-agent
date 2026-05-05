import { describe, expect, it } from "vitest";

import {
  filterPreviewTaskList,
  resolvePreviewLeadingIcon,
  resolvePreviewTaskSelection,
  resolvePreviewTitle,
} from "./filePreviewModel";

describe("filePreviewModel", () => {
  const buildTask = (overrides?: Partial<CHAT.Task>): CHAT.Task =>
    ({
      taskId: "task-1",
      messageTime: "1710000000000",
      requestId: "req-1",
      messageId: "msg-1",
      finish: true,
      isFinal: true,
      id: "task-1",
      messageType: "file",
      resultMap: {},
      ...overrides,
    }) as CHAT.Task;

  it("deep_search report 阶段应优先显示查询标题", () => {
    expect(
      resolvePreviewTitle(
        buildTask({
          messageType: "deep_search",
          resultMap: {
            messageType: "report",
            query: "帮我总结 5 月投放效果",
          } as CHAT.Task["resultMap"],
        })
      )
    ).toContain("帮我总结 5 月投放效果");
  });

  it("应过滤掉不可进入工作区的任务，并在无默认任务时回退到最后一项", () => {
    const filtered = filterPreviewTaskList([
      buildTask({ id: "summary", messageType: "task_summary" }),
      buildTask({
        id: "deep-search-extend",
        messageType: "deep_search",
        resultMap: {
          messageType: "extend",
        } as CHAT.Task["resultMap"],
      }),
      buildTask({ id: "file-task", messageType: "file" }),
    ]);

    expect(filtered.map((item) => item.id)).toEqual(["file-task"]);

    const selection = resolvePreviewTaskSelection({
      taskList: filtered,
    });
    expect(selection.taskItem?.id).toBe("file-task");
    expect(selection.realActiveTaskIndex).toBe(0);
  });

  it("deep_search search 阶段应返回搜索图标标记", () => {
    expect(
      resolvePreviewLeadingIcon(
        buildTask({
          messageType: "deep_search",
          resultMap: {
            messageType: "search",
          } as CHAT.Task["resultMap"],
        })
      )
    ).toBe("search");
    expect(
      resolvePreviewLeadingIcon(
        buildTask({
          messageType: "deep_search",
          resultMap: {
            messageType: "report",
          } as CHAT.Task["resultMap"],
        })
      )
    ).toBeUndefined();
  });
});
