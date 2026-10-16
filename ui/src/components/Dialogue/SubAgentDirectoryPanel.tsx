import { memo, useCallback, useMemo } from "react";
import { CheckCircle2, CircleAlert, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  resolveRosterAgentTool,
  selectEndedSubAgents,
  selectRunningSubAgents,
  type SubAgentRosterItem,
} from "@/utils/chat/subAgentRoster";
import {
  formatSubAgentRelativeTime,
  resolveSubAgentStateLabel,
  useNowTicker,
} from "@/components/ChatView/subAgentsModel";

/**
 * 右侧工作区「子智能体目录」tab（移植 ZCode `SubagentDirectorySidePane`）。
 *
 * 两段式：**正在运行** + **已结束**，行上给标题 / 状态 / 摘要 / 相对时间，
 * 点一行打开该子智能体的详情 tab（与文件预览同一套 tab 挂载机制）。
 *
 * 数据源与右上角「智能体」面板同源（`collectSubAgentRoster`），
 * 面板是「现在谁在跑」的快捷入口，目录是「这轮一共派了谁」的完整账本。
 */

type Props = {
  items: SubAgentRosterItem[];
  onOpen?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

/** 摘要行文案：优先终态摘要，其次回传正文首行（ZCode 用 output 头部） */
function resolveRowSummary(item: SubAgentRosterItem): string {
  const summary = (item.summary || "").trim();
  if (summary) {
    return summary;
  }
  const firstLine = (item.output?.[0] || "").trim();
  if (!firstLine || firstLine.length > 160) {
    return "";
  }
  return firstLine;
}

function DirectoryRow({
  item,
  now,
  onOpen,
}: {
  item: SubAgentRosterItem;
  now: number;
  onOpen?: (item: SubAgentRosterItem) => void;
}) {
  const running = item.state === "run";
  const failed = item.state === "fail";
  const timestamp = running ? item.startedAtMs : item.endedAtMs ?? item.startedAtMs;
  const relative = formatSubAgentRelativeTime(timestamp, now);
  const summary = resolveRowSummary(item);

  return (
    <button
      type="button"
      className="flex w-full items-start gap-2.5 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-hover"
      onClick={() => onOpen?.(item)}
      data-testid="sub-agent-directory-row"
    >
      {running ? (
        <Loader2 className="mt-0.5 size-4 shrink-0 animate-spin text-foreground-subtle" />
      ) : failed ? (
        <CircleAlert className="mt-0.5 size-4 shrink-0 text-foreground-subtle" />
      ) : (
        <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-foreground-subtle" />
      )}
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="flex min-w-0 items-baseline gap-2">
          <span
            className={cn(
              "truncate text-ui-base font-medium",
              running ? "text-foreground" : "text-foreground-subtle"
            )}
          >
            {item.name}
          </span>
          <span className="shrink-0 text-ui-sm text-foreground-subtlest">
            {resolveSubAgentStateLabel(item.state)}
          </span>
        </span>
        {summary ? (
          <span className="mt-0.5 truncate text-ui-sm text-foreground-subtle">
            {summary}
          </span>
        ) : null}
      </span>
      {relative ? (
        <span className="mt-0.5 shrink-0 text-ui-sm text-foreground-subtlest">
          {relative}
        </span>
      ) : null}
    </button>
  );
}

export const SubAgentDirectoryPanel = memo(function SubAgentDirectoryPanel({
  items,
  onOpen,
}: Props) {
  const runningItems = useMemo(() => selectRunningSubAgents(items), [items]);
  const endedItems = useMemo(() => selectEndedSubAgents(items), [items]);
  const now = useNowTicker(runningItems.length > 0);

  const handleOpen = useCallback(
    (item: SubAgentRosterItem) => {
      onOpen?.(resolveRosterAgentTool(item), item.hostChat);
    },
    [onOpen]
  );

  const hasAny = runningItems.length > 0 || endedItems.length > 0;

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden">
      <div className="flex h-10 shrink-0 items-center px-3 text-ui-base font-medium text-foreground">
        子智能体目录
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-1.5 pb-3">
        {!hasAny ? (
          <div className="kimi-detail-panel-empty">暂无子智能体</div>
        ) : null}

        {runningItems.length > 0 ? (
          <section className="pb-1">
            <div className="px-3 py-1.5 text-ui-sm text-foreground-subtlest">
              正在运行 · {runningItems.length}
            </div>
            {runningItems.map((item) => (
              <DirectoryRow
                key={item.id}
                item={item}
                now={now}
                onOpen={handleOpen}
              />
            ))}
          </section>
        ) : null}

        {endedItems.length > 0 ? (
          <section className="pb-1">
            <div className="px-3 py-1.5 text-ui-sm text-foreground-subtlest">
              已结束 · {endedItems.length}
            </div>
            {endedItems.map((item) => (
              <DirectoryRow
                key={item.id}
                item={item}
                now={now}
                onOpen={handleOpen}
              />
            ))}
          </section>
        ) : null}
      </div>
    </div>
  );
});
