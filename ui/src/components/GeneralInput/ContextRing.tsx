import { useMemo, useState } from "react";

import { cn } from "@/lib/utils";

export type ContextUsageView = {
  sys: number;
  tools: number;
  history: number;
  files: number;
  max: number;
  used: number;
  promptTokens?: number;
  completionTokens?: number;
  source?: string;
};

type Props = {
  /** SSE 推送的分段用量；优先于粗估 */
  usage?: ContextUsageView | null;
  /** 输入区当前字符数（fallback 粗估） */
  inputChars: number;
  /** 上下文窗口上限 fallback */
  contextWindow?: number | null;
  className?: string;
};

const R = 9;
const STROKE = 3;
const CIRC = 2 * Math.PI * R;

function estimateTokens(chars: number) {
  return Math.max(0, Math.ceil(chars / 2));
}

function formatTokens(n: number) {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}

const SEG_COLORS = {
  sys: "#5ac8fa",
  tools: "#af52de",
  history: "#34c759",
  files: "#ff9f0a",
};

/**
 * 上下文占用环：优先用后端 TokenCounter 分段（SSE context_usage），否则输入粗估。
 */
const ContextRing: ReactorType.FC<Props> = ({
  usage,
  inputChars,
  contextWindow,
  className,
}) => {
  const [open, setOpen] = useState(false);

  const model = useMemo(() => {
    if (usage && usage.max > 0) {
      return {
        sys: usage.sys,
        tools: usage.tools,
        history: usage.history,
        files: usage.files,
        max: usage.max,
        used: usage.used || usage.sys + usage.tools + usage.history + usage.files,
        source: usage.source || "estimate",
        measured: usage.source === "measured",
      };
    }
    const max = contextWindow && contextWindow > 0 ? contextWindow : 128_000;
    const used = estimateTokens(inputChars);
    return {
      sys: 0,
      tools: 0,
      history: used,
      files: 0,
      max,
      used,
      source: "input-estimate",
      measured: false,
    };
  }, [usage, inputChars, contextWindow]);

  const ratio = Math.min(1, model.used / model.max);
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

  return (
    <div className={cn("relative flex-none", className)}>
      <button
        type="button"
        className="flex size-8 items-center justify-center rounded-full hover:bg-black/[0.04]"
        title={`上下文 ${formatTokens(model.used)} / ${formatTokens(model.max)}`}
        onClick={() => setOpen((v) => !v)}
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
      {open ? (
        <div className="absolute bottom-full right-0 z-20 mb-1.5 w-[220px] rounded-xl border border-black/[0.06] bg-white p-3 text-[12px] shadow-lg">
          <div className="mb-1 font-medium text-[#1d1d1f]">上下文占用</div>
          <div className="mb-2 tabular-nums text-[#6b6b70]">
            {formatTokens(model.used)} / {formatTokens(model.max)} · {pct}%
            <span className="ml-1 text-[10.5px] text-[#aeaeb2]">
              {model.measured ? "实测" : model.source === "estimate" ? "预估" : "输入估"}
            </span>
          </div>
          {(model.sys > 0 || model.tools > 0 || model.history > 0) &&
          model.source !== "input-estimate" ? (
            <div className="space-y-1 border-t border-slate-100 pt-2">
              <Seg label="System" value={model.sys} color={SEG_COLORS.sys} max={model.max} />
              <Seg label="Tools" value={model.tools} color={SEG_COLORS.tools} max={model.max} />
              <Seg label="History" value={model.history} color={SEG_COLORS.history} max={model.max} />
              {model.files > 0 ? (
                <Seg label="Files" value={model.files} color={SEG_COLORS.files} max={model.max} />
              ) : null}
            </div>
          ) : (
            <div className="text-[11px] leading-relaxed text-[#aeaeb2]">
              发送后将显示 system / tools / history 分段预估。
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
};

function Seg({
  label,
  value,
  color,
  max,
}: {
  label: string;
  value: number;
  color: string;
  max: number;
}) {
  const w = Math.min(100, Math.round((value / max) * 100));
  return (
    <div className="flex items-center gap-2 text-[11px]">
      <span className="w-12 shrink-0 text-[#86868b]">{label}</span>
      <div className="h-1.5 min-w-0 flex-1 overflow-hidden rounded-full bg-slate-100">
        <div
          className="h-full rounded-full"
          style={{ width: `${w}%`, background: color }}
        />
      </div>
      <span className="w-10 shrink-0 text-right tabular-nums text-[#6b6b70]">
        {formatTokens(value)}
      </span>
    </div>
  );
}

export default ContextRing;
