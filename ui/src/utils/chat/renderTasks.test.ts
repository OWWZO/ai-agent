import { describe, expect, it } from "vitest";

import { processTaskForRender } from "./renderTasks";

describe("DeepSearch 展示任务拆分", () => {
  it("章节内有多个查询词时仍按章节生成展示卡", () => {
    const task = {
      messageType: "deep_search",
      messageTime: "1714041600000",
      resultMap: {
        messageType: "chapter_summary",
        searchResult: {
          query: ["一章查询一", "一章查询二", "一章查询三", "二章查询一", "二章查询二", "二章查询三"],
          docs: [],
        },
        chapters: {
          C1: {
            chapterId: "C1",
            chapterTitle: "第一章",
            chapterOrder: 1,
            queries: ["一章查询一", "一章查询二", "一章查询三"],
            docs: [[]],
            summary: "第一章总结",
          },
          C2: {
            chapterId: "C2",
            chapterTitle: "第二章",
            chapterOrder: 2,
            queries: ["二章查询一", "二章查询二", "二章查询三"],
            docs: [[]],
            summary: "第二章总结",
          },
        },
      },
    } as unknown as CHAT.Task;

    const rendered = processTaskForRender(task, "deep-search-");

    expect(rendered).toHaveLength(2);
    expect(rendered.map((item) => item.resultMap?.chapterId)).toEqual(["C1", "C2"]);
    expect(rendered.map((item) => item.resultMap?.chapterTitle)).toEqual([
      "第一章",
      "第二章",
    ]);
  });

  it("搜索早期只有 searchResult.chapters 时也按章节生成卡片", () => {
    const task = {
      messageType: "deep_search",
      messageTime: "1714041600001",
      resultMap: {
        messageType: "search",
        searchResult: {
          query: ["查询一", "查询二", "查询三", "查询四"],
          docs: [
            [
              {
                link: "a",
                title: "来源一",
              },
            ],
            [],
            [
              {
                link: "b",
                title: "来源二",
              },
            ],
            [],
          ],
          chapters: [
            {
              chapterId: "C1",
              chapterTitle: "第一章",
              chapterOrder: 1,
            },
            {
              chapterId: "C2",
              chapterTitle: "第二章",
              chapterOrder: 2,
            },
          ],
        },
      },
    } as unknown as CHAT.Task;

    const rendered = processTaskForRender(task, "deep-search-early-");

    expect(rendered).toHaveLength(2);
    expect(rendered.map((item) => item.resultMap?.chapterId)).toEqual(["C1", "C2"]);
  });
});
