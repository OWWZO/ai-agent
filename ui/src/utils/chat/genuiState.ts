import { applyUiPatches } from "@/components/genui/applyUiPatch";

type PatchOp = { op: string; path: string; value?: unknown };

function readNestedResultMap(task?: CHAT.Task | MESSAGE.Task | null): any {
  if (!task) return {};
  const rm: any = task.resultMap || {};
  return rm.resultMap && typeof rm.resultMap === "object" ? rm.resultMap : rm;
}

function writeNestedField(
  task: CHAT.Task | MESSAGE.Task,
  fields: Record<string, unknown>
): void {
  const prevRm: any = task.resultMap || {};
  if (prevRm.resultMap && typeof prevRm.resultMap === "object") {
    task.resultMap = {
      ...prevRm,
      resultMap: {
        ...prevRm.resultMap,
        ...fields,
      },
      isFinal: true,
    };
  } else {
    task.resultMap = {
      ...prevRm,
      ...fields,
      isFinal: true,
    };
  }
}

/** 首次看到 tree 时冻结 originalTree，后续 merge 不覆盖，供展示层可靠重放。 */
export function ensureOriginalTree(task?: CHAT.Task | MESSAGE.Task | null): any | null {
  if (!task) return null;
  const nested = readNestedResultMap(task);
  if (nested?.originalTree) return nested.originalTree;
  const tree = nested?.tree ?? (task as any)?.resultMap?.tree ?? (task as any)?.tree;
  if (!tree) return null;
  try {
    const frozen = JSON.parse(JSON.stringify(tree));
    writeNestedField(task, { originalTree: frozen });
    return frozen;
  } catch {
    return tree;
  }
}

export function getGenUiTreeFromTask(task?: CHAT.Task | MESSAGE.Task | null): any | null {
  if (!task) return null;
  const nested = readNestedResultMap(task);
  if (nested?.tree) return nested.tree;
  if ((task as any)?.resultMap?.tree) return (task as any).resultMap.tree;
  if ((task as any)?.tree) return (task as any).tree;
  return null;
}

