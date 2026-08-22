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
  const chapters = resultMap.chapters
    ? Object.fromEntries(
        Object.entries(resultMap.chapters).map(([key, chapter]) => [
          key,
          chapter
            ? {
                ...chapter,
                queries: [...(chapter.queries || [])],
                docs: (chapter.docs || []).map((bucket) =>
                  Array.isArray(bucket) ? [...bucket] : []
                ),
              }
            : chapter,
        ])
      )
    : resultMap.chapters;

  return {
    ...resultMap,
    searchResult: cloneSearchResultSnapshot(resultMap.searchResult),
    chapters,
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

function getChaptersSignature(resultMap?: MESSAGE.ResultMap): string {
  const chapters = resultMap?.chapters;
  if (!chapters) {
    return "";
  }
  return Object.keys(chapters)
    .sort()
    .map((key) => {
      const chapter = chapters[key];
      return `${key}:${chapter?.summary?.length || 0}:${chapter?.docs?.length || 0}:${chapter?.streaming ? 1 : 0}`;
    })
    .join(",");
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
    resultMap.toolName ||
      (resultMap as { resultMap?: { toolName?: string } }).resultMap?.toolName ||
      "",
    resultMap.toolCallId ||
      (resultMap as { resultMap?: { toolCallId?: string } }).resultMap
        ?.toolCallId ||
      "",
    Array.isArray(resultMap.subAgentProgressLines)
      ? resultMap.subAgentProgressLines.length
      : 0,
    String(resultMap.subAgentLiveText || "").length,
    resultMap.subAgentElapsedMs ?? "",
    // 流式入参增长必须使签名失效，否则 WeakMap 会卡住旧 argumentsText
    typeof resultMap.argumentsText === "string"
      ? resultMap.argumentsText.length
      : 0,
    typeof (resultMap as { resultMap?: { argumentsText?: string } }).resultMap
      ?.argumentsText === "string"
      ? (resultMap as { resultMap?: { argumentsText?: string } }).resultMap!
          .argumentsText!.length
      : 0,
    toolCallTargetName,
    task.toolThought?.length || 0,
    resultMap.answer?.length || 0,
    resultMap.codeOutput?.length || 0,
    resultMap.data?.length || 0,
    artifactRefs.length,
    artifactRefs[0]?.resourceKey || artifactRefs[0]?.previewUrl || artifactRefs[0]?.downloadUrl || "",
    querySignature,
    docsSignature,
    getChaptersSignature(resultMap as MESSAGE.ResultMap),
    Array.isArray(plan?.stepStatus) ? plan.stepStatus.join(",") : "",
    // emit_ui_patch mutates ui_tree in place; include GenUI markers so render cache invalidates.
    getGenUiRenderSignature(resultMap as MESSAGE.ResultMap),
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

function findChapterForQuery(
  chapters: Record<string, MESSAGE.DeepSearchChapterState> | undefined | null,
  query: string
): MESSAGE.DeepSearchChapterState | undefined {
  if (!chapters || !query) {
    return undefined;
  }
  return Object.values(chapters).find((chapter) => {
    if (!chapter) {
      return false;
    }
    if (chapter.chapterTitle === query) {
      return true;
    }
    return Array.isArray(chapter.queries) && chapter.queries.includes(query);
  });
}

function isDeepSearchStageMap(resultMap?: MESSAGE.ResultMap | null): boolean {
  if (!resultMap) {
    return false;
  }
  const stage = String(resultMap.messageType || "");
  return Boolean(
    resultMap.searchResult ||
      resultMap.chapters ||
      resultMap.chapterSummary ||
      resultMap.chapterId ||
      stage === "extend" ||
      stage === "search" ||
      stage === "chapter_summary" ||
      stage === "report"
  );
}

function resolveDeepSearchInnerMap(
  task: RenderableTask
): MESSAGE.ResultMap | undefined {
  const nested = task.resultMap as MESSAGE.ResultMap | undefined;
  if (isDeepSearchStageMap(nested) && nested?.messageType !== "deep_search") {
    return nested;
  }
  if (isDeepSearchStageMap(nested?.resultMap)) {
    return nested?.resultMap;
  }
  if (nested?.searchResult || nested?.chapters || nested?.chapterSummary) {
    return nested;
  }
  // 兼容历史扁平载荷：searchResult 直接挂在 task 上
  const flat = task as MESSAGE.ResultMap & RenderableTask;
  if (flat.searchResult) {
    return flat as unknown as MESSAGE.ResultMap;
  }
  return nested?.resultMap || nested;
}

function inferDeepSearchStage(inner?: MESSAGE.ResultMap): string {
  const raw = String(inner?.messageType || "");
  if (
    raw === "extend" ||
    raw === "search" ||
    raw === "chapter_summary" ||
    raw === "report"
  ) {
    return raw;
  }
  if (inner?.chapterSummary || inner?.chapterId) {
    return "chapter_summary";
  }
  if (String(inner?.answer || "").trim() && !inner?.searchResult?.query?.length) {
    return "report";
  }
  if (inner?.searchFinish === false) {
    return "extend";
  }
  if (inner?.searchFinish === true) {
    return "search";
  }
  return raw || "search";
}

function processDeepSearchTask(
  task: RenderableTask,
  baseId: string
): CHAT.Task[] {
  const inner = resolveDeepSearchInnerMap(task);
  const messageType = inferDeepSearchStage(inner);

  if (messageType === "report") {
    const reportTask = createRenderTask(task, baseId);
    if (reportTask.resultMap && inner && reportTask.resultMap !== inner) {
      reportTask.resultMap = {
        ...reportTask.resultMap,
        ...inner,
        messageType: "report",
      };
    }
    return [reportTask];
  }

  const queries = (inner?.searchResult?.query || []).filter(Boolean);
  const chapterTitles = Object.values(inner?.chapters || {})
    .map((chapter) => String(chapter?.chapterTitle || "").trim())
    .filter(Boolean);
  const effectiveQueries = queries.length ? queries : chapterTitles;

  // 没有 query 也至少保留一张卡，避免工具卡在中间态被拆空。
  if (!effectiveQueries.length) {
    const fallback = createRenderTask(task, baseId);
    if (fallback.resultMap && inner && fallback.resultMap !== inner) {
      fallback.resultMap = {
        ...fallback.resultMap,
        ...inner,
        messageType: messageType === "extend" ? "extend" : "search",
      };
    }
    return [fallback];
  }

  return effectiveQueries.map((query: string, index: number) => {
    const chapter = findChapterForQuery(inner?.chapters, query);
    const rawDocs =
      inner?.searchResult?.docs?.[index] ||
      chapter?.docs?.[0];
    const docs = Array.isArray(rawDocs) ? rawDocs : rawDocs ? [rawDocs] : [];
    const searchResult = {
      query: [query],
      docs,
    };

    const renderTask = createRenderTask(
      task,
      baseId.concat(String(index)),
      searchResult
    );

    if (!renderTask.resultMap) {
      renderTask.resultMap = {} as CHAT.Task["resultMap"];
    }

    const hasSummary = Boolean(chapter?.summary);
    const cardStage = hasSummary
      ? "chapter_summary"
      : docs.length
        ? "search"
        : "extend";
    renderTask.resultMap = {
      ...renderTask.resultMap,
      messageType: cardStage,
      searchResult: searchResult as CHAT.Task["resultMap"]["searchResult"],
      chapterId: chapter?.chapterId,
      chapterTitle: chapter?.chapterTitle || query,
      chapterOrder: chapter?.chapterOrder ?? index + 1,
      chapterSummary: chapter?.summary || "",
      chapterStreaming: Boolean(chapter?.streaming),
      answer: chapter?.summary || "",
      chapters: inner?.chapters,
    };

    return renderTask;
  });
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
