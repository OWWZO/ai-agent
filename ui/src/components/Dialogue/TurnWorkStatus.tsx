import { useEffect, useMemo, useState, type FC } from "react";
import { LoaderCircle } from "lucide-react";
import { formatWorkDuration, parseTimestampMs } from "@/lib/workDuration";

type TurnWorkStatusProps = {
  /** 本轮开始时间戳（毫秒字符串或 ISO 串），来自 `chat.startedAt`。 */
  startedAt?: string;
  className?: string;
};

const TICK_MS = 1000;

/**
 * 运行中的轮次状态条：「已工作 4 分 24 秒」，每秒更新。
 *
 * 对齐 ZCode `AssistantHistoryStatus` 的 running 分支（图1 的实时增长观感）。
 * 结束后同一位置交给 `ReplayDisclosure`（已完成态），两者互斥、共用同一套时长写法
 * （`lib/workDuration.ts`），所以「运行中」与「已完成」的数字口径一致。
 */
const TurnWorkStatus: FC<TurnWorkStatusProps> = ({ startedAt, className }) => {
  const startMs = useMemo(() => parseTimestampMs(startedAt), [startedAt]);
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    if (startMs === undefined) {
      return;
    }
    // 立刻对齐一次，避免首帧用的是挂载时刻的旧值。
    setNowMs(Date.now());
    const timer = window.setInterval(() => setNowMs(Date.now()), TICK_MS);
    return () => window.clearInterval(timer);
  }, [startMs]);

  if (startMs === undefined) {
    return null;
  }

  const duration = formatWorkDuration(Math.max(0, nowMs - startMs));

  return (
    <div
      className={[
        "flex w-full items-center gap-2 border-b border-[var(--chat-border)]/50 pb-2 text-ui-base text-[var(--chat-text-soft)]",
        className || "",
      ]
        .filter(Boolean)
        .join(" ")}
      role="status"
      aria-live="polite"
      data-testid="turn-work-status"
    >
      <LoaderCircle
        className="h-3.5 w-3.5 shrink-0 animate-spin text-[var(--chat-text-muted)]"
        aria-hidden="true"
      />
      <span className="shrink-0">已工作</span>
      <span className="shrink-0 tabular-nums">{duration}</span>
    </div>
  );
};

export default TurnWorkStatus;
