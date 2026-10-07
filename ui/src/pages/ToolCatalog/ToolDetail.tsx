import { ArrowLeft, ArrowUpRight } from "lucide-react";

import { Button } from "@/components/ui/button";

import type { ToolCatalogItem, ToolFunction } from "./toolCatalog";
import { getToolCatalogIcon } from "./toolCatalogIcons";

export type ToolDetailProps = {
  tool: ToolCatalogItem;
  onBack: () => void;
  onSelectFunction: (toolFunction: ToolFunction) => void;
};

export default function ToolDetail({ tool, onBack, onSelectFunction }: ToolDetailProps) {
  const Icon = getToolCatalogIcon(tool.iconKey);

  return (
    <section className="min-w-0 space-y-5" aria-labelledby="tool-detail-title">
      <Button
        type="button"
        variant="ghost"
        className="-ml-2 text-[var(--color-text-muted)]"
        onClick={onBack}
      >
        <ArrowLeft aria-hidden="true" />
        返回工具列表
      </Button>

      <div className="flex min-w-0 items-start gap-3 border-b border-[var(--color-line)] pb-4">
        <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-[var(--color-line)] bg-[var(--color-surface-raised)] text-[var(--color-accent)]">
          <Icon aria-hidden="true" className="h-5 w-5" />
        </span>
        <div className="min-w-0">
          <h2 id="tool-detail-title" className="text-base font-semibold text-[var(--color-text)]">
            {tool.title}
          </h2>
          <p className="mt-1 max-w-3xl break-words text-[13px] leading-5 text-[var(--color-text-muted)]">
            {tool.description}
          </p>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3">
        <h3 className="workspace-admin-section-title">功能</h3>
        <span className="workspace-admin-section-meta">{tool.functions.length} 项</span>
      </div>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(100%,270px),1fr))] gap-3">
        {tool.functions.map((toolFunction) => (
          <button
            key={toolFunction.id}
            type="button"
            aria-label={`配置任务：${toolFunction.title}`}
            onClick={() => onSelectFunction(toolFunction)}
            className="flex min-h-[192px] min-w-0 flex-col items-start rounded-md border border-[var(--color-line)] bg-[var(--color-surface-raised)] p-4 text-left transition-colors hover:border-[var(--color-line-strong)] hover:bg-[var(--color-surface)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]"
          >
            <span className="flex w-full items-start justify-between gap-3">
              <span className="text-sm font-medium leading-5 text-[var(--color-text)]">
                {toolFunction.title}
              </span>
              <ArrowUpRight aria-hidden="true" className="h-4 w-4 shrink-0 text-[var(--color-text-faint)]" />
            </span>
            {toolFunction.access === "write" ? (
              <span className="mt-2 rounded-sm border border-rose-300 bg-rose-50 px-2 py-0.5 text-[11px] font-semibold text-rose-800 dark:border-rose-800 dark:bg-rose-950 dark:text-rose-200">
                写入操作
              </span>
            ) : null}
            <span className="mt-2 line-clamp-3 min-h-[60px] w-full break-words text-[12px] leading-5 text-[var(--color-text-muted)]">
              {toolFunction.description}
            </span>
            <span className="mt-auto w-full border-t border-[var(--color-line)] pt-3 text-left">
              <span className="block text-[10px] font-medium uppercase text-[var(--color-text-faint)]">
                {tool.kind === "opencli-site" ? "OpenCLI 命令" : "BaseTool 名称"}
              </span>
              <code className="mt-1 block break-all font-mono text-[11px] text-[var(--color-text)]">
                {toolFunction.command ?? toolFunction.id}
              </code>
            </span>
          </button>
        ))}
      </div>
    </section>
  );
}
