import { FC, memo, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Maximize2, Sparkles, X } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import classNames from "classnames";
import GenUiNode, { type GenUiNodeData } from "./GenUiNode";
import { GenUiRenderProvider } from "./GenUiRenderContext";
import { applyUiPatches } from "./applyUiPatch";
import { DURATION, EASE_OUT } from "@/lib/motion";

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
    const [patchFlash, setPatchFlash] = useState(false);
    const reduceMotion = useReducedMotion();

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

    useEffect(() => {
      if (typeof patchCount !== "number" || patchCount <= 0) return;
      if (reduceMotion) return;
      setPatchFlash(true);
      const t = window.setTimeout(() => setPatchFlash(false), 700);
      return () => window.clearTimeout(t);
    }, [patchCount, reduceMotion]);

    if (!root) return null;

    const body = (
      <GenUiRenderProvider value={{ sessionId, messageId }}>
        <motion.div
          className={classNames(
            "genui-root",
            patchFlash && "genui-patch-flash"
          )}
          initial={reduceMotion ? false : { opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: DURATION.message, ease: EASE_OUT }}
        >
          <GenUiNode node={root} />
        </motion.div>
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
              <button
                type="button"
                aria-label="关闭沉浸模式"
                className="absolute inset-0 border-0 bg-[var(--chat-text)]/35 backdrop-blur-[6px]"
                onClick={() => setImmersive(false)}
              />

              <div
                className="relative z-[1] flex h-[min(92vh,960px)] w-full max-w-[min(1280px,96vw)] flex-col overflow-hidden rounded-[20px] border border-[var(--chat-border)] bg-[var(--chat-surface-soft)] shadow-[var(--shadow-elevated)]"
                onClick={(e) => e.stopPropagation()}
              >
                <div className="flex shrink-0 items-center justify-between gap-3 border-b border-[var(--chat-border)] bg-[var(--chat-surface)] px-4 py-3 sm:px-5">
                  <div className="flex min-w-0 items-center gap-2">
                    <span className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[var(--chat-accent-soft)] text-[var(--chat-accent)]">
                      <Sparkles className="size-3.5" strokeWidth={2.2} />
                    </span>
                    <div className="min-w-0">
                      <div className="truncate text-[14px] font-semibold tracking-tight text-[var(--chat-text)]">
                        Generative UI
                      </div>
                      {typeof patchCount === "number" && patchCount > 0 ? (
                        <div className="truncate text-[11px] text-[var(--chat-text-soft)]">
                          已合并 {patchCount} 条补丁
                        </div>
                      ) : null}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setImmersive(false)}
                    className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-[var(--chat-text-soft)] transition hover:bg-[var(--chat-interactive-hover)] hover:text-[var(--chat-text)]"
                    title="关闭（Esc）"
                  >
                    <X className="size-4" />
                  </button>
                </div>

                <div className="min-h-0 flex-1 overflow-auto p-3 sm:p-4 md:p-5">
                  <div className="min-h-full rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)] p-4 shadow-[var(--shadow-xs)] sm:p-6 md:p-8">
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
          <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-medium tracking-wide text-[var(--chat-text-muted)]">
                交互演示
              </span>
              {typeof patchCount === "number" && patchCount > 0 ? (
                <span
                  className={classNames(
                    "rounded-full bg-[var(--chat-surface-muted)] px-2 py-0.5 text-[11px] text-[var(--chat-text-soft)] transition-colors",
                    patchFlash && "bg-[var(--chat-accent-soft)] text-[var(--chat-accent)]"
                  )}
                >
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
