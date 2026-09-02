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

function getArtifactSignature(task: RenderableTask): string {
  const resultMap = task.resultMap || {};
  const files: unknown[] = [
    ...(task.fileList || []),
    ...(resultMap.fileList || []),
    ...(task.artifactRefs || []),
    ...(resultMap.artifactRefs || []),
  ];
  return files
    .map((value) => {
      const file = (value || {}) as Record<string, unknown>;
      return [
        file.displayName,
        file.fileName,
        file.name,
        file.resourceKey,
        file.previewUrl,
        file.domainUrl,
        file.downloadUrl,
        file.ossUrl,
        file.missing ? "1" : "0",
      ]
        .filter(Boolean)
        .join("@");
    })
    .filter(Boolean)
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
      ? `${resultMap.subAgentProgressLines.length}:${resultMap.subAgentProgressLines[resultMap.subAgentProgressLines.length - 1] || ""}`
      : 0,
    String(resultMap.subAgentLiveText || ""),
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
    task.toolThought || "",
    resultMap.answer || "",
    resultMap.codeOutput?.length || 0,
    resultMap.data?.length || 0,
    artifactRefs.length,
    artifactRefs[0]?.resourceKey || artifactRefs[0]?.previewUrl || artifactRefs[0]?.downloadUrl || "",
    getArtifactSignature(task),
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
    __timelineRendered: true,
    resultMap: task.resultMap ? { ...task.resultMap } : task.resultMap,
    plan: clonePlanForRender(task.plan),
  } as unknown as CHAT.Task;

  if (searchResult && nextTask.resultMap) {
    nextTask.resultMap.searchResult = searchResult as CHAT.Task["resultMap"]["searchResult"];
  }

  return nextTask;
}

type SearchResultWithChapters = MESSAGE.SearchResult & {
  chapters?:
    | Record<string, MESSAGE.DeepSearchChapterState>
    | MESSAGE.DeepSearchChapterState[];
};

type DeepSearchChapterEntry = {
  key: string;
  chapter: MESSAGE.DeepSearchChapterState;
};

function readChapterEntries(
  value:
    | Record<string, MESSAGE.DeepSearchChapterState>
    | MESSAGE.DeepSearchChapterState[]
    | undefined
    | null
): DeepSearchChapterEntry[] {
  if (Array.isArray(value)) {
    return value.reduce<DeepSearchChapterEntry[]>((entries, chapter, index) => {
      if (!chapter) {
        return entries;
      }
      entries.push({
        key: chapter.chapterId || chapter.chapterTitle || `chapter-${index}`,
        chapter,
      });
      return entries;
    }, []);
  }

  if (!value) {
    return [];
  }

  return Object.entries(value).reduce<DeepSearchChapterEntry[]>(
    (entries, [key, chapter]) => {
      if (!chapter) {
        return entries;
      }
      entries.push({
        key: chapter.chapterId || key,
        chapter,
      });
      return entries;
    },
    []
  );
}

function collectDeepSearchChapters(
  inner?: MESSAGE.ResultMap
): DeepSearchChapterEntry[] {
  const searchResult = inner?.searchResult as SearchResultWithChapters | undefined;
  const chapters = new Map<string, MESSAGE.DeepSearchChapterState>();

  // 搜索早期事件把完整章节目录放在 searchResult.chapters，章节总结阶段
  // 则把带查询词、来源和总结的状态放在 resultMap.chapters。两者合并后
  // 才能在整个流式过程里保持“一章一张卡”。
  for (const { key, chapter } of readChapterEntries(searchResult?.chapters)) {
    chapters.set(key, chapter);
  }
  for (const { key, chapter } of readChapterEntries(inner?.chapters)) {
    chapters.set(key, {
      ...chapters.get(key),
      ...chapter,
      chapterId: chapter.chapterId || chapters.get(key)?.chapterId || key,
      chapterTitle:
        chapter.chapterTitle || chapters.get(key)?.chapterTitle,
      chapterContent:
        chapter.chapterContent || chapters.get(key)?.chapterContent,
      chapterOrder:
        chapter.chapterOrder ?? chapters.get(key)?.chapterOrder,
      queries:
        chapter.queries?.length
          ? chapter.queries
          : chapters.get(key)?.queries,
      docs: chapter.docs?.length ? chapter.docs : chapters.get(key)?.docs,
      summary: chapter.summary || chapters.get(key)?.summary,
      streaming: chapter.streaming ?? chapters.get(key)?.streaming,
    });
  }

  return [...chapters.entries()]
    .map(([key, chapter]) => ({
      key,
      chapter
    }))
    .sort((left, right) => {
      const leftOrder = left.chapter.chapterOrder ?? Number.MAX_SAFE_INTEGER;
      const rightOrder = right.chapter.chapterOrder ?? Number.MAX_SAFE_INTEGER;
      return leftOrder - rightOrder;
    });
}

