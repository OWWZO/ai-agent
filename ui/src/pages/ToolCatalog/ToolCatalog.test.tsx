import { isValidElement, type ReactElement, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import ToolCatalog from "./index";
import ToolCatalogList from "./ToolCatalogList";
import ToolDetail from "./ToolDetail";
import ToolTaskDialog from "./ToolTaskDialog";
import { TOOL_CATALOG, type ToolCatalogItem, type ToolFunction } from "./toolCatalog";
import {
  filterToolCatalog,
  initializeToolFieldValues,
} from "./toolCatalogModel";
import { submitToolTask } from "./toolTaskSubmission";

type TestElementProps = {
  children?: ReactNode;
  onClick?: () => void;
  onChange?: (event: { currentTarget: { value: string } }) => void;
  "aria-label"?: string;
  type?: string;
};

function findElement(
  node: ReactNode,
  predicate: (element: ReactElement<TestElementProps>) => boolean,
): ReactElement<TestElementProps> | undefined {
  if (Array.isArray(node)) {
    for (const child of node) {
      const found = findElement(child, predicate);
      if (found) return found;
    }
    return undefined;
  }
  if (!isValidElement(node)) return undefined;

  const element = node as ReactElement<TestElementProps>;
  if (predicate(element)) return element;
  return findElement(element.props.children, predicate);
}

function getTool(id: string): ToolCatalogItem {
  const tool = TOOL_CATALOG.find((item) => item.id === id);
  if (!tool) throw new Error(`Missing tool ${id}`);
  return tool;
}

function getFunction(tool: ToolCatalogItem, command: string): ToolFunction {
  const toolFunction = tool.functions.find((entry) => entry.command === command);
  if (!toolFunction) throw new Error(`Missing function ${command}`);
  return toolFunction;
}

describe("ToolCatalog", () => {
  it("renders the default tool list with the requested categories and search control", () => {
    const html = renderToStaticMarkup(
      <ToolCatalog embedded onStartToolTask={() => {}} />,
    );

    expect(html).toContain("工具目录");
    expect(html).toContain("搜索工具");
    expect(html).toContain("社交媒体");
    expect(html).toContain("搜索与研究");
    expect(html).toContain("数据分析");
    expect(html).toContain("文档处理");
    expect(html).toContain("浏览器");
    expect(html).toContain("内容创作");
    expect(html).toContain("音视频");
    expect(html).toContain("新闻资讯");
    expect(html).toContain("购物与游戏");
    expect(html).toContain("财经");
    expect(html).toContain("浏览器能力");
  });

  it("filters list entries by category and case-insensitive keyword", () => {
    const filtered = filterToolCatalog(TOOL_CATALOG, "research", "WEBSEARCH");
    expect(filtered.map((item) => item.id)).toEqual(["base-research"]);

    const searchHtml = renderToStaticMarkup(
      <ToolCatalogList
        items={filtered}
        category="research"
        query="WEBSEARCH"
        onCategoryChange={() => {}}
        onQueryChange={() => {}}
        onSelectTool={() => {}}
      />,
    );
    expect(searchHtml).toContain("搜索与研究");
    expect(searchHtml).not.toContain("抖音");
  });

  it("opens a tool detail from a list card", () => {
    const onSelectTool = vi.fn();
    const list = ToolCatalogList({
      items: [getTool("base-research")],
      category: "all",
      query: "",
      onCategoryChange: () => {},
      onQueryChange: () => {},
      onSelectTool,
    });
    const card = findElement(
      list,
      (element) => element.props["aria-label"] === "打开工具 搜索与研究",
    );

    expect(card).toBeDefined();
    card?.props.onClick?.();
    expect(onSelectTool).toHaveBeenCalledWith(getTool("base-research"));
  });

  it("marks public OpenCLI sites as not requiring login on their cards", () => {
    const html = renderToStaticMarkup(
      <ToolCatalogList
        items={[getTool("opencli-aibase")]}
        category="all"
        query=""
        onCategoryChange={() => {}}
        onQueryChange={() => {}}
        onSelectTool={() => {}}
      />,
    );

    expect(html).toContain("无需登录");
    expect(html).toContain("aria-label=\"打开工具 AIBase，无需登录\"");
  });

  it("opens a task form from a function card and shows command names in detail", () => {
    const tool = getTool("opencli-xiaohongshu");
    const publish = getFunction(tool, "xiaohongshu/publish");
    const onSelectFunction = vi.fn();
    const detail = ToolDetail({
      tool,
      onBack: () => {},
      onSelectFunction,
    });
    const card = findElement(
      detail,
      (element) => element.props["aria-label"] === "配置任务：发布笔记",
    );

    expect(renderToStaticMarkup(detail)).toContain("xiaohongshu/publish");
    expect(renderToStaticMarkup(detail)).toContain("写入操作");
    card?.props.onClick?.();
    expect(onSelectFunction).toHaveBeenCalledWith(publish);
  });

  it("shows a field-level required error and the local-browser write warning", async () => {
    const tool = getTool("opencli-xiaohongshu");
    const publish = getFunction(tool, "xiaohongshu/publish");
    const html = renderToStaticMarkup(
      <ToolTaskDialog
        tool={tool}
        toolFunction={publish}
        onClose={() => {}}
        onStartToolTask={() => {}}
        onSubmitted={() => {}}
      />,
    );

    expect(html).toContain("此任务会操作用户本机浏览器。");
    expect(html).toContain("笔记标题");
    expect(html).toContain("开始任务");
    expect(html).not.toContain("登录状态");

    const onStartToolTask = vi.fn();
    const errors = await submitToolTask(
      publish,
      initializeToolFieldValues(publish.fields),
      onStartToolTask,
    );
    expect(errors.title).toBe("此字段为必填项");
    expect(errors.content).toBe("此字段为必填项");
    expect(onStartToolTask).not.toHaveBeenCalled();
  });

  it("builds a draft, keeps files outside the message, deduplicates them, and calls the parent", async () => {
    const tool = getTool("base-document-read");
    const toolFunction = getFunction(tool, "csv_processor");
    const repeatedFileFunction: ToolFunction = {
      ...toolFunction,
      fields: [
        ...toolFunction.fields,
        {
          ...toolFunction.fields[0]!,
          name: "same_file",
        },
      ],
    };
    const file = new File(["a,b\n1,2"], "data.csv", { type: "text/csv" });
    const values = {
      file,
      same_file: file,
      operation: "read",
      row_limit: 200,
    };
    const onStartToolTask = vi.fn();

    const errors = await submitToolTask(
      repeatedFileFunction,
      values,
      onStartToolTask,
    );

    expect(errors).toEqual({});
    expect(onStartToolTask).toHaveBeenCalledOnce();
    const draft = onStartToolTask.mock.calls[0]?.[0];
    expect(draft?.message).toContain("BaseTool 的 csv_processor");
    expect(draft?.message).not.toContain("data.csv");
    expect(draft?.files).toEqual([file]);
  });

  it("keeps the form callback failure observable so entered values remain in the form", async () => {
    const tool = getTool("base-research");
    const search = getFunction(tool, "WebSearch");
    const values = {
      query: "release notes",
      max_results: 5,
      recency: "any",
    };

    await expect(
      submitToolTask(search, values, () => {
        throw new Error("主页不可用");
      }),
    ).rejects.toThrow("主页不可用");
    expect(values.query).toBe("release notes");
  });
});
