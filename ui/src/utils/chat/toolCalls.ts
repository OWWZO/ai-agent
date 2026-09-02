function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function getArtifactIdentity(artifact: MESSAGE.ArtifactReference) {
  // 资源 key 优先于 URL 和展示名，最后才退回完整 JSON，保证同一产物在不同事件中只保留一份。
  return (
    artifact.resourceKey ||
    artifact.previewUrl ||
    artifact.downloadUrl ||
    artifact.displayName ||
    JSON.stringify(artifact)
  );
}

export function pickFirstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value !== "string") {
      continue;
    }
    const trimmed = value.trim();
    if (trimmed) {
      return trimmed;
    }
  }
  return "";
}

export function resolveToolCallInput(resultMap?: MESSAGE.ResultMap) {
  // 新旧事件分别使用 input/toolParam；只接受普通对象，避免数组或 null 被误当成参数映射。
  // 实时 SSE 经 AgentSessionPrinter 多包一层 resultMap，入参常在 nested.input。
  const input = resultMap?.input;
  if (isRecord(input)) {
    return input;
  }

  const toolParam = resultMap?.toolParam;
  if (isRecord(toolParam)) {
    return toolParam;
  }

  const nested = isRecord(resultMap?.resultMap)
    ? (resultMap.resultMap as MESSAGE.ResultMap)
    : undefined;
  if (nested && nested !== resultMap) {
    if (isRecord(nested.input)) {
      return nested.input;
    }
    if (isRecord(nested.toolParam)) {
      return nested.toolParam;
    }
  }

  return {};
}

export type ResolvedToolResult = {
  toolName?: string;
  toolResult?: string;
  toolParam?: Record<string, unknown>;
  toolCallId?: string;
};

function readResultMapLayers(task?: Record<string, unknown>) {
  const layers: Record<string, unknown>[] = [];
  let current = task?.resultMap;
  for (let depth = 0; depth < 4 && isRecord(current); depth += 1) {
    layers.push(current);
    current = current.resultMap;
  }
  return layers;
}

/** 将任务的多层 resultMap 合并为 renderer 可直接读取的结果视图。 */
export function resolveTaskResultMap(
  task?: Partial<MESSAGE.Task> | Partial<CHAT.Task> | Record<string, unknown>
): MESSAGE.ResultMap {
  if (!task) {
    return {} as MESSAGE.ResultMap;
  }

  const layers = readResultMapLayers(task as Record<string, unknown>);
  return layers
    .reduce<Record<string, unknown>>(
      (merged, layer) => ({
        ...merged,
        ...layer,
      }),
      {}
    ) as MESSAGE.ResultMap;
}

function readToolResultCandidate(value: unknown): ResolvedToolResult | undefined {
  if (typeof value === "string") {
    return { toolResult: value };
  }
  if (!isRecord(value)) {
    return undefined;
  }

  const toolResult = value.toolResult;
  let normalizedText = "";
  if (typeof toolResult === "string") {
    normalizedText = toolResult;
  } else if (toolResult != null) {
    try {
      normalizedText = JSON.stringify(toolResult) || "";
    } catch {
      normalizedText = String(toolResult);
    }
  }
  const toolParam = isRecord(value.toolParam) ? value.toolParam : undefined;
  const normalized = {
    toolName: pickFirstText(value.toolName) || undefined,
    toolResult: normalizedText || undefined,
    toolParam,
    toolCallId: pickFirstText(value.toolCallId) || undefined,
  };

  return Object.values(normalized).some(Boolean) ? normalized : undefined;
}

/**
 * 统一读取 realtime/history/sub-agent 任务里的 tool_result。
 * 不同事件包装可能把它放在 task、resultMap 或多层 resultMap 下。
 */
export function resolveTaskToolResult(
  task?: Partial<MESSAGE.Task> | Partial<CHAT.Task> | Record<string, unknown>
): ResolvedToolResult | undefined {
  if (!task) {
    return undefined;
  }

  const record = task as Record<string, unknown>;
  const layers = readResultMapLayers(record);
  const candidates = [
    readToolResultCandidate(record.toolResult),
    ...layers.map((layer) => readToolResultCandidate(layer.toolResult)),
  ].filter((candidate): candidate is ResolvedToolResult => Boolean(candidate));

  if (!candidates.length) {
    return undefined;
  }

  const firstParam = candidates.find((candidate) => candidate.toolParam)?.toolParam;
  const result = {
    toolName: pickFirstText(
      ...candidates.map((candidate) => candidate.toolName),
      ...layers.map((layer) => layer.toolName),
      record.toolName
    ) || undefined,
    toolResult: pickFirstText(
      ...candidates.map((candidate) => candidate.toolResult),
      ...layers.map((layer) => layer.toolResult)
    ) || undefined,
    toolParam:
      firstParam ||
      (() => {
        const input = resolveToolCallInput(record.resultMap as MESSAGE.ResultMap | undefined);
        return Object.keys(input).length ? input : undefined;
      })(),
    toolCallId: pickFirstText(
      ...candidates.map((candidate) => candidate.toolCallId),
      ...layers.map((layer) => layer.toolCallId),
      record.toolCallId
    ) || undefined,
  };

  return Object.values(result).some(Boolean) ? result : undefined;
}

