import { describe, expect, it } from "vitest";

import { getTaskRenderSignature, processTaskForRender } from "./renderTasks";

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

describe("getTaskRenderSignature 缓存失效契约", () => {
  const base = {
    messageId: "m1",
    messageType: "tool_call",
    messageTime: "1714041600000",
    resultMap: {
      messageType: "tool_call",
      toolName: "workspace_read",
      toolCallId: "c1",
      argumentsText: '{"path":"a.ts"}',
    },
  } as unknown as Parameters<typeof getTaskRenderSignature>[0];

  const sig = (patch: Record<string, unknown>, baseId = "m1") =>
    getTaskRenderSignature({ ...base, ...patch } as never, baseId);

  it("同一任务两次求值稳定", () => {
    expect(getTaskRenderSignature(base, "m1")).toBe(
      getTaskRenderSignature(base, "m1")
    );
  });

  it("baseId 变化即使任务相同也失效", () => {
    expect(getTaskRenderSignature(base, "m1")).not.toBe(
      getTaskRenderSignature(base, "m2")
    );
  });

  it.each([
    ["messageId", { messageId: "m2" }],
    ["messageType", { messageType: "tool_result" }],
    ["messageTime", { messageTime: "1714041600001" }],
    ["resultMap.status", { resultMap: { ...base.resultMap, status: "running" } }],
    ["resultMap.summary", { resultMap: { ...base.resultMap, summary: "s" } }],
    ["resultMap.isFinal", { resultMap: { ...base.resultMap, isFinal: true } }],
    [
      "argumentsText 增长",
      { resultMap: { ...base.resultMap, argumentsText: '{"path":"a.ts","x":1}' } },
    ],
    [
      "argumentsText 相同长度但内容不同",
      { resultMap: { ...base.resultMap, argumentsText: '{"path":"b.ts"}' } },
    ],
    ["toolThought", { toolThought: "想一下" }],
    ["plan.stepStatus", { plan: { steps: [], stepStatus: ["completed"] } }],
  ])("展示字段 %s 变化必须使签名失效", (_label, patch) => {
    expect(sig(patch)).not.toBe(getTaskRenderSignature(base, "m1"));
  });

  it("artifactRefs 变化必须使签名失效", () => {
    const withRefs = sig({
      artifactRefs: [{ resourceKey: "k1", previewUrl: "u1" }],
    });
    expect(withRefs).not.toBe(getTaskRenderSignature(base, "m1"));
    // 同一 key 的另一个 URL 也要失效
    expect(
      sig({ artifactRefs: [{ resourceKey: "k1", previewUrl: "u2" }] })
    ).not.toBe(withRefs);
  });
});
