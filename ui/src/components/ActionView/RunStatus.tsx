import { AlertCircle, Ban, CheckCircle2, Clock3 } from "lucide-react";

type RunStatusProps = {
  status?: string | null;
  errorMsg?: string;
  finishedAt?: string;
  className?: string;
};

const STATUS_META: Record<
  string,
  {
    label: string;
    description: string;
    icon: typeof CheckCircle2;
    toneClass: string;
  }
> = {
  SUCCESS: {
    label: "已完成",
    description: "当前轮次已正常结束。",
    icon: CheckCircle2,
    toneClass:
      "border-emerald-200 bg-emerald-50/80 text-emerald-700",
  },
  FAILED: {
    label: "执行失败",
    description: "本轮未能完成。",
    icon: AlertCircle,
    toneClass:
      "border-rose-200 bg-rose-50/85 text-rose-700",
  },
  STOPPED: {
    label: "已停止",
    description: "本轮已停止。",
    icon: Ban,
    toneClass:
      "border-[var(--chat-border)] bg-[var(--chat-surface-soft)] text-[var(--chat-text-soft)]",
  },
  TIMEOUT: {
    label: "已超时",
    description: "本轮执行超时。",
    icon: Clock3,
    toneClass:
      "border-[var(--chat-border)] bg-[var(--chat-surface-soft)] text-[var(--chat-text-soft)]",
  },
};

const normalizeStatus = (status?: string | null) => {
  return String(status || "").trim().toUpperCase();
};

const RunStatus = (props: RunStatusProps) => {
  const { status, errorMsg, finishedAt, className } = props;
  const normalizedStatus = normalizeStatus(status);
  const meta = STATUS_META[normalizedStatus];

  if (!meta || normalizedStatus === "SUCCESS") {
    return null;
  }

  const Icon = meta.icon;

  return (
    <div
      className={[
        "rounded-2xl border px-4 py-3 shadow-[var(--shadow-xs)]",
        meta.toneClass,
        className || "",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white/70">
          <Icon className="h-4.5 w-4.5" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="text-[14px] font-semibold">{meta.label}</span>
            {finishedAt ? (
              <span className="text-[11px] opacity-80">
                结束于 {finishedAt}
              </span>
            ) : null}
          </div>
          <p className="mt-1 text-[12px] leading-relaxed opacity-90">
            {errorMsg || meta.description}
          </p>
        </div>
      </div>
    </div>
  );
};

export default RunStatus;
