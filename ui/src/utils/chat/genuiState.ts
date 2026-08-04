import { applyUiPatches } from "@/components/genui/applyUiPatch";

type PatchOp = { op: string; path: string; value?: unknown };

function readNestedResultMap(task?: CHAT.Task | MESSAGE.Task | null): any {
  if (!task) return {};
  const rm: any = task.resultMap || {};
  return rm.resultMap && typeof rm.resultMap === "object" ? rm.resultMap : rm;
}

export function getGenUiTreeFromTask(task?: CHAT.Task | MESSAGE.Task | null): any | null {
  const nested = readNestedResultMap(task);
  if (nested?.tree) return nested.tree;
  if ((task as any)?.resultMap?.tree) return (task as any).resultMap.tree;
  return null;
}

export function getGenUiPatchesFromTask(task?: CHAT.Task | MESSAGE.Task | null): PatchOp[] {
  const nested = readNestedResultMap(task);
  const patches = nested?.patches ?? (task as any)?.resultMap?.patches;
  return Array.isArray(patches) ? patches : [];
}

export function findLatestGenUiTreeIndex(taskGroup?: Array<CHAT.Task | MESSAGE.Task> | null): number {
  if (!Array.isArray(taskGroup) || !taskGroup.length) return -1;
  for (let i = taskGroup.length - 1; i >= 0; i--) {
    if (taskGroup[i]?.messageType === "ui_tree" && getGenUiTreeFromTask(taskGroup[i])) {
      return i;
    }
  }
  return -1;
}

/**
 * Apply patch task onto the latest ui_tree in the same task group.
 * Returns true if merged in place; false if caller should keep the patch task as-is.
 */
export function mergeUiPatchIntoTaskGroup(
  taskGroup: Array<CHAT.Task | MESSAGE.Task>,
  patchTask: CHAT.Task | MESSAGE.Task
): boolean {
  // patch 只投影到同一 task group 中最近的 ui_tree；tree 是展示基准，patch task
  // 只保留轻量 breadcrumb 状态。找不到基准时返回 false，由调用方继续保留独立 patch。
  const treeIndex = findLatestGenUiTreeIndex(taskGroup);
  if (treeIndex < 0) return false;

  const treeTask = taskGroup[treeIndex] as CHAT.Task;
  const baseTree = getGenUiTreeFromTask(treeTask);
  const patches = getGenUiPatchesFromTask(patchTask);
  if (!baseTree || !patches.length) return false;

  const nextTree = applyUiPatches(baseTree, patches);
  const prevRm: any = treeTask.resultMap || {};
  const prevNested =
    prevRm.resultMap && typeof prevRm.resultMap === "object" ? { ...prevRm.resultMap } : { ...prevRm };

  const applied = Array.isArray(prevNested.appliedPatches) ? [...prevNested.appliedPatches] : [];
  applied.push(...patches);

  const nextNested = {
    ...prevNested,
    tree: nextTree,
    appliedPatches: applied,
    patchCount: applied.length,
    lastPatchedAt: patchTask.messageTime || String(Date.now()),
    isFinal: true,
  };

  // Keep outer resultMap shape stable for buildTaskFromEventData consumers.
  // 兼容旧的 resultMap.resultMap 嵌套形态，避免合并后前端读取路径发生变化。
  if (prevRm.resultMap && typeof prevRm.resultMap === "object") {
    treeTask.resultMap = {
      ...prevRm,
      resultMap: nextNested,
      isFinal: true,
    };
  } else {
    treeTask.resultMap = {
      ...prevRm,
      ...nextNested,
      isFinal: true,
    };
  }

  // Light patch task for timeline breadcrumb (no full re-render needed elsewhere).
  const patchRm: any = patchTask.resultMap || {};
  // Preserve nested patches for getGenUiPatchesFromTask / breadcrumb count.
  if (patchRm.resultMap && typeof patchRm.resultMap === "object") {
    patchTask.resultMap = {
      ...patchRm,
      resultMap: {
        ...patchRm.resultMap,
        mergedIntoTree: true,
        patchCount: patches.length,
        isFinal: true,
      },
      mergedIntoTree: true,
      patchCount: patches.length,
      isFinal: true,
    };
  } else {
    patchTask.resultMap = {
      ...patchRm,
      mergedIntoTree: true,
      patchCount: patches.length,
      isFinal: true,
    };
  }

  return true;
}

/**
 * Search task groups from newest to oldest and merge patch into the latest ui_tree.
 * Plan/multi-step runs may place tree and patch in different task groups.
 */
export function mergeUiPatchIntoTasks(
  tasks: Array<Array<CHAT.Task | MESSAGE.Task>> | undefined | null,
  patchTask: CHAT.Task | MESSAGE.Task
): boolean {
  // 计划执行可能把 tree 和 patch 分到不同组，因此从最新组向前搜索；首次成功合并
  // 即停止，确保一个 patch 不会被重复应用到多个历史 tree。
  if (!Array.isArray(tasks) || !tasks.length) return false;
  for (let g = tasks.length - 1; g >= 0; g--) {
    const group = tasks[g];
    if (!Array.isArray(group) || !group.length) continue;
    if (mergeUiPatchIntoTaskGroup(group, patchTask)) {
      return true;
    }
  }
  return false;
}

/**
 * Resolve display tree for a ui_tree task (already includes applied patches if merged).
 */
export function resolveDisplayGenUiTree(task?: CHAT.Task | MESSAGE.Task | null): any | null {
  return getGenUiTreeFromTask(task);
}
