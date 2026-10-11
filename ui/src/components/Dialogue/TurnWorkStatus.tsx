import { useEffect, useMemo, useState, type FC } from "react";
import { formatWorkDuration, parseTimestampMs } from "@/lib/workDuration";

type TurnWorkStatusProps = {
  /** 本轮开始时间戳（毫秒字符串或 ISO 串），来自 `chat.startedAt`。 */
  startedAt?: string;
  className?: string;
};

const TICK_MS = 1000;

/**
 * 运行中的轮次状态条：「工作中 4 分 24 秒」，每秒更新。
 *
 * 对齐 ZCode `AssistantHistoryStatus` 的 running 分支：
 * - 文案用 `chat.history.workingFor`（`工作中 {duration}`），完成态才是「已工作」；
 * - **没有状态图标、也没有 spinner**，活跃感来自每秒跳动的秒数；
 * - running 时该轮的过程区默认展开，所以 ZCode 此时**不渲染右侧箭头**，
 *   这里同样不渲染（箭头只在「已完成且可折叠」时出现，见 `ReplayDisclosure`）。
 *
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
    // 分隔线与内边距都在外层容器上，与 `ReplayDisclosure` 保持同一套骨架
    // （ZCode `AssistantHistoryStatus` 同款），两种状态切换时不会跳版。
    <div
      className={[
        "flex w-full border-b border-border/50 pb-2",
        className || "",
      ]
        .filter(Boolean)
        .join(" ")}
      role="status"
      aria-live="polite"
      data-testid="turn-work-status"
    >
      <span className="inline-flex max-w-full items-center text-ui-lg text-foreground-subtle">
        <span className="truncate">工作中 {duration}</span>
      </span>
    </div>
  );
};

export default TurnWorkStatus;