export function getGenUiPatchesFromTask(task?: CHAT.Task | MESSAGE.Task | null): PatchOp[] {
  if (!task) return [];
  const nested = readNestedResultMap(task);
  const candidates = [
    nested?.patches,
    (task as any)?.resultMap?.patches,
    (task as any)?.resultMap?.resultMap?.patches,
    (task as any)?.patches,
  ];
  for (const patches of candidates) {
    if (Array.isArray(patches) && patches.length) {
      return patches as PatchOp[];
    }
  }
  return [];
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

function markPatchMerged(
  patchTask: CHAT.Task | MESSAGE.Task,
  patches: PatchOp[]
): void {
  writeNestedField(patchTask, {
    mergedIntoTree: true,
    patchCount: patches.length,
    isFinal: true,
  });
  // 外层也打标，兼容浅读
  const rm: any = patchTask.resultMap || {};
  patchTask.resultMap = {
    ...rm,
    mergedIntoTree: true,
    patchCount: patches.length,
    isFinal: true,
  };
}

/**
 * Apply patch task onto the latest ui_tree in the same task group.
 */
export function mergeUiPatchIntoTaskGroup(
  taskGroup: Array<CHAT.Task | MESSAGE.Task>,
  patchTask: CHAT.Task | MESSAGE.Task
): boolean {
  const treeIndex = findLatestGenUiTreeIndex(taskGroup);
  if (treeIndex < 0) return false;

  const treeTask = taskGroup[treeIndex] as CHAT.Task;
  ensureOriginalTree(treeTask);
  const baseTree = getGenUiTreeFromTask(treeTask);
  const patches = getGenUiPatchesFromTask(patchTask);
  if (!baseTree || !patches.length) return false;

  const prevNested = readNestedResultMap(treeTask);
  const applied = Array.isArray(prevNested.appliedPatches)
    ? [...prevNested.appliedPatches]
    : [];
  applied.push(...patches);
  const nextTree = applyUiPatches(baseTree, patches);
  writeNestedField(treeTask, {
    tree: nextTree,
    appliedPatches: applied,
    patchCount: applied.length,
    lastPatchedAt: patchTask.messageTime || String(Date.now()),
    isFinal: true,
  });
  markPatchMerged(patchTask, patches);
  return true;
}

export function mergeUiPatchIntoTasks(
  tasks: Array<Array<CHAT.Task | MESSAGE.Task>> | undefined | null,
  patchTask: CHAT.Task | MESSAGE.Task
): boolean {
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

export function resolveDisplayGenUiTree(task?: CHAT.Task | MESSAGE.Task | null): any | null {
  return getGenUiTreeFromTask(task);
}

export type FeaturedGenUi = {
  task: CHAT.Task;
  tree: any;
  patchCount: number;
  revision: string;
};

function collectToolsDepthFirst(
  nodes?: Array<CHAT.Task | MESSAGE.Task> | null,
  out: Array<CHAT.Task | MESSAGE.Task> = []
): Array<CHAT.Task | MESSAGE.Task> {
  if (!Array.isArray(nodes)) return out;
  for (const node of nodes) {
    if (!node) continue;
    out.push(node);
    const children = (node as CHAT.Task).children;
    if (Array.isArray(children) && children.length) {
      collectToolsDepthFirst(children, out);
    }
  }
  return out;
}

function collectFromTaskMatrix(
  tasks?: Array<Array<CHAT.Task | MESSAGE.Task>> | null
): Array<CHAT.Task | MESSAGE.Task> {
  const flat: Array<CHAT.Task | MESSAGE.Task> = [];
  if (!Array.isArray(tasks)) return flat;
  for (const group of tasks) {
    collectToolsDepthFirst(group, flat);
  }
  return flat;
}

function rebuildFeaturedFromFlat(
  flat: Array<CHAT.Task | MESSAGE.Task>
): FeaturedGenUi | null {
  let treeTask: CHAT.Task | null = null;
  let treeIndex = -1;
  for (let i = 0; i < flat.length; i++) {
    if (flat[i]?.messageType === "ui_tree" && getGenUiTreeFromTask(flat[i])) {
      treeTask = flat[i] as CHAT.Task;
      treeIndex = i;
    }
  }
  if (!treeTask || treeIndex < 0) return null;

  // 优先 originalTree，保证每次从初始基准 + 全量 patch 重放
  const original = ensureOriginalTree(treeTask);
  let tree = original || getGenUiTreeFromTask(treeTask);
  if (!tree) return null;

  const allPatches: PatchOp[] = [];
  for (let i = treeIndex + 1; i < flat.length; i++) {
    const task = flat[i];
    if (task?.messageType === "ui_tree" && getGenUiTreeFromTask(task)) {
      treeTask = task as CHAT.Task;
      tree = ensureOriginalTree(task) || getGenUiTreeFromTask(task);
      allPatches.length = 0;
      continue;
    }
    if (task?.messageType !== "ui_patch") continue;
    const patches = getGenUiPatchesFromTask(task);
    if (!patches.length) continue;
    // 展示层始终重放全部 patch 事件（相对 originalTree，不会双计）
    allPatches.push(...patches);
  }

  const deduped: PatchOp[] = [];
  const seen = new Set<string>();
  for (const p of allPatches) {
    const key = JSON.stringify(p);
    if (seen.has(key)) continue;
    seen.add(key);
    deduped.push(p);
  }

  if (deduped.length) {
    tree = applyUiPatches(tree, deduped);
  }

  // 用 patch 内容摘要 + 条数驱动 React key，保证 emit_ui_patch 后最终回复区必重挂
  const patchDigest = deduped
    .map((p) => `${p.op}:${p.path}:${JSON.stringify(p.value ?? null)}`)
    .join("|")
    .slice(0, 400);
  const revision = `${treeTask.messageId || treeTask.id || "t"}:${deduped.length}:${patchDigest}`;

  return {
    task: treeTask,
    tree,
    patchCount: deduped.length,
    revision,
  };
}

/**
 * multiAgent.tasks 优先（事件真相源），chat.tasks 兜底。
 * 始终 originalTree + 后续全部 ui_patch 重放，保证主回复区随 patch 更新。
 */
export function findFeaturedGenUi(
  tasks?: Array<Array<CHAT.Task | MESSAGE.Task>> | null,
  multiAgentTasks?: Array<Array<CHAT.Task | MESSAGE.Task>> | null
): FeaturedGenUi | null {
  // 先冻结 multiAgent 里的 originalTree
  for (const t of collectFromTaskMatrix(multiAgentTasks)) {
    if (t?.messageType === "ui_tree") ensureOriginalTree(t);
  }
  const fromMulti = rebuildFeaturedFromFlat(collectFromTaskMatrix(multiAgentTasks));
  if (fromMulti) return fromMulti;
  for (const t of collectFromTaskMatrix(tasks)) {
    if (t?.messageType === "ui_tree") ensureOriginalTree(t);
  }
  return rebuildFeaturedFromFlat(collectFromTaskMatrix(tasks));
}
