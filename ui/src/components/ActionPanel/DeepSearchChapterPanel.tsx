import { memo } from "react";
import { BookOpenText, ListTree } from "lucide-react";
import MarkdownRenderer from "./MarkdownRenderer";
import SearchListRenderer from "./SearchListRenderer";
import type { DeepSearchChapterWorkspaceModel } from "@/types/deepSearch";

type DeepSearchChapterPanelProps = {
  model: DeepSearchChapterWorkspaceModel;
};

/**
 * 章节工作区：上方该章检索来源列表，下方章节总结。
 */
const DeepSearchChapterPanel: ReactorType.FC<DeepSearchChapterPanelProps> = memo(
  ({ model }) => {
    const orderLabel =
      typeof model.order === "number" && model.order > 0
        ? `第 ${model.order} 章`
        : "章节";

    return (
      <div className="mx-auto flex h-full w-full max-w-2xl flex-col gap-6 px-1 pb-8 pt-2">
        <section>
          <div className="mb-3 flex items-center gap-2 px-1">
            <ListTree className="h-4 w-4 text-[var(--chat-text-muted)]" strokeWidth={1.75} />
            <p className="text-[12px] font-medium text-[var(--chat-text-muted)]">
              本章检索来源
            </p>
          </div>
          <SearchListRenderer
            list={model.sources}
            eyebrow="章节来源"
            title="网页与文档"
            emptyTitle="暂无本章来源"
            emptyDescription="该章节还没有可展示的检索结果。"
          />
        </section>

        <section className="rounded-2xl border border-[var(--chat-border)]/50 bg-white px-4 py-4">
          <div className="mb-3 flex items-start gap-3">
            <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[var(--chat-border)]/40 bg-[#f5f5f7] text-[var(--chat-text-muted)]">
              <BookOpenText className="h-5 w-5" strokeWidth={1.75} />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--chat-text-muted)]">
                {orderLabel}
              </p>
              <h3 className="mt-1 text-[16px] font-semibold leading-snug tracking-[-0.01em] text-[var(--chat-text)]">
                {model.title}
              </h3>
            </div>
          </div>
          {model.summary ? (
            <div className="rounded-xl bg-[#fafafa] px-3 py-2">
              <MarkdownRenderer
                markDownContent={model.summary}
                isStreaming={Boolean(model.isStreaming)}
              />
            </div>
          ) : (
            <p className="text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
              章节总结生成中，可先查看上方检索来源。
            </p>
          )}
        </section>
      </div>
    );
  }
);

DeepSearchChapterPanel.displayName = "DeepSearchChapterPanel";

export default DeepSearchChapterPanel;