/** 工具结果正文的统一读取入口，兼容旧事件的 data/answer/codeOutput 兜底字段。 */
export function resolveTaskToolResultText(
  task?: Partial<MESSAGE.Task> | Partial<CHAT.Task> | Record<string, unknown>
) {
  const record = task as Record<string, unknown> | undefined;
  const result = resolveTaskToolResult(task);
  if (result?.toolResult?.trim()) {
    return result.toolResult;
  }

  const layers = readResultMapLayers(record);
  return pickFirstText(
    record?.result,
    ...layers.map((layer) => layer.result),
    ...layers.flatMap((layer) => [
      layer.data,
      layer.codeOutput,
      layer.answer,
      layer.summary,
      layer.errorMsg,
    ])
  );
}

export function resolveToolCallArgumentsText(resultMap?: MESSAGE.ResultMap) {
  if (!resultMap) {
    return "";
  }
  // argumentsRaw 优先；兼容历史 argumentsText
  if (typeof resultMap.argumentsRaw === "string" && resultMap.argumentsRaw) {
    return resultMap.argumentsRaw;
  }
  if (typeof resultMap.argumentsText === "string" && resultMap.argumentsText) {
    return resultMap.argumentsText;
  }
  const nested = isRecord(resultMap.resultMap)
    ? (resultMap.resultMap as MESSAGE.ResultMap)
    : null;
  if (typeof nested?.argumentsRaw === "string" && nested.argumentsRaw) {
    return nested.argumentsRaw;
  }
  return typeof nested?.argumentsText === "string" ? nested.argumentsText : "";
}

/** 流式 tool_call 卡片稳定键：streamToolKey > toolCallId > messageId */
export function resolveToolCallStreamKey(
  task?: Partial<MESSAGE.Task> | Partial<CHAT.Task> | MESSAGE.ResultMap
) {
  if (!task) {
    return "";
  }
  const record = task as Record<string, unknown>;
  const resultMap = (record.resultMap || record) as Record<string, unknown>;
  const nested = isRecord(resultMap.resultMap)
    ? (resultMap.resultMap as Record<string, unknown>)
    : {};
  return pickFirstText(
    resultMap.streamToolKey,
    nested.streamToolKey,
    record.streamToolKey,
    resolveTaskToolCallId(task as Partial<MESSAGE.Task>),
    typeof record.messageId === "string" ? record.messageId : "",
    typeof resultMap.messageId === "string" ? resultMap.messageId : ""
  );
}

export function resolveToolCallTargetName(resultMap?: MESSAGE.ResultMap) {
  const input = resolveToolCallInput(resultMap);
  // 文件类工具的目标字段历史上多次改名，按稳定性从显式主文件名到 path 依次回退。
  return pickFirstText(
    resultMap?.primaryFileName,
    input.fileName,
    input.file_name,
    input.filename,
    input.outputFileName,
    input.displayName,
    input.name,
    input.path,
    input.targetPath,
  );
}

