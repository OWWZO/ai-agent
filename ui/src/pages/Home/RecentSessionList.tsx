import { memo } from "react";
import classNames from "classnames";

import type { ConversationSessionItem } from "@/services/agentConversation";
import { formatTimestamp } from "@/utils";

type RecentSessionListProps = {
  sessions: ConversationSessionItem[];
  loading?: boolean;
  selectedSessionId?: string;
  onSelect: (session: ConversationSessionItem) => void;
};

const STATUS_LABEL_MAP: Record<string, string> = {
  RUNNING: "进行中",
  SUCCESS: "已完成",
  FAILED: "失败",
  TIMEOUT: "超时",
  STOPPED: "已停止",
};

const RecentSessionList = memo((props: RecentSessionListProps) => {
  const { sessions, loading = false, selectedSessionId, onSelect } = props;

  if (!loading && sessions.length === 0) {
    return null;
  }

  return (
    <div className="mx-auto mt-6 w-full max-w-[920px] rounded-[28px] border border-[var(--chat-border)] bg-[var(--chat-surface)]/88 p-4 shadow-[var(--shadow-sm)]">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <div className="text-[14px] font-medium text-[var(--chat-text)]">
            近期会话
          </div>
          <div className="mt-1 text-[12px] text-[var(--chat-text-soft)]">
            当前会话没有历史时，可手动切换到最近活跃的会话
          </div>
        </div>
        {loading ? (
          <div className="text-[12px] text-[var(--chat-text-muted)]">加载中...</div>
        ) : null}
      </div>

      <div className="flex flex-col gap-2">
        {sessions.map((session) => {
          const isActive = session.sessionId === selectedSessionId;
          return (
            <button
              key={session.sessionId}
              type="button"
              data-session-id={session.sessionId}
              onClick={() => onSelect(session)}
              className={classNames(
                "flex w-full items-start justify-between gap-4 rounded-[20px] border px-4 py-3 text-left transition-all duration-200",
                isActive
                  ? "border-[var(--chat-border-strong)] bg-[var(--chat-surface-soft)]"
                  : "border-[var(--chat-border)] hover:border-[var(--chat-border-strong)] hover:bg-[var(--chat-surface-soft)]/70"
              )}
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate text-[14px] font-medium text-[var(--chat-text)]">
                    {session.title || "未命名会话"}
                  </span>
                  {isActive ? (
                    <span className="rounded-full bg-[var(--primary)]/10 px-2 py-0.5 text-[11px] text-[var(--primary)]">
                      当前会话
                    </span>
                  ) : null}
                </div>
                <div className="mt-1 line-clamp-1 text-[12px] text-[var(--chat-text-soft)]">
                  {session.latestQueryText || "暂无问题预览"}
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-3 text-[11px] text-[var(--chat-text-muted)]">
                  <span>{STATUS_LABEL_MAP[session.status] || "未知状态"}</span>
                  <span>{session.finishedRunCount || 0} 成功</span>
                  <span>{session.failedRunCount || 0} 失败</span>
                  <span>{session.runCount || 0} 轮</span>
                </div>
              </div>

              <div className="shrink-0 text-right text-[11px] text-[var(--chat-text-muted)]">
                <div>最近活动</div>
                <div className="mt-1 whitespace-nowrap">
                  {formatTimestamp(Date.parse(session.lastActiveAt || "") || Date.now())}
                </div>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
});

RecentSessionList.displayName = "RecentSessionList";

export default RecentSessionList;
