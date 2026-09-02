import { memo } from "react";
import { ArrowLeft, X } from "lucide-react";
import { DiffCodeFence } from "./markdown/KimiCodeFence";
import { ToolOutputBlock } from "./tools/ToolOutputBlock";

type ToolDiffPanelProps = {
  title: string;
  path?: string;
  diffCode: string | null;
  output: string[];
  status?: "running" | "ok" | "error";
  onBack?: () => void;
  onClose?: () => void;
};

export const ToolDiffPanel = memo(function ToolDiffPanel({
  title,
  path,
  diffCode,
  output,
  status = "ok",
  onBack,
  onClose,
}: ToolDiffPanelProps) {
  const showDiff = Boolean(diffCode) && status !== "error";

  return (
    <div className="kimi-detail-panel">
      <div className="kimi-detail-panel-head">
        <div className="min-w-0 flex-1">
          <div className="text-[12px] text-[var(--color-text-faint)]">预览</div>
          <div className="truncate text-[14px] font-medium text-[var(--color-text)]">
            {title}
          </div>
          {path ? (
            <div className="truncate font-mono text-[12px] text-[var(--color-text-muted)]">
              {path}
            </div>
          ) : null}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {onBack ? (
            <button
              type="button"
              className="flex h-7 w-7 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-sunken)] hover:text-[var(--color-text)]"
              title="返回子 Agent"
              aria-label="返回子 Agent"
              onClick={onBack}
            >
              <ArrowLeft className="h-4 w-4" />
            </button>
          ) : null}
          <button
            type="button"
            className="flex h-7 w-7 items-center justify-center rounded-full text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-sunken)] hover:text-[var(--color-text)]"
            aria-label="关闭"
            onClick={onClose}
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>
      <div className="kimi-detail-panel-body">
        {showDiff && diffCode ? (
          <DiffCodeFence code={diffCode} />
        ) : output.length > 0 ? (
          <div className="px-3 py-2">
            <ToolOutputBlock
              lines={output}
              emptyText={status === "error" ? "工具执行失败" : "No output"}
            />
          </div>
        ) : (
          <div className="kimi-detail-panel-empty">
            {status === "error" ? "工具执行失败，无可用 diff" : "暂无 diff"}
          </div>
        )}
      </div>
    </div>
  );
});