export function resolveTaskToolCallId(
  task?: Partial<MESSAGE.Task> | Partial<CHAT.Task>
) {
  if (!task) {
    return "";
  }

  const record = task as Record<string, unknown>;
  const resultMap = (record.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolResult = (record.toolResult || {}) as Record<string, unknown>;
  const resolvedToolResult = resolveTaskToolResult(task);

  // 兼容 realtime / history / 多层 resultMap 展开后的多种落点，避免父 Agent
  // 无法登记 toolCallId 导致子工具挂不上 children。
  return pickFirstText(
    resultMap.toolCallId,
    nested.toolCallId,
    toolResult.toolCallId,
    resolvedToolResult?.toolCallId,
    record.toolCallId,
  );
}

export function resolveToolCallActionText(task: CHAT.Task) {
  const status = task.resultMap?.status;
  if (status === "success") {
    return "工具调用完成";
  }
  if (status === "failed") {
    return "工具调用失败";
  }
  if (status === "streaming" || status === "preparing") {
    return "正在生成工具参数";
  }
  if (task.resultMap?.isFinal) {
    return "工具调用完成";
  }
  return "正在调用工具";
}

export function isImageGenerationToolResultTask(task?: Partial<MESSAGE.Task>) {
  return task?.messageType === "tool_result" &&
    resolveTaskToolResult(task)?.toolName === "image_generation_tool";
}

export function isImageGenerationFileTask(task?: Partial<MESSAGE.Task>) {
  return task?.messageType === "file" &&
    task?.resultMap?.command === "生成图片";
}

export function findLastTaskIndex<TTask>(
  tasks: TTask[],
  matcher: (task: TTask) => boolean
) {
  for (let index = tasks.length - 1; index >= 0; index -= 1) {
    if (matcher(tasks[index])) {
      return index;
    }
  }
  return -1;
}

export function findToolCallPlaceholderIndex(
  tasks: MESSAGE.Task[],
  toolCallId: string | undefined
) {
  if (!toolCallId) {
    return -1;
  }

  // 从尾部查找，因为同一 toolCallId 在重试/流式更新中可能留下多个占位任务。
  return findLastTaskIndex(tasks, (task) =>
    task.messageType === "tool_call" &&
    resolveTaskToolCallId(task) === toolCallId
  );
}

export function findTaskIndexByToolCallId(
  tasks: MESSAGE.Task[],
  toolCallId: string | undefined,
  options?: {
    excludeMessageType?: string;
  }
) {
  if (!toolCallId) {
    return -1;
  }

  return findLastTaskIndex(tasks, (task) => {
    if (options?.excludeMessageType && task.messageType === options.excludeMessageType) {
      return false;
    }
    return resolveTaskToolCallId(task) === toolCallId;
  });
}

function readHtmlPreviewFileInfo(task: MESSAGE.Task | undefined) {
  if (!task) {
    return [];
  }
  const resultMap = resolveTaskResultMap(task) as Record<string, unknown>;
  const nested = isRecord(resultMap.resultMap) ? resultMap.resultMap : undefined;
  const candidates = [
    nested?.fileInfo,
    resultMap.fileInfo,
    task.resultMap?.fileInfo,
    (task as { fileInfo?: unknown }).fileInfo,
  ];
  for (const candidate of candidates) {
    if (Array.isArray(candidate) && candidate.length) {
      return candidate;
    }
  }
  return [];
}

/**
 * canvas_publish 的 html 预览事件应并入原 tool_call 卡，
 * 保留工具卡片的同时带上 preview/download，供右侧工作区打开。
 */
export function mergeHtmlPreviewIntoToolCall(
  toolTask: MESSAGE.Task,
  htmlTask: MESSAGE.Task
): MESSAGE.Task {
  const htmlMap = resolveTaskResultMap(htmlTask) as Record<string, unknown>;
  const nested = isRecord(htmlMap.resultMap) ? htmlMap.resultMap : undefined;
  const fileInfo = readHtmlPreviewFileInfo(htmlTask);
  const artifactRefs = Array.isArray(htmlTask.artifactRefs) && htmlTask.artifactRefs.length
    ? [...htmlTask.artifactRefs]
    : Array.isArray(toolTask.artifactRefs)
      ? [...toolTask.artifactRefs]
      : undefined;
  const previewUrl = pickFirstText(
    htmlMap.previewUrl,
    nested?.previewUrl,
    htmlMap.domainUrl,
    nested?.domainUrl
  );
  const downloadUrl = pickFirstText(
    htmlMap.downloadUrl,
    nested?.downloadUrl,
    htmlMap.ossUrl,
    nested?.ossUrl
  );
  const primaryFileName = pickFirstText(
    htmlMap.primaryFileName,
    nested?.primaryFileName,
    htmlMap.fileName,
    nested?.fileName
  );

  return {
    ...toolTask,
    ...(artifactRefs?.length ? { artifactRefs } : {}),
    resultMap: {
      ...(toolTask.resultMap || {}),
      ...(fileInfo.length ? { fileInfo } : {}),
      ...(previewUrl ? { previewUrl } : {}),
      ...(downloadUrl ? { downloadUrl } : {}),
      ...(primaryFileName ? { primaryFileName } : {}),
    },
  };
}

export function mergeImageGenerationToolTask(
  toolTask: MESSAGE.Task,
  fileTask: MESSAGE.Task
): MESSAGE.Task {
  // 工具结果和后续 file 事件分别携带 artifact/fileInfo；以 file 事件为准，缺失时保留工具事件已有值。
  const artifactRefs = Array.isArray(fileTask.artifactRefs)
    ? [...fileTask.artifactRefs]
    : Array.isArray(toolTask.artifactRefs)
      ? [...toolTask.artifactRefs]
      : undefined;
  const mergedFileInfo = Array.isArray(fileTask.resultMap?.fileInfo)
    ? [...fileTask.resultMap.fileInfo]
    : toolTask.resultMap?.fileInfo;

  return {
    ...toolTask,
    ...(artifactRefs?.length ? { artifactRefs } : {}),
    resultMap: {
      ...(toolTask.resultMap || {}),
      ...(mergedFileInfo?.length ? { fileInfo: mergedFileInfo } : {}),
    },
  };
}

/**
 * 将顶层 artifactRefs 统一挂到任务对象上，供后续预览链路复用。
 */
export function mergeTaskArtifactRefs(
  targetTask: MESSAGE.Task | undefined,
  eventData?: MESSAGE.EventData
) {
  if (!targetTask || !Array.isArray(eventData?.artifactRefs) || !eventData?.artifactRefs.length) {
    return;
  }

  const previousRefs = Array.isArray(targetTask.artifactRefs)
    ? targetTask.artifactRefs
    : [];
  // 事件可能重复到达，先合并再按资源身份去重，保持任务对象作为后续预览链路的唯一入口。
  const mergedRefs = [...previousRefs, ...eventData.artifactRefs];

  targetTask.artifactRefs = mergedRefs.filter((artifact, index, current) =>
    index === current.findIndex((item) => getArtifactIdentity(item) === getArtifactIdentity(artifact))
  );
}
