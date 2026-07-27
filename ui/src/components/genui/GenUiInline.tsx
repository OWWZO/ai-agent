import { FC, memo, useMemo, useState } from "react";
import { Download, FileText, LoaderCircle } from "lucide-react";
import GenUiNode, { type GenUiNodeData } from "./GenUiNode";
import { applyUiPatches } from "./applyUiPatch";
import { exportGenUiFile } from "@/services/genuiExport";
import { showMessage } from "@/utils/utils";

type Props = {
  tree?: any;
  patches?: Array<{ op: string; path: string; value?: unknown }>;
  className?: string;
  /** Show PDF/DOCX export actions */
  showExport?: boolean;
  patchCount?: number;
};

const GenUiInline: FC<Props> = memo(
  ({ tree, patches, className, showExport = true, patchCount }) => {
    const [exporting, setExporting] = useState<"pdf" | "docx" | null>(null);

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

    const onExport = async (format: "pdf" | "docx") => {
      if (!resolved || exporting) return;
      setExporting(format);
      try {
        await exportGenUiFile({ format, tree: resolved, mode: "document" });
        showMessage()?.success(format === "pdf" ? "PDF 已开始下载" : "Word 已开始下载");
      } catch (e: any) {
        showMessage()?.error(e?.message || "导出失败");
      } finally {
        setExporting(null);
      }
    };

    if (!root) return null;

    return (
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
          {showExport ? (
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                disabled={!!exporting}
                onClick={() => onExport("pdf")}
                className="inline-flex h-7 items-center gap-1 rounded-md border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] px-2 text-[12px] text-[var(--chat-text)] transition hover:bg-[var(--chat-surface-muted)] disabled:opacity-50"
                title="导出 PDF"
              >
                {exporting === "pdf" ? (
                  <LoaderCircle className="size-3.5 animate-spin" />
                ) : (
                  <Download className="size-3.5" />
                )}
                PDF
              </button>
              <button
                type="button"
                disabled={!!exporting}
                onClick={() => onExport("docx")}
                className="inline-flex h-7 items-center gap-1 rounded-md border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] px-2 text-[12px] text-[var(--chat-text)] transition hover:bg-[var(--chat-surface-muted)] disabled:opacity-50"
                title="导出 Word"
              >
                {exporting === "docx" ? (
                  <LoaderCircle className="size-3.5 animate-spin" />
                ) : (
                  <FileText className="size-3.5" />
                )}
                DOCX
              </button>
            </div>
          ) : null}
        </div>
        <GenUiNode node={root} />
      </div>
    );
  }
);

GenUiInline.displayName = "GenUiInline";

export default GenUiInline;
