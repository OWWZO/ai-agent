import { useMemo, useState, type ReactNode } from "react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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

const SEG = {
  sys: "#0a84ff",
  memory: "#bf5af2",
  messages: "#34c759",
  free: "#e5e5ea",
} as const;

function estimateTokens(chars: number) {
  return Math.max(0, Math.ceil(chars / 2));
}

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
 * 上下文占用环：优先用后端 TokenCounter 分段（SSE context_usage），否则输入粗估。
 * 展开面板走 Portal，避免被输入框 overflow 裁切。
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
      const sys = Math.max(0, usage.sys);
      const memory = Math.max(0, usage.tools + usage.files);
      const messages = Math.max(0, usage.history);
      const used = usage.used || sys + memory + messages;
      return {
        sys,
        memory,
        messages,
        free: Math.max(0, usage.max - used),
        max: usage.max,
        used,
        source: usage.source || "estimate",
        measured: usage.source === "measured",
        fromServer: true,
      };
    }
    const max = contextWindow && contextWindow > 0 ? contextWindow : 128_000;
    const used = estimateTokens(inputChars);
    return {
      sys: 0,
      memory: 0,
      messages: used,
      free: Math.max(0, max - used),
      max,
      used,
      source: "input-estimate",
      measured: false,
      fromServer: false,
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

  const segWidths = useMemo(() => {
    const total = Math.max(1, model.max);
    return {
      sys: (model.sys / total) * 100,
      memory: (model.memory / total) * 100,
      messages: (model.messages / total) * 100,
      free: (model.free / total) * 100,
    };
  }, [model]);

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className={cn(
            "flex size-8 items-center justify-center rounded-full hover:bg-black/[0.04]",
            className
          )}
          title={`上下文 ${formatTokens(model.used)} / ${formatTokens(model.max)}`}
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
              {pct}%
            </span>
            <span className="text-[11.5px] text-[#86868b]">
              {formatTokens(model.used)} / {formatTokens(model.max)}
            </span>
          </div>
          <div className="flex h-[6px] overflow-hidden rounded-full bg-[#f2f2f7]">
            {segWidths.sys > 0 ? (
              <div
                className="h-full"
                style={{
                  width: `${segWidths.sys}%`,
                  background: SEG.sys,
                }}
              />
            ) : null}
            {segWidths.memory > 0 ? (
              <div
                className="h-full"
                style={{
                  width: `${segWidths.memory}%`,
                  background: SEG.memory,
                }}
              />
            ) : null}
            {segWidths.messages > 0 ? (
              <div
                className="h-full"
                style={{
                  width: `${segWidths.messages}%`,
                  background: SEG.messages,
                }}
              />
            ) : null}
            <div
              className="h-full"
              style={{
                width: `${Math.max(0, segWidths.free)}%`,
                background: SEG.free,
              }}
            />
          </div>
        </div>

        <div className="border-t border-black/[0.05] px-1.5 py-1.5">
          {model.fromServer ? (
            <>
              <Row
                icon={<DocIcon />}
                color={SEG.sys}
                label="System prompt"
                value={model.sys}
              />
              <Row
                icon={<BrainIcon />}
                color={SEG.memory}
                label="Tool"
                value={model.memory}
              />
              <Row
                icon={<MessagesIcon />}
                color={SEG.messages}
                label="Messages"
                value={model.messages}
              />
              <Row
                icon={<FreeIcon />}
                color="#aeaeb2"
                label="Free space"
                value={model.free}
                muted
              />
            </>
          ) : (
            <div className="px-2.5 py-2 text-[11.5px] leading-relaxed text-[#aeaeb2]">
              发送后将显示 System / Tool / Messages 分段占用。
            </div>
          )}
        </div>

        <div className="border-t border-black/[0.05] px-3.5 py-2 text-[10.5px] text-[#aeaeb2]">
          {model.measured
            ? "实测 prompt tokens"
            : model.source === "estimate"
              ? "预估占用"
              : "按输入粗估"}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

function Row({
  icon,
  color,
  label,
  value,
  muted,
}: {
  icon: ReactNode;
  color: string;
  label: string;
  value: number;
  muted?: boolean;
}) {
  return (
    <div className="flex items-center gap-2.5 rounded-lg px-2 py-1.5">
      <span
        className="flex size-[18px] shrink-0 items-center justify-center"
        style={{ color }}
      >
        {icon}
      </span>
      <span
        className={cn(
          "min-w-0 flex-1 truncate",
          muted ? "text-[#86868b]" : "text-[#1d1d1f]"
        )}
      >
        {label}
      </span>
      <span
        className={cn(
          "shrink-0 tabular-nums",
          muted ? "text-[#aeaeb2]" : "text-[#6b6b70]"
        )}
      >
        {formatTokens(value)}
      </span>
    </div>
  );
}

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

function DocIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path
        d="M4.5 2.5h5.2L12.5 5.3V13a.5.5 0 0 1-.5.5H4.5A.5.5 0 0 1 4 13V3a.5.5 0 0 1 .5-.5z"
        stroke="currentColor"
        strokeWidth="1.2"
      />
      <path
        d="M9.5 2.5V5h2.8"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function BrainIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path
        d="M6.2 2.8a2.2 2.2 0 0 0-2.1 2.2c0 .3.05.6.15.86A2 2 0 0 0 3 7.7c0 .8.42 1.5 1.05 1.9-.1.28-.15.58-.15.9 0 1.2.9 2.2 2.1 2.3V4.2c0-.5.2-.95.5-1.3A2.2 2.2 0 0 0 6.2 2.8z"
        stroke="currentColor"
        strokeWidth="1.15"
      />
      <path
        d="M9.8 2.8a2.2 2.2 0 0 1 2.1 2.2c0 .3-.05.6-.15.86A2 2 0 0 1 13 7.7c0 .8-.42 1.5-1.05 1.9.1.28.15.58.15.9 0 1.2-.9 2.2-2.1 2.3V4.2c0-.5-.2-.95-.5-1.3.3-.07.6-.1.9-.1z"
        stroke="currentColor"
        strokeWidth="1.15"
      />
      <path
        d="M8 3.2v9.4"
        stroke="currentColor"
        strokeWidth="1.15"
        strokeLinecap="round"
      />
    </svg>
  );
}

function MessagesIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path
        d="M3 4.2A1.2 1.2 0 0 1 4.2 3h6.1A1.2 1.2 0 0 1 11.5 4.2v3.6A1.2 1.2 0 0 1 10.3 9H7.1L4.8 11V9H4.2A1.2 1.2 0 0 1 3 7.8V4.2z"
        stroke="currentColor"
        strokeWidth="1.15"
      />
      <path
        d="M6.2 10.5h.9l1.8 1.6V10.5h2.4A1.2 1.2 0 0 0 12.5 9.3V6.8"
        stroke="currentColor"
        strokeWidth="1.15"
        strokeLinecap="round"
      />
    </svg>
  );
}

function FreeIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden>
      <circle cx="8" cy="8" r="4.6" stroke="currentColor" strokeWidth="1.2" />
    </svg>
  );
}

export default ContextRing;
