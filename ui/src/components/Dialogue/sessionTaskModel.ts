export type SessionTask = {
  id: string;
  subject?: string;
  description?: string;
  status?: string;
  activeForm?: string;
  blockedBy?: string[];
};

export type SessionTaskSnapshot = {
  tasks: SessionTask[];
  total: number;
  completed: number;
  inProgress: number;
};

export type SessionTaskTransition = {
  id: string;
  kind: "completed" | "started";
  label: string;
};

function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null
    ? (value as Record<string, unknown>)
    : {};
}

export function asSessionTasks(raw: unknown): SessionTask[] {
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

export function extractSessionTaskSnapshot(tool?: CHAT.Task | null): SessionTaskSnapshot | null {
  if (!tool || tool.messageType !== "session_tasks") {
    return null;
  }
  const resultMap = asRecord(tool.resultMap);
  const nested = asRecord(resultMap.resultMap);
  const toolAny = tool as unknown as Record<string, unknown>;
  const tasks = asSessionTasks(nested.tasks || resultMap.tasks || toolAny.tasks);
  if (!tasks.length) {
    return null;
  }
  const total = Number(nested.total ?? resultMap.total ?? toolAny.total ?? tasks.length);
  const completed = Number(
    nested.completed ??
      resultMap.completed ??
      toolAny.completed ??
      tasks.filter((task) => task.status === "completed").length
  );
  const inProgress = Number(
    nested.inProgress ??
      resultMap.inProgress ??
      toolAny.inProgress ??
      tasks.filter((task) => task.status === "in_progress").length
  );
  return { tasks, total, completed, inProgress };
}

function flattenTasks(chat?: CHAT.ChatItem, taskList?: CHAT.Task[]): CHAT.Task[] {
  if (taskList?.length) {
    return taskList;
  }
  if (!chat?.tasks?.length) {
    return [];
  }
  const flat: CHAT.Task[] = [];
  for (const group of chat.tasks) {
    if (Array.isArray(group)) {
      flat.push(...group);
    }
  }
  return flat;
}

export function findLatestSessionTasks(
  chat?: CHAT.ChatItem,
  taskList?: CHAT.Task[]
): CHAT.Task | undefined {
  const tasks = flattenTasks(chat, taskList);
  for (let i = tasks.length - 1; i >= 0; i -= 1) {
    if (tasks[i]?.messageType === "session_tasks") {
      return tasks[i];
    }
  }
  return undefined;
}

export function findPreviousSessionTasks(
  chat: CHAT.ChatItem | undefined,
  current: CHAT.Task
): CHAT.Task | undefined {
  const sessionTasks = flattenTasks(chat).filter(
    (task) => task?.messageType === "session_tasks"
  );
  if (sessionTasks.length < 2) {
    return undefined;
  }

  let index = sessionTasks.findIndex((task) => task === current);
  if (index < 0 && current.messageId) {
    index = sessionTasks.findIndex((task) => task.messageId === current.messageId);
  }
  if (index < 0 && current.taskId) {
    index = sessionTasks.findIndex((task) => task.taskId === current.taskId);
  }
  if (index < 0) {
    // Fallback: treat the last snapshot as current when identity is lost after clone.
    const currentSnap = extractSessionTaskSnapshot(current);
    index = sessionTasks.findIndex((task) => {
      const snap = extractSessionTaskSnapshot(task);
      return (
        snap &&
        currentSnap &&
        snap.completed === currentSnap.completed &&
        snap.inProgress === currentSnap.inProgress &&
        snap.total === currentSnap.total &&
        snap.tasks.length === currentSnap.tasks.length
      );
    });
  }
  if (index < 0) {
    index = sessionTasks.length - 1;
  }
  return index > 0 ? sessionTasks[index - 1] : undefined;
}

function taskLabel(task: SessionTask): string {
  return (task.subject || task.description || task.activeForm || "未命名任务").trim();
}

/**
 * Diff consecutive session_tasks snapshots into quiet timeline lines.
 */
export function diffSessionTaskTransitions(
  previous: SessionTask[] | null | undefined,
  current: SessionTask[]
): SessionTaskTransition[] {
  const prevMap = new Map((previous || []).map((task) => [task.id, task]));
  const transitions: SessionTaskTransition[] = [];

  for (const task of current) {
    const prev = prevMap.get(task.id);
    const label = taskLabel(task);
    const prevStatus = prev?.status || "";
    const nextStatus = task.status || "pending";

    if (nextStatus === "completed" && prevStatus !== "completed") {
      transitions.push({
        id: `${task.id}-completed`,
        kind: "completed",
        label,
      });
      continue;
    }

    if (
      nextStatus === "in_progress" &&
      prevStatus !== "in_progress" &&
      prevStatus !== "completed"
    ) {
      transitions.push({
        id: `${task.id}-started`,
        kind: "started",
        label,
      });
    }
  }

  // First snapshot: announce started tasks only
  if (!previous?.length) {
    return current
      .filter((task) => task.status === "in_progress")
      .map((task) => ({
        id: `${task.id}-started`,
        kind: "started" as const,
        label: taskLabel(task),
      }));
  }

  return transitions;
}
