import { FC, memo, useMemo, useState } from "react";
import {
  CheckCircle2Icon,
  ChevronDownIcon,
  CircleDotIcon,
  CircleIcon,
  ListTodoIcon,
} from "lucide-react";
import {
  extractSessionTaskSnapshot,
  findLatestSessionTasks,
  type SessionTask,
} from "@/components/Dialogue/sessionTaskModel";

type SessionTaskComposerBarProps = {
  chat?: CHAT.ChatItem;
  taskList?: CHAT.Task[];
};

function StatusIcon({ status }: { status?: string }) {
  if (status === "completed") {
    return <CheckCircle2Icon className="size-3.5 shrink-0 text-[var(--chat-text-muted)]" />;
  }
  if (status === "in_progress") {
    return <CircleDotIcon className="size-3.5 shrink-0 text-[var(--chat-accent)]" />;
  }
  return <CircleIcon className="size-3.5 shrink-0 text-[var(--chat-text-soft)]" />;
}

const SessionTaskComposerBar: FC<SessionTaskComposerBarProps> = memo(
  ({ chat, taskList }) => {
    const tool = useMemo(
      () => findLatestSessionTasks(chat, taskList),
      [chat, taskList]
    );
    const snapshot = useMemo(() => extractSessionTaskSnapshot(tool), [tool]);
    const [open, setOpen] = useState(false);

    if (!snapshot?.tasks.length) {
      return null;
    }

    const { tasks, total, completed, inProgress } = snapshot;
    const summary = `${completed}/${total} 完成${
      inProgress > 0 ? ` · ${inProgress} 进行中` : ""
    }`;

    return (
      <div className="mb-2 overflow-hidden rounded-2xl border border-[var(--chat-border)]/45 bg-[var(--chat-surface)]/95 shadow-[var(--shadow-sm)]">
        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          className="flex w-full items-center gap-2.5 px-3 py-2.5 text-left transition-colors hover:bg-[var(--chat-surface-soft)]/60"
        >
          <div className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-[var(--chat-surface-muted)] text-[var(--chat-text-muted)]">
            <ListTodoIcon className="size-4" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="truncate text-[13px] font-semibold text-[var(--chat-text)]">
                实现任务
              </span>
              <span className="shrink-0 rounded-full bg-[var(--chat-surface-muted)] px-2 py-0.5 text-[11px] font-medium text-[var(--chat-text-soft)]">
                {summary}
              </span>
            </div>
            {inProgress > 0 ? (
              <div className="mt-0.5 line-clamp-1 text-[11px] text-[var(--chat-text-soft)]">
                {tasks.find((task) => task.status === "in_progress")?.activeForm ||
                  tasks.find((task) => task.status === "in_progress")?.subject ||
                  "任务进行中"}
              </div>
            ) : (
              <div className="mt-0.5 line-clamp-1 text-[11px] text-[var(--chat-text-soft)]">
                {completed >= total && total > 0 ? "全部完成" : "待执行"}
              </div>
            )}
          </div>
          <ChevronDownIcon
            className={[
              "size-4 shrink-0 text-[var(--chat-text-soft)] transition-transform duration-150",
              open ? "rotate-180" : "",
            ].join(" ")}
          />
        </button>

        {open ? (
          <ul className="flex flex-col gap-1 border-t border-[var(--chat-border)]/30 px-3 py-2.5">
            {tasks.map((task: SessionTask) => (
              <li
                key={task.id}
                className="flex items-start gap-2 rounded-xl bg-[var(--chat-surface-soft)]/50 px-2.5 py-1.5"
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
                    {task.subject || task.description || "未命名"}
                  </div>
                  {task.status === "in_progress" && task.activeForm ? (
                    <div className="text-[11px] text-[var(--chat-text-muted)]">
                      {task.activeForm}
                    </div>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    );
  }
);

SessionTaskComposerBar.displayName = "SessionTaskComposerBar";

export default SessionTaskComposerBar;
