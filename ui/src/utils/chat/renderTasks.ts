import { resolveToolCallTargetName } from "./toolCalls";

type RenderSearchResult = {
  query: string[];
  docs: MESSAGE.Doc[];
};

type RenderableTask = MESSAGE.Task | CHAT.Task;

type TaskRenderCacheEntry = {
  signature: string;
  items: CHAT.Task[];
};

const taskRenderCache = new WeakMap<object, TaskRenderCacheEntry>();

function cloneSearchResultSnapshot(searchResult?: MESSAGE.SearchResult) {
  if (!searchResult) {
    return searchResult;
  }

  // SSE 合并会原地更新 searchResult；渲染前复制数组，避免缓存中的旧任务被后续事件悄悄改写。
  return {
    ...searchResult,
    query: [...(searchResult.query || [])],
    docs: (searchResult.docs || []).map((item) =>
      Array.isArray(item) ? [...item] : item
    ),
  };
}

function cloneResultMapSnapshot(
  resultMap?: MESSAGE.ResultMap
): MESSAGE.ResultMap {
  if (!resultMap) {
    return {} as MESSAGE.ResultMap;
  }

  // 只复制渲染链会读取的可变数组，保留其它字段引用以控制复制成本。
  return {
    ...resultMap,
    searchResult: cloneSearchResultSnapshot(resultMap.searchResult),
    fileInfo: [...(resultMap.fileInfo || [])],
    fileList: [...(resultMap.fileList || [])],
    refList: [...(resultMap.refList || [])],
    steps: [...(resultMap.steps || [])],
  };
}

export function clonePlanForRender(plan?: MESSAGE.Plan) {
  if (!plan) {
    return plan;
  }

  return {
    ...plan,
    notes: [...(plan.notes || [])],
    stages: [...(plan.stages || [])],
    stepStatus: [...(plan.stepStatus || [])],
    steps: [...(plan.steps || [])],
  };
}

export function cloneTaskSnapshot(task: MESSAGE.Task): MESSAGE.Task {
  return {
    ...task,
    plan: clonePlanForRender(task.plan),
    resultMap: cloneResultMapSnapshot(task.resultMap),
    toolResult: task.toolResult
      ? {
        ...task.toolResult,
        toolParam: task.toolResult.toolParam
          ? { ...task.toolResult.toolParam }
          : task.toolResult.toolParam,
      }
      : task.toolResult,
  };
}

function getGenUiRenderSignature(resultMap?: MESSAGE.ResultMap): string {
  if (!resultMap) {
    return "";
  }
  const nested =
    (resultMap as { resultMap?: MESSAGE.ResultMap }).resultMap &&
    typeof (resultMap as { resultMap?: MESSAGE.ResultMap }).resultMap === "object"
      ? ((resultMap as { resultMap?: MESSAGE.ResultMap }).resultMap as MESSAGE.ResultMap)
      : resultMap;
  const nestedAny = nested as {
    tree?: unknown;
    patchCount?: number;
    lastPatchedAt?: string;
    appliedPatches?: unknown[];
  };
  const applied = Array.isArray(nestedAny.appliedPatches)
    ? nestedAny.appliedPatches.length
    : 0;
  return [
    nestedAny.tree ? "1" : "0",
    nestedAny.patchCount ?? 0,
    nestedAny.lastPatchedAt || "",
    applied,
  ].join(",");
}

function getTaskRenderSignature(task: RenderableTask, baseId: string): string {
  const resultMap = task.resultMap || {};
  const searchResult = resultMap.searchResult;
  const plan = task.plan;
  const artifactRefs = Array.isArray(task.artifactRefs) ? task.artifactRefs : [];
  const toolCallTargetName = resolveToolCallTargetName(
    resultMap as unknown as MESSAGE.ResultMap | undefined
  );
  const querySignature = Array.isArray(searchResult?.query)
    ? searchResult.query.join("||")
    : "";
  const docsSignature = Array.isArray(searchResult?.docs)
    ? searchResult.docs
      .map((docs: MESSAGE.Doc[] | MESSAGE.Doc) => (Array.isArray(docs) ? docs.length : 0))
      .join(",")
    : "";

  // 签名必须覆盖 tool/artifact/query/plan 等展示字段；否则同一个任务对象原地更新时 WeakMap 会返回过期结果。
  return [
    baseId,
    task.messageId || "",
    task.messageType || "",
    task.messageTime || "",
    resultMap.messageType || "",
    resultMap.isFinal ? "1" : "0",
    resultMap.searchFinish ? "1" : "0",
    resultMap.status || "",
    resultMap.summary || "",
    resultMap.toolName || "",
    resultMap.toolCallId || "",
    toolCallTargetName,
    task.toolThought?.length || 0,
    resultMap.answer?.length || 0,
    resultMap.codeOutput?.length || 0,
    resultMap.data?.length || 0,
    artifactRefs.length,
    artifactRefs[0]?.resourceKey || artifactRefs[0]?.previewUrl || artifactRefs[0]?.downloadUrl || "",
    querySignature,
    docsSignature,
    Array.isArray(plan?.stepStatus) ? plan.stepStatus.join(",") : "",
    // emit_ui_patch mutates ui_tree in place; include GenUI markers so render cache invalidates.
    getGenUiRenderSignature(resultMap),
  ].join("|");
}

function createRenderTask(
  task: RenderableTask,
  id: string,
  searchResult?: RenderSearchResult
): CHAT.Task {
  const nextTask = {
    ...task,
    id,
    resultMap: task.resultMap ? { ...task.resultMap } : task.resultMap,
    plan: clonePlanForRender(task.plan),
  } as CHAT.Task;

  if (searchResult && nextTask.resultMap) {
    nextTask.resultMap.searchResult = searchResult as CHAT.Task["resultMap"]["searchResult"];
  }

  return nextTask;
}

function processDeepSearchTask(
  task: RenderableTask,
  baseId: string
): CHAT.Task[] {
  const messageType = task.resultMap?.messageType;
  if (messageType === "report") {
    return [
      createRenderTask(task, baseId),
    ];
  }

  if (messageType === "extend" || messageType === "search") {
    const queries = task.resultMap?.searchResult?.query || [];

    // 查询分解和检索阶段都按 query 拆分；没有真实 query 时不制造伪占位项。
    if (!queries.length) {
      return [];
    }

    return queries.map((query: string, index: number) => {
      const rawDocs = task.resultMap.searchResult?.docs?.[index];
      const searchResult = {
        query: [query],
        docs: Array.isArray(rawDocs) ? rawDocs : rawDocs ? [rawDocs] : [],
      };

      return createRenderTask(task, baseId.concat(String(index)), searchResult);
    });
  }

  return [
    createRenderTask(task, baseId),
  ];
}

export function processTaskForRender(
  task: RenderableTask,
  baseId: string
): CHAT.Task[] {
  const signature = getTaskRenderSignature(task, baseId);
  const cached = taskRenderCache.get(task);
  if (cached?.signature === signature) {
    // 同一对象且展示输入未变化时复用拆分结果，避免每个 SSE chunk 都重复生成任务数组。
    return cached.items;
  }

  let items: CHAT.Task[];
  if (task.messageType === "deep_search") {
    items = processDeepSearchTask(task, baseId);
  } else {
    items = [createRenderTask(task, baseId)];
  }

  taskRenderCache.set(task, {
    signature,
    items,
  });

  return items;
}
