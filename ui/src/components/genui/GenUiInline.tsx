import { FC, memo, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Maximize2, Sparkles, X } from "lucide-react";
import GenUiNode, { type GenUiNodeData } from "./GenUiNode";
import { GenUiRenderProvider } from "./GenUiRenderContext";
import { applyUiPatches } from "./applyUiPatch";

type Props = {
  tree?: any;
  patches?: Array<{ op: string; path: string; value?: unknown }>;
  className?: string;
  patchCount?: number;
  sessionId?: string;
  messageId?: string;
};

const GenUiInline: FC<Props> = memo(
  ({ tree, patches, className, patchCount, sessionId, messageId }) => {
    const [immersive, setImmersive] = useState(false);

    const resolved = useMemo(() => {
      if (!tree) return null;
      if (Array.isArray(patches) && patches.length) {
        return applyUiPatches(tree, patches);
      }
      return tree;
    }, [tree, patches]);

    const root: GenUiNodeData | null = useMemo(() => {
      if (!resolved) return null;
      if (resolved.root) return resolved.root as GenUiNodeData;
      if (resolved.kind) return resolved as GenUiNodeData;
      return null;
    }, [resolved]);

    useEffect(() => {
      if (!immersive) return;
      const onKey = (e: KeyboardEvent) => {
        if (e.key === "Escape") setImmersive(false);
      };
      window.addEventListener("keydown", onKey);
      const prev = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      return () => {
        window.removeEventListener("keydown", onKey);
        document.body.style.overflow = prev;
      };
    }, [immersive]);

    if (!root) return null;

    const body = (
      <GenUiRenderProvider value={{ sessionId, messageId }}>
        <GenUiNode node={root} />
      </GenUiRenderProvider>
    );

    const stage =
      immersive && typeof document !== "undefined"
        ? createPortal(
            <div
              className="fixed inset-0 z-[1000] flex items-center justify-center p-3 sm:p-5 md:p-8"
              role="dialog"
              aria-modal="true"
              aria-label="Generative UI"
            >
              {/* 毛玻璃遮罩 — 点击关闭 */}
              <button
                type="button"
                aria-label="关闭沉浸模式"
                className="absolute inset-0 border-0 bg-black/35 backdrop-blur-[6px]"
                onClick={() => setImmersive(false)}
              />

              {/* 大屏卡片 */}
              <div
                className="relative z-[1] flex h-[min(92vh,960px)] w-full max-w-[min(1280px,96vw)] flex-col overflow-hidden rounded-[20px] border border-white/70 bg-[#f7f7f8] shadow-[0_32px_80px_-24px_rgba(15,23,42,0.45)]"
                onClick={(e) => e.stopPropagation()}
              >
                {/* 顶栏 */}
                <div className="flex shrink-0 items-center justify-between gap-3 border-b border-black/[0.06] bg-white px-4 py-3 sm:px-5">
                  <div className="flex min-w-0 items-center gap-2">
                    <span className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[#e8f1ff] text-[#2f7cf6]">
                      <Sparkles className="size-3.5" strokeWidth={2.2} />
                    </span>
                    <div className="min-w-0">
                      <div className="truncate text-[14px] font-semibold tracking-tight text-[#1d1d1f]">
                        Generative UI
                      </div>
                      {typeof patchCount === "number" && patchCount > 0 ? (
                        <div className="truncate text-[11px] text-[#86868b]">
                          已合并 {patchCount} 条补丁
                        </div>
                      ) : null}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setImmersive(false)}
                    className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-[#86868b] transition hover:bg-black/[0.04] hover:text-[#1d1d1f]"
                    title="关闭（Esc）"
                  >
                    <X className="size-4" />
                  </button>
                </div>

                {/* 内容区：内层白卡 + 可滚动 */}
                <div className="min-h-0 flex-1 overflow-auto p-3 sm:p-4 md:p-5">
                  <div className="min-h-full rounded-2xl border border-black/[0.05] bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.04)] sm:p-6 md:p-8">
                    {body}
                  </div>
                </div>
              </div>
            </div>,
            document.body
          )
        : null;

    return (
      <>
        <div className={className || "mt-3 w-full max-w-3xl"}>
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-medium uppercase tracking-wide text-[var(--chat-text-soft)]">
                GenUI
              </span>
              {typeof patchCount === "number" && patchCount > 0 ? (
                <span className="rounded-full bg-[var(--chat-surface-muted)] px-2 py-0.5 text-[11px] text-[var(--chat-text-soft)]">
                  已合并 {patchCount} 条补丁
                </span>
              ) : null}
            </div>
            <button
              type="button"
              onClick={() => setImmersive(true)}
              className="inline-flex h-7 items-center gap-1 rounded-md border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] px-2 text-[12px] text-[var(--chat-text)] transition hover:bg-[var(--chat-surface-muted)]"
              title="进入沉浸模式"
            >
              <Maximize2 className="size-3.5" />
              沉浸
            </button>
          </div>
          {body}
        </div>
        {stage}
      </>
    );
  }
);

GenUiInline.displayName = "GenUiInline";

export default GenUiInline;
