import { useMemo, useState } from "react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

export type ContextUsageView = {
  max: number;
  /** 模型返回的真实 prompt_tokens */
  promptTokens?: number;
};

type Props = {
  /** SSE 推送的模型真实 prompt_tokens */
  usage?: ContextUsageView | null;
  /** 上下文窗口上限 fallback */
  contextWindow?: number | null;
  className?: string;
};

const R = 9;
const STROKE = 3;
const CIRC = 2 * Math.PI * R;

function formatTokens(n: number) {
  if (n >= 1_000_000) {
    const v = n / 1_000_000;
    return `${v >= 10 ? v.toFixed(0) : v.toFixed(1)}M`;
  }
  if (n >= 1000) {
    const v = n / 1000;
    return `${v >= 100 ? v.toFixed(0) : v.toFixed(v >= 10 ? 0 : 1)}k`;
  }
  return String(Math.round(n));
}

/**
 * 上下文占用环：只展示模型返回的真实 prompt_tokens。
 * 展开面板走 Portal，避免被输入框 overflow 裁切。
 */
const ContextRing: ReactorType.FC<Props> = ({
  usage,
  contextWindow,
  className,
}) => {
  const [open, setOpen] = useState(false);

  const model = useMemo(() => {
    const max =
      usage && usage.max > 0
        ? usage.max
        : contextWindow && contextWindow > 0
          ? contextWindow
          : 100_000;
    const promptTokens =
      usage &&
      typeof usage.promptTokens === "number" &&
      Number.isFinite(usage.promptTokens) &&
      usage.promptTokens >= 0
        ? usage.promptTokens
        : null;

    return {
      max,
      promptTokens,
    };
  }, [usage, contextWindow]);

  const ratio =
    model.promptTokens === null
      ? 0
      : Math.min(1, model.promptTokens / model.max);
  const tone =
    ratio >= 0.9 ? "danger" : ratio >= 0.7 ? "warn" : "normal";
  const stroke =
    tone === "danger"
      ? "#ff3b30"
      : tone === "warn"
        ? "#ff9f0a"
        : "#34c759";
  const dash = CIRC * ratio;
  const pct = Math.round(ratio * 100);
  const hasMeasuredUsage = model.promptTokens !== null;
  const usedLabel = hasMeasuredUsage
    ? formatTokens(model.promptTokens ?? 0)
    : "—";

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className={cn(
            "flex size-8 items-center justify-center rounded-full hover:bg-black/[0.04]",
            className
          )}
          title={
            hasMeasuredUsage
              ? `上下文 ${usedLabel} / ${formatTokens(model.max)}`
              : "上下文等待真实用量"
          }
        >
          <svg width="22" height="22" viewBox="0 0 22 22" aria-hidden>
            <circle
              cx="11"
              cy="11"
              r={R}
              fill="none"
              stroke="#e5e5ea"
              strokeWidth={STROKE}
            />
            <circle
              cx="11"
              cy="11"
              r={R}
              fill="none"
              stroke={stroke}
              strokeWidth={STROKE}
              strokeLinecap="round"
              strokeDasharray={`${dash} ${CIRC - dash}`}
              transform="rotate(-90 11 11)"
            />
          </svg>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        side="top"
        sideOffset={10}
        collisionPadding={12}
        className="max-h-none w-[280px] overflow-visible rounded-xl border border-black/[0.06] bg-white p-0 text-[12.5px] text-[#1d1d1f] shadow-[0_12px_40px_rgba(0,0,0,0.12)]"
      >
        <div className="flex items-center justify-between px-3.5 pb-1 pt-3">
          <div className="text-[13px] font-semibold tracking-[-0.01em]">
            Context
          </div>
          <button
            type="button"
            className="flex size-6 items-center justify-center rounded-md text-[#aeaeb2] transition-colors hover:bg-black/[0.04] hover:text-[#1d1d1f]"
            aria-label="关闭"
            onClick={() => setOpen(false)}
          >
            <CloseIcon />
          </button>
        </div>

        <div className="px-3.5 pb-3 pt-1">
          <div className="mb-1.5 flex items-baseline justify-between tabular-nums">
            <span className="text-[13px] font-medium text-[#1d1d1f]">
              {hasMeasuredUsage ? `${pct}%` : "—"}
            </span>
            <span className="text-[11.5px] text-[#86868b]">
              {usedLabel} / {formatTokens(model.max)}
            </span>
          </div>
          <div className="h-[6px] overflow-hidden rounded-full bg-[#f2f2f7]">
            <div
              className="h-full rounded-full"
              style={{
                width: `${ratio * 100}%`,
                background: stroke,
              }}
            />
          </div>
        </div>

        <div className="border-t border-black/[0.05] px-3.5 py-2 text-[10.5px] text-[#aeaeb2]">
          {hasMeasuredUsage
            ? "真实 prompt tokens"
            : "等待模型返回真实 prompt tokens"}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

function CloseIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden>
      <path
        d="M2.5 2.5l7 7M9.5 2.5l-7 7"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  );
}

export default ContextRing;
