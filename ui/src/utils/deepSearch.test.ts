import { describe, expect, it } from "vitest";

import {
  buildDeepSearchPreviewModel,
  buildDeepSearchExtendMarkdown,
  buildDeepSearchResultItems,
  formatDeepSearchQueryText,
  resolveDeepSearchActionText,
  resolveDeepSearchStage,
  resolveDeepSearchTitle,
  shouldRenderDeepSearchPreview,
  shouldRenderDeepSearchWorkspace,
} from "./deepSearch";

type DeepSearchPreviewTask = Pick<CHAT.Task, "messageType" | "resultMap">;
type DeepSearchPreviewSearchResult = NonNullable<
  CHAT.Task["resultMap"]["searchResult"]
>;

function createDoc(link: string, title: string, content: string): MESSAGE.Doc {
  return {
    link,
    doc_type: "web",
    title,
    content,
  };
}

function createPreviewTask(
  stage: "extend" | "search" | "report",
  searchResult: DeepSearchPreviewSearchResult
): DeepSearchPreviewTask {
  return {
    messageType: "deep_search",
    resultMap: {
      messageType: stage,
      searchResult,
    } as CHAT.Task["resultMap"],
  };
}

describe("deepSearch utils", () => {
  it("优先使用显式阶段，并在缺失时回退到事件子类型", () => {
    expect(resolveDeepSearchStage("extend", "search")).toBe("extend");
    expect(resolveDeepSearchStage(undefined, "report")).toBe("report");
    expect(resolveDeepSearchStage(undefined, "unknown")).toBe("search");
  });

  it("统一格式化查询分解文本和标题", () => {
    expect(formatDeepSearchQueryText([" 子问题A ", "", "子问题B "])).toBe(
      "子问题A / 子问题B"
    );
    expect(resolveDeepSearchTitle("extend", ["子问题A", "子问题B"])).toBe(
      "搜索中：子问题A / 子问题B"
    );
  });

  it("为 extend 阶段生成可直接展示的 Markdown 内容", () => {
    const markdown = buildDeepSearchExtendMarkdown(["问题A", "问题B"]);

    expect(markdown).toContain("已完成查询分解");
    expect(markdown).toContain("- 问题A");
    expect(markdown).toContain("- 问题B");
  });

  it("快照态的 extend 阶段仍保持正在搜索文案", () => {
    expect(resolveDeepSearchActionText("extend", true)).toBe("正在搜索");
  });

  it("区分左侧预览阶段和右侧工作区阶段", () => {
    expect(shouldRenderDeepSearchPreview("extend")).toBe(true);
    expect(shouldRenderDeepSearchPreview("search")).toBe(true);
    expect(shouldRenderDeepSearchPreview("report")).toBe(false);
    expect(shouldRenderDeepSearchWorkspace("extend")).toBe(false);
    expect(shouldRenderDeepSearchWorkspace("search")).toBe(true);
    expect(shouldRenderDeepSearchWorkspace("report")).toBe(true);
  });

  it("为 extend 阶段构建左侧紧凑查询预览模型", () => {
    const model = buildDeepSearchPreviewModel(
      createPreviewTask("extend", {
        query: ["问题A"],
        docs: [],
      })
    );

    expect(model).toMatchObject({
      stage: "extend",
      query: "问题A",
      statusLabel: "正在搜索",
      loading: true,
      interactive: false,
      resultCount: 0,
    });
    expect(model?.description).toContain("查询分解");
  });

  it("为 search 阶段构建左侧完成态预览，并为右侧工作区去重来源", () => {
    const duplicateDoc = createDoc("https://example.com/a", "来源A", "内容A");
    const model = buildDeepSearchPreviewModel(
      createPreviewTask("search", {
        query: ["问题A"],
        docs: [
          duplicateDoc,
          duplicateDoc,
          createDoc("https://example.com/b", "来源B", "内容B"),
        ],
      }),
    );
    const resultItems = buildDeepSearchResultItems([
      duplicateDoc,
      duplicateDoc,
      createDoc("https://example.com/b", "来源B", "内容B"),
    ]);

    expect(model).toMatchObject({
      stage: "search",
      query: "问题A",
      statusLabel: "搜索完成",
      loading: false,
      interactive: true,
      resultCount: 2,
    });
    expect(model?.description).toContain("点击查看右侧详情");
    expect(resultItems).toHaveLength(2);
  });

  it("report 阶段不会继续生成查询预览模型", () => {
    const model = buildDeepSearchPreviewModel(
      createPreviewTask("report", {
        query: ["问题A"],
        docs: [],
      })
    );

    expect(model).toBeUndefined();
  });
});
