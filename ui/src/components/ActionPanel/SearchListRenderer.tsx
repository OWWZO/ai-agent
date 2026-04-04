import React, { useMemo } from "react";
import { motion } from "motion/react";
import { ExternalLink, Link2, Search } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { jumpUrl } from "@/utils";

import type { SearchListItem } from "./type";

function hostnameFromUrl(url: string): string {
  if (!url) return "";
  try {
    const u = new URL(url.startsWith("http") ? url : `https://${url}`);
    return u.hostname.replace(/^www\./, "");
  } catch {
    return url.replace(/^https?:\/\//, "").split("/")[0] || url;
  }
}

function normalizeSnippet(text: string, title: string): string {
  const t = text?.trim() || "";
  if (!t) return "";
  // Avoid showing a stray leading fragment that duplicates title numbering (e.g. blogs).
  if (title && t.length < 24 && title.includes(t)) return "";
  return t;
}

interface SearchListRendererProps {
  list?: SearchListItem[];
}

const SearchListItemComponent: ReactorType.FC<SearchListItem & { index: number }> = React.memo(
  ({ name, pageContent, url, index }) => {
    const host = hostnameFromUrl(url);
    const snippet = normalizeSnippet(pageContent, name);

    return (
      <motion.article
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{
          duration: 0.22,
          delay: Math.min(index * 0.04, 0.24),
          ease: [0.25, 0.46, 0.45, 0.94],
        }}
        className={cn(
          "group cursor-pointer rounded-2xl px-4 py-4 transition-all duration-200",
          "bg-[var(--chat-surface-soft)]/65 shadow-[var(--shadow-xs)]",
          "hover:bg-[var(--chat-surface-muted)]/90 hover:shadow-[var(--shadow-sm)]"
        )}
        onClick={() => jumpUrl(url)}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            jumpUrl(url);
          }
        }}
        role="link"
        tabIndex={0}
      >
        <div className="flex gap-3.5">
          <div
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[var(--chat-surface)]/90 text-[var(--chat-text-muted)] transition-colors duration-200 group-hover:text-[var(--chat-text-soft)]"
            aria-hidden
          >
            <Link2 className="h-4 w-4" strokeWidth={1.75} />
          </div>
          <div className="min-w-0 flex-1">
            <h3
              className={cn(
                "text-[15px] font-medium leading-snug tracking-[-0.01em] text-[var(--chat-text)]",
                "transition-colors duration-200 group-hover:text-[#0071e3]"
              )}
            >
              {name || "未命名来源"}
            </h3>
            {snippet ? (
              <p className="mt-2 line-clamp-3 text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
                {snippet}
              </p>
            ) : null}
            {host ? (
              <div className="mt-3 flex items-center gap-1.5 font-mono text-[11px] tracking-tight text-[var(--chat-text-muted)]">
                <ExternalLink className="h-3 w-3 shrink-0 opacity-70" strokeWidth={2} />
                <span className="truncate">{host}</span>
              </div>
            ) : null}
          </div>
        </div>
      </motion.article>
    );
  }
);

SearchListItemComponent.displayName = "SearchListItemComponent";

/**
 * 工作区检索结果列表：与全局 chat 变量、Instrument 系排版一致，无描边，靠层级与阴影区分。
 */
const SearchListRenderer: ReactorType.FC<SearchListRendererProps> = React.memo(({ list }) => {
  const count = list?.length ?? 0;

  const subtitle = useMemo(() => {
    if (count <= 0) return "";
    return count === 1 ? "1 条来源" : `${count} 条来源`;
  }, [count]);

  if (!list?.length) {
    return (
      <div className="mx-auto flex w-full max-w-xl flex-col items-center justify-center px-4 py-16 text-center">
        <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-[var(--chat-surface-soft)] text-[var(--chat-text-muted)] shadow-[var(--shadow-xs)]">
          <Search className="h-6 w-6" strokeWidth={1.5} />
        </div>
        <p className="text-[15px] font-medium text-[var(--chat-text)]">暂无检索结果</p>
        <p className="mt-1 max-w-[280px] text-[13px] leading-relaxed text-[var(--chat-text-soft)]">
          任务返回后，匹配的网页来源会显示在这里。
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-2xl px-1 pb-8 pt-2">
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--chat-surface-soft)] text-[var(--chat-text-soft)] shadow-[var(--shadow-xs)]">
          <Search className="h-5 w-5" strokeWidth={1.75} />
        </div>
        <div className="min-w-0 flex-1">
          <p
            className="text-[11px] font-semibold uppercase tracking-[0.12em] text-[var(--chat-text-muted)]"
            style={{ fontFamily: "var(--font-sans)" }}
          >
            检索来源
          </p>
          <p className="mt-0.5 text-[15px] font-medium tracking-[-0.02em] text-[var(--chat-text)]">
            网页与文档
          </p>
        </div>
        {subtitle ? (
          <Badge variant="secondary" className="h-6 shrink-0 px-2.5 text-[11px] font-medium">
            {subtitle}
          </Badge>
        ) : null}
      </div>

      <div className="flex flex-col gap-3">
        {list.map((item, index) => (
          <SearchListItemComponent
            key={`${item.url}-${index}`}
            index={index}
            name={item.name}
            pageContent={item.pageContent}
            url={item.url}
          />
        ))}
      </div>
    </div>
  );
});

SearchListRenderer.displayName = "SearchListRenderer";

export default SearchListRenderer;
