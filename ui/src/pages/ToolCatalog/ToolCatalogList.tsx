import { Search } from "lucide-react";

import type { ToolCatalogItem, ToolCategory } from "./toolCatalog";
import type { ToolCatalogFilterCategory } from "./toolCatalogModel";
import { getToolFunctionCount } from "./toolCatalogModel";
import { getToolCatalogIcon } from "./toolCatalogIcons";

const categories: Array<{ value: ToolCatalogFilterCategory; label: string }> = [
  {
    value: "all",
    label: "全部",
  },
  {
    value: "social",
    label: "社交媒体",
  },
  {
    value: "research",
    label: "搜索与研究",
  },
  {
    value: "data",
    label: "数据分析",
  },
  {
    value: "document",
    label: "文档处理",
  },
  {
    value: "browser",
    label: "浏览器",
  },
  {
    value: "creation",
    label: "内容创作",
  },
  {
    value: "media",
    label: "音视频",
  },
  {
    value: "news",
    label: "新闻资讯",
  },
  {
    value: "commerce",
    label: "购物与游戏",
  },
  {
    value: "finance",
    label: "财经",
  },
];

const categoryLabels: Record<ToolCategory, string> = {
  social: "社交媒体",
  research: "搜索与研究",
  data: "数据分析",
  document: "文档处理",
  browser: "浏览器",
  creation: "内容创作",
  media: "音视频",
  news: "新闻资讯",
  commerce: "购物与游戏",
  finance: "财经",
};

export type ToolCatalogListProps = {
  items: readonly ToolCatalogItem[];
  category: ToolCatalogFilterCategory;
  query: string;
  onCategoryChange: (category: ToolCatalogFilterCategory) => void;
  onQueryChange: (query: string) => void;
  onSelectTool: (tool: ToolCatalogItem) => void;
};

export default function ToolCatalogList({
  items,
  category,
  query,
  onCategoryChange,
  onQueryChange,
  onSelectTool,
}: ToolCatalogListProps) {
  return (
    <div className="space-y-5">
      <section aria-label="工具筛选" className="space-y-3">
        <label className="relative block max-w-xl">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-faint)]"
          />
          <input
            type="search"
            aria-label="搜索工具"
            value={query}
            onChange={(event) => onQueryChange(event.currentTarget.value)}
            placeholder="搜索工具、功能或命令"
            className="h-10 w-full rounded-md border border-[var(--color-line)] bg-[var(--color-surface-raised)] pl-9 pr-3 text-[13px] text-[var(--color-text)] outline-none placeholder:text-[var(--color-text-faint)] focus-visible:border-[var(--color-accent)] focus-visible:ring-2 focus-visible:ring-[var(--color-accent-soft)]"
          />
        </label>
        <div className="flex flex-wrap gap-1.5" role="tablist" aria-label="工具分类">
          {categories.map((entry) => (
            <button
              key={entry.value}
              type="button"
              role="tab"
              aria-selected={category === entry.value}
              data-active={category === entry.value}
              onClick={() => onCategoryChange(entry.value)}
              className="workspace-admin-tab"
            >
              {entry.label}
            </button>
          ))}
        </div>
      </section>

      <section aria-label="工具列表">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="workspace-admin-section-title">可用工具</h2>
          <span className="workspace-admin-section-meta">{items.length} 项</span>
        </div>
        {items.length > 0 ? (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,250px),1fr))] gap-3">
            {items.map((tool) => {
              const Icon = getToolCatalogIcon(tool.iconKey);
              return (
                <button
                  key={tool.id}
                  type="button"
                  aria-label={`打开工具 ${tool.title}${tool.requiresLogin === false ? "，无需登录" : ""}`}
                  onClick={() => onSelectTool(tool)}
                  className="group flex min-h-[148px] min-w-0 flex-col items-start rounded-md border border-[var(--color-line)] bg-[var(--color-surface-raised)] p-4 text-left transition-colors hover:border-[var(--color-line-strong)] hover:bg-[var(--color-surface)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]"
                >
                  <span className="mb-3 inline-flex h-9 w-9 items-center justify-center rounded-md border border-[var(--color-accent-bd)] bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
                    <Icon aria-hidden="true" className="h-[18px] w-[18px]" />
                  </span>
                  <span className="flex w-full min-w-0 items-center justify-between gap-2">
                    <span className="min-w-0 truncate text-sm font-medium text-[var(--color-text)]">
                      {tool.title}
                    </span>
                    {tool.requiresLogin === false ? (
                      <span className="shrink-0 rounded-sm border border-[var(--color-line)] bg-[var(--status-success-bg)] px-1.5 py-0.5 text-[10px] font-medium leading-4 text-[var(--status-success-text)]">
                        无需登录
                      </span>
                    ) : null}
                  </span>
                  <span className="mt-1 line-clamp-2 min-h-10 w-full text-left text-[12px] leading-5 text-[var(--color-text-muted)]">
                    {tool.description}
                  </span>
                  <span className="mt-auto flex w-full items-center justify-between gap-2 pt-3 text-[11px] text-[var(--color-text-faint)]">
                    <span>{categoryLabels[tool.category]}</span>
                    <span>{getToolFunctionCount(tool)} 项功能</span>
                  </span>
                </button>
              );
            })}
          </div>
        ) : (
          <div className="workspace-admin-dashed-empty">没有匹配的工具</div>
        )}
      </section>
    </div>
  );
}
