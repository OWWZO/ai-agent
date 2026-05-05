export type TimelineTaskContainer = {
  hidden: boolean;
  task?: string;
  children: CHAT.Task[];
  __placeholder?: boolean;
} & Partial<MESSAGE.Task>;

/**
 * 历史回放里即便不是 deepThink，也可能存在多个任务组。
 * 这里按索引兜底创建时间线分组，避免后续容器逻辑读到 undefined。
 */
export function ensureTimelineTaskGroup(
  chatList: TimelineTaskContainer[][],
  groupIndex: number
) {
  if (!Array.isArray(chatList[groupIndex])) {
    chatList[groupIndex] = [];
  }

  return chatList[groupIndex];
}

/**
 * 某些任务组里工具事件会早于 task 父节点到达，
 * 这里先创建一个临时容器承接左侧时间线子项，避免查询分解等阶段被直接丢弃。
 */
export function ensureTimelineTaskContainer(
  taskGroup: TimelineTaskContainer[],
  task?: MESSAGE.Task
): TimelineTaskContainer {
  const lastContainer = taskGroup[taskGroup.length - 1];
  if (lastContainer) {
    return lastContainer;
  }

  const placeholder: TimelineTaskContainer = {
    hidden: false,
    task: task?.task || "",
    taskId: task?.taskId,
    messageId: task?.messageId,
    messageTime: task?.messageTime,
    children: [],
    __placeholder: true,
  };
  taskGroup.push(placeholder);
  return placeholder;
}

/**
 * task 父节点晚到时，用正式 task 信息回填占位容器，
 * 保留之前已经挂上的工具子项。
 */
export function upsertTimelineTaskContainer(
  taskGroup: TimelineTaskContainer[],
  task: MESSAGE.Task
): TimelineTaskContainer {
  const lastContainer = taskGroup[taskGroup.length - 1];
  if (lastContainer?.__placeholder) {
    const children = lastContainer.children || [];
    Object.assign(lastContainer, {
      ...task,
      task: task.task,
      hidden: false,
      children,
    });
    delete lastContainer.__placeholder;
    return lastContainer;
  }

  const nextContainer: TimelineTaskContainer = {
    ...task,
    task: task.task,
    hidden: false,
    children: [],
  };
  taskGroup.push(nextContainer);
  return nextContainer;
}
