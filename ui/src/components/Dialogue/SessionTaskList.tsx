import { FC, memo, useMemo } from "react";
import { CheckCircle2Icon, CircleDotIcon, CircleIcon, ListTodoIcon } from "lucide-react";

type SessionTask = {
  id: string;
  subject?: string;
  description?: string;
  status?: string;
  activeForm?: string;
  blockedBy?: string[];
};

type SessionTaskListProps = {
  tool: CHAT.Task;
};

function asTasks(raw: unknown): SessionTask[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      id: String(item.id || ""),
      subject: item.subject ? String(item.subject) : undefined,
      description: item.description ? String(item.description) : undefined,
      status: item.status ? String(item.status) : "pending",
      activeForm: item.activeForm ? String(item.activeForm) : undefined,
      blockedBy: Array.isArray(item.blockedBy)
        ? item.blockedBy.map((id) => String(id))
        : undefined,
    }))
    .filter((item) => item.id || item.subject);
}

function StatusIcon({ status }: { status?: string }) {
  if (status === "completed") {
    return <CheckCircle2Icon className="size-3.5 shrink-0 text-emerald-500" />;
  }
  if (status === "in_progress") {
    return <CircleDotIcon className="size-3.5 shrink-0 text-[var(--chat-accent)]" />;
  }
  return <CircleIcon className="size-3.5 shrink-0 text-[var(--chat-text-soft)]" />;
}

/**
 * 会话 Todo 列表（对标 cchaha TaskListV2）。
 * 由 SSE session_tasks 驱动，TaskCreate/Update/TodoWrite 后刷新。
 */
const SessionTaskList: FC<SessionTaskListProps> = memo(({ tool }) => {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  const tasks = useMemo(
    () => asTasks(nested.tasks || resultMap.tasks || toolAny.tasks),
    [nested.tasks, resultMap.tasks, toolAny.tasks]
  );
  const total = Number(nested.total ?? resultMap.total ?? toolAny.total ?? tasks.length);
  const completed = Number(
    nested.completed ?? resultMap.completed ?? toolAny.completed ?? tasks.filter((t) => t.status === "completed").length
  );
  const inProgress = Number(
    nested.inProgress ?? resultMap.inProgress ?? toolAny.inProgress ?? tasks.filter((t) => t.status === "in_progress").length
  );

  if (!tasks.length) {
    return (
      <div className="mt-2 rounded-2xl border border-[var(--chat-border)]/30 bg-[var(--chat-surface-soft)]/40 px-3 py-2 text-[12px] text-[var(--chat-text-soft)]">
        任务列表为空
      </div>
    );
  }

  return (
    <div className="mt-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/35 bg-[var(--chat-surface-soft)]/55 px-3 py-2.5">
      <div className="mb-2 flex items-center gap-2">
        <div className="flex size-7 items-center justify-center rounded-lg bg-[var(--chat-accent)]/10 text-[var(--chat-accent)]">
          <ListTodoIcon className="size-3.5" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-medium text-[var(--chat-text)]">实现任务</div>
          <div className="text-[11px] text-[var(--chat-text-soft)]">
            {completed}/{total} 完成
            {inProgress > 0 ? ` · ${inProgress} 进行中` : ""}
          </div>
        </div>
      </div>
      <ul className="flex flex-col gap-1.5">
        {tasks.map((task) => (
          <li
            key={task.id}
            className="flex items-start gap-2 rounded-xl border border-[var(--chat-border)]/25 bg-[var(--chat-surface)]/60 px-2.5 py-1.5"
          >
            <StatusIcon status={task.status} />
            <div className="min-w-0 flex-1">
              <div
                className={[
                  "text-[12px] font-medium leading-5",
                  task.status === "completed"
                    ? "text-[var(--chat-text-soft)] line-through"
                    : "text-[var(--chat-text)]",
                ].join(" ")}
              >
                <span className="mr-1 text-[11px] text-[var(--chat-text-soft)]">#{task.id}</span>
                {task.subject || task.description || "未命名"}
              </div>
              {task.status === "in_progress" && task.activeForm ? (
                <div className="text-[11px] text-[var(--chat-accent)]">{task.activeForm}</div>
              ) : null}
              {task.description && task.subject && task.description !== task.subject ? (
                <div className="mt-0.5 line-clamp-2 text-[11px] text-[var(--chat-text-soft)]">
                  {task.description}
                </div>
              ) : null}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
});

SessionTaskList.displayName = "SessionTaskList";

export default SessionTaskList;
