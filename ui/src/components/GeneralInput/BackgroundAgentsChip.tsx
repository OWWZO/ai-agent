import { BotIcon } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * 输入框内的后台子智能体徽标（移植 ZCode `ConversationBackgroundWorkTrigger` 的智能体分支）。
 * 与右上角「智能体」面板同源：点击一律把面板切成展开态。
 */
type Props = {
  /** 展示数量：有运行中取运行数，否则取总数 */
  count: number;
  runningCount?: number;
  disabled?: boolean;
  onOpen?: () => void;
};

export default function BackgroundAgentsChip({
  count,
  runningCount = 0,
  disabled,
  onOpen,
}: Props) {
  if (!onOpen || count <= 0) {
    return null;
  }
  const title =
    runningCount > 0
      ? `${runningCount} 个后台子智能体正在运行，点击查看`
      : "查看后台子智能体";

  return (
    <button
      type="button"
      disabled={disabled}
      className={cn(
        "inline-flex h-8 min-w-0 items-center gap-1 rounded-md px-2 text-ui-base font-medium text-foreground-subtle transition-colors hover:bg-hover hover:text-foreground",
        disabled && "cursor-not-allowed opacity-45 hover:bg-transparent hover:text-foreground-subtle"
      )}
      title={title}
      aria-label={title}
      data-testid="background-agents-composer-chip"
      onClick={onOpen}
    >
      <BotIcon className="size-3.5 shrink-0 opacity-80" />
      <span className="tabular-nums">{count}</span>
    </button>
  );
}
