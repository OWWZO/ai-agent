type TimelineChildLike = Pick<CHAT.Task, "finish" | "isFinal">;

type TimelineTaskContainerLike = {
  task?: string;
  children?: TimelineChildLike[];
};

type TimelineGroupStatusInput = {
  isPlanSolve: boolean;
  isLastGroup: boolean;
  loading: boolean;
  tasks: TimelineTaskContainerLike[];
};

/**
 * 只有真实 task 容器且所有子工具都结束后，才认为该任务组完成。
 * 这样可以避免占位容器和普通工具组被误渲染成“已完成任务”。
 */
export function isTimelineTaskContainerCompleted(
  task: TimelineTaskContainerLike
): boolean {
  if (!task.task?.trim()) {
    return false;
  }

  if (!Array.isArray(task.children) || task.children.length === 0) {
    return false;
  }

  return task.children.every((child) => child.finish || child.isFinal);
}

/**
 * 时间线左侧的蓝色完成勾只属于 PlanSolve 的真实任务组。
 * ReAct/普通结构化对话不再展示这个视觉特效。
 */
export function shouldShowTimelineGroupCompletedIcon(
  input: TimelineGroupStatusInput
): boolean {
  const { isPlanSolve, isLastGroup, loading, tasks } = input;

  if (!isPlanSolve) {
    return false;
  }

  if (isLastGroup && loading) {
    return false;
  }

  return tasks.some((task) => isTimelineTaskContainerCompleted(task));
}