function resolveChapterDocs(
  chapter: MESSAGE.DeepSearchChapterState,
  chapterIndex: number,
  chapterCount: number,
  searchResult?: MESSAGE.SearchResult
): MESSAGE.Doc[] {
  if (chapter.docs?.length) {
    const docs = chapter.docs.flatMap((bucket) =>
      Array.isArray(bucket) ? bucket : []
    );
    if (docs.length) {
      return docs;
    }
  }

  // 搜索事件仍是扁平 query/docs 结构。按本章第一个查询词定位来源桶，
  // 支持章节并发完成时只有部分章节已有检索结果的中间状态。
  const firstQuery = chapter.queries?.find(Boolean);
  const queryIndex = firstQuery
    ? (searchResult?.query || []).indexOf(firstQuery)
    : -1;
  if (queryIndex < 0) {
    const populatedBuckets = (searchResult?.docs || []).filter(
      (bucket) => Array.isArray(bucket) && bucket.length > 0
    );
    if (populatedBuckets.length !== chapterCount) {
      return [];
    }
    return populatedBuckets[chapterIndex] || [];
  }

  const bucket = searchResult?.docs?.[queryIndex];
  return Array.isArray(bucket) ? bucket : [];
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
      } as unknown as CHAT.Task["resultMap"];
    }
    return [reportTask];
  }

  const chapterEntries = collectDeepSearchChapters(inner);
  if (chapterEntries.length) {
    const searchResult = inner?.searchResult;
    return chapterEntries.map(({ key, chapter }, index) => {
      const chapterTitle = String(chapter.chapterTitle || "").trim();
      const chapterQueries = (chapter.queries || []).filter(Boolean);
      const query = chapterTitle || chapterQueries[0] || key;
      const docs = resolveChapterDocs(
        chapter,
        index,
        chapterEntries.length,
        searchResult
      );
      const chapterSearchResult = {
        query: chapterQueries.length ? chapterQueries : [query],
        docs,
      };
      const renderTask = createRenderTask(
        task,
        baseId.concat(String(index)),
        chapterSearchResult
      );

      if (!renderTask.resultMap) {
        renderTask.resultMap = {} as CHAT.Task["resultMap"];
      }

      const hasSummary = Boolean(chapter.summary);
      const cardStage = hasSummary
        ? "chapter_summary"
        : docs.length
          ? "search"
          : "extend";
      renderTask.resultMap = {
        ...renderTask.resultMap,
        messageType: cardStage,
        searchResult: chapterSearchResult as CHAT.Task["resultMap"]["searchResult"],
        chapterId: chapter.chapterId || key,
        chapterTitle: chapterTitle || query,
        chapterOrder: chapter.chapterOrder ?? index + 1,
        chapterSummary: chapter.summary || "",
        chapterStreaming: Boolean(chapter.streaming),
        answer: chapter.summary || "",
        chapters: inner?.chapters,
      } as unknown as CHAT.Task["resultMap"];

      return renderTask;
    });
  }

  const queries = (inner?.searchResult?.query || []).filter(Boolean);

  // 没有 query 也至少保留一张卡，避免工具卡在中间态被拆空。
  if (!queries.length) {
    const fallback = createRenderTask(task, baseId);
    if (fallback.resultMap && inner && fallback.resultMap !== inner) {
      fallback.resultMap = {
        ...fallback.resultMap,
        ...inner,
        messageType: messageType === "extend" ? "extend" : "search",
      } as unknown as CHAT.Task["resultMap"];
    }
    return [fallback];
  }

  return queries.map((query: string, index: number) => {
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
    } as unknown as CHAT.Task["resultMap"];

    return renderTask;
  });
}

export function processTaskForRender(
  task: RenderableTask,
  baseId: string
): CHAT.Task[] {
  if (
    (task as CHAT.Task & { __timelineRendered?: boolean }).__timelineRendered
  ) {
    return [task as CHAT.Task];
  }
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
