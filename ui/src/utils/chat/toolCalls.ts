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
  const input = resultMap?.input;
  if (isRecord(input)) {
    return input;
  }

  const toolParam = resultMap?.toolParam;
  if (isRecord(toolParam)) {
    return toolParam;
  }

  return {};
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

  return pickFirstText(
    task.resultMap?.toolCallId,
    task.toolResult?.toolCallId,
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
  if (task.resultMap?.isFinal) {
    return "工具调用完成";
  }
  return "正在调用工具";
}

export function isImageGenerationToolResultTask(task?: Partial<MESSAGE.Task>) {
  return task?.messageType === "tool_result" &&
    task?.toolResult?.toolName === "image_generation_tool";
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
