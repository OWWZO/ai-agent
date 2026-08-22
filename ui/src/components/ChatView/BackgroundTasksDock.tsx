import { memo, useMemo, useState } from "react";
import {
  BotIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  LoaderCircleIcon,
} from "lucide-react";
import {
  projectDockTasks,
  projectAgentMemberByToolCallId,
} from "@/utils/chat/agentRuntimeProjector";
import {
  isAgentDispatchTask,
  resolveSubAgentDisplay,
} from "@/utils/chat/subagent";
import { resolveTaskToolCallId } from "@/utils/chat/toolCalls";
import type { DockTaskItem } from "@/types/agentRuntime";
import { StatusDot } from "@/components/Dialogue/tools/StatusDot";
import { cn } from "@/lib/utils";

type BackgroundTasksDockProps = {
  chat?: CHAT.ChatItem;
  onOpenAgent?: (task: CHAT.Task, chat: CHAT.ChatItem) => void;
};

export function findAgentTaskByToolCallId(
  chat: CHAT.ChatItem,
  toolCallId: string
): CHAT.Task | null {
  if (!toolCallId) return null;
  const scan = (tasks: CHAT.Task[] | undefined): CHAT.Task | null => {
    for (const task of tasks || []) {
      if (isAgentDispatchTask(task)) {
        const id =
          resolveTaskToolCallId(task) || task.messageId || task.id || "";
        if (id === toolCallId) return task;
      }
      const nested = scan(task.children);
      if (nested) return nested;
    }
    return null;
  };

  for (const group of chat.tasks || []) {
    for (const container of group || []) {
      const hit = scan((container as CHAT.Task).children || [container as CHAT.Task]);
      if (hit) return hit;
    }
  }
  for (const group of chat.multiAgent?.tasks || []) {
    const hit = scan(group as CHAT.Task[]);
    if (hit) return hit;
  }
  return null;
}

export function findLatestRunningAgentTask(
  chat?: CHAT.ChatItem
): CHAT.Task | null {
  if (!chat) return null;
  let latest: CHAT.Task | null = null;
  const visit = (task?: CHAT.Task) => {
    if (!task) return;
    if (
      isAgentDispatchTask(task) &&
      resolveSubAgentDisplay(task).status === "running"
    ) {
      latest = task;
    }
    for (const child of task.children || []) {
      visit(child);
    }
  };
  for (const group of chat.tasks || []) {
    for (const container of group || []) {
      visit(container as CHAT.Task);
    }
  }
  if (latest) return latest;
  for (const group of chat.multiAgent?.tasks || []) {
    for (const task of group || []) {
      visit(task as CHAT.Task);
    }
  }
  return latest;
}

function TaskRow({
  task,
  onOpen,
}: {
  task: DockTaskItem;
  onOpen: (id: string) => void;
}) {
  return (
    <button
      type="button"
      className={cn(
        "kimi-dock-card__row",
        task.state === "done" && "is-done",
        task.state === "fail" && "is-fail"
      )}
      onClick={() => onOpen(task.id)}
    >
      {task.state === "run" ? (
        <StatusDot status="running" />
      ) : task.state === "fail" ? (
        <StatusDot status="error" />
      ) : (
        <StatusDot status="done" />
      )}
      <BotIcon className="size-3.5 shrink-0 text-[var(--color-text-muted)]" />
      <span
        className={cn(
          "min-w-0 flex-1 truncate text-[12px] font-medium",
          task.state === "done"
            ? "text-[var(--color-text-faint)] line-through"
            : "text-[var(--color-text)]"
        )}
      >
        {task.name}
      </span>
      <span className="shrink-0 rounded-full bg-[var(--color-surface-sunken)] px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-[var(--color-text-faint)]">
        {task.kind}
      </span>
      {task.timing ? (
        <span className="shrink-0 text-[11px] text-[var(--color-text-faint)]">
          {task.timing}
        </span>
      ) : null}
      <ChevronRightIcon className="size-3.5 shrink-0 text-[var(--color-text-faint)]" />
    </button>
  );
}

/**
 * 底部 Dock：仅展示 run_in_background 子 Agent（前台 Agent 留在时间线）。
 * 点击行 → 打开右侧 AgentDetailPanel。
 */
const BackgroundTasksDock = memo(function BackgroundTasksDock({
  chat,
  onOpenAgent,
}: BackgroundTasksDockProps) {
  const [open, setOpen] = useState(true);
  const tasks = useMemo(
    () => (chat ? projectDockTasks(chat) : []),
    [chat]
  );

  if (!chat || tasks.length === 0) {
    return null;
  }

  const running = tasks.filter((t) => t.state === "run").length;

  const handleOpen = (id: string) => {
    if (!onOpenAgent) return;
    const tool = findAgentTaskByToolCallId(chat, id);
    if (tool) {
      onOpenAgent(tool, chat);
      return;
    }
    // 兜底：用投影成员确认存在性
    if (projectAgentMemberByToolCallId(chat, id)) {
      // 无 task 实体时无法开面板
      return;
    }
  };

  return (
    <div className="kimi-dock-card">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="kimi-dock-card__head"
      >
        <div className="flex size-8 shrink-0 items-center justify-center rounded-[var(--kimi-radius-sm)] bg-[var(--color-surface-sunken)] text-[var(--color-text-muted)]">
          {running > 0 ? (
            <LoaderCircleIcon className="size-4 animate-spin text-[var(--color-accent)]" />
          ) : (
            <BotIcon className="size-4" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate text-[13px] font-semibold text-[var(--color-text)]">
              后台任务
            </span>
            <span className="shrink-0 rounded-full bg-[var(--color-surface-sunken)] px-2 py-0.5 text-[11px] font-medium text-[var(--color-text-faint)]">
              {tasks.length}
              {running > 0 ? ` · ${running} 运行中` : ""}
            </span>
          </div>
          <div className="mt-0.5 line-clamp-1 text-[11px] text-[var(--color-text-faint)]">
            {running > 0
              ? tasks.find((t) => t.state === "run")?.name || "子智能体运行中"
              : "后台子智能体"}
          </div>
        </div>
        <ChevronDownIcon
          className={cn(
            "size-4 shrink-0 text-[var(--color-text-faint)] transition-transform duration-150",
            open ? "rotate-180" : ""
          )}
        />
      </button>
      {open ? (
        <div className="kimi-dock-card__body">
          {tasks.map((task) => (
            <TaskRow key={task.id} task={task} onOpen={handleOpen} />
          ))}
        </div>
      ) : null}
    </div>
  );
});

export default BackgroundTasksDock;
