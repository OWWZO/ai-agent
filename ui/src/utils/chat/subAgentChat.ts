import { resolveSubAgentDisplay } from "./subagent";
import { buildConversationTaskData } from "../chat";
import { processTaskForRender } from "./renderTasks";
import { findBestAgentTask, identityKeys, readTaskIdentity } from "./taskIdentity";
import { resolveTaskResultMap } from "./toolCalls";
import { getTaskFiles } from "@/utils/taskArtifacts";

function asText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function taskFileKey(file: CHAT.TFile): string {
  return file.resourceKey || file.url || file.downloadUrl || file.name;
}

function collectTaskFiles(task: CHAT.Task): CHAT.TFile[] {
  const files = new Map<string, CHAT.TFile>();
  const visit = (current?: CHAT.Task) => {
    if (!current) {
      return;
    }
    for (const file of getTaskFiles(current)) {
      const key = taskFileKey(file);
      if (key) {
        files.set(key, file);
      }
    }
    for (const child of current.children || []) {
      visit(child);
    }
  };
  visit(task);
  return [...files.values()];
}

function stripArtifactSection(value: string): string {
  const delimiterIndex = value.indexOf("$$$");
  return delimiterIndex >= 0
    ? value.slice(0, delimiterIndex).trim()
    : value;
}

function isNativeReasoningTask(task: CHAT.Task): boolean {
  return (
    task.messageType === "llm_reasoning" || task.messageType === "plan_thought"
  );
}

function hasAssistantReply(children: CHAT.Task[]): boolean {
  return children.some(
    (child) => child.messageType === "tool_thought" && asText(child.toolThought)
  );
}

function buildLiveTextTask(
  tool: CHAT.Task,
  liveText: string,
  running: boolean
): CHAT.Task {
  const id = `${tool.id || tool.messageId || "subagent"}:live-text`;
  return {
    id,
    messageId: id,
    messageType: "tool_thought",
    toolThought: liveText,
    messageTime: tool.messageTime,
    requestId: tool.requestId,
    taskId: tool.taskId,
    finish: !running,
    isFinal: !running,
    resultMap: { isFinal: !running },
  } as CHAT.Task;
}

function toArtifactRef(file: CHAT.TFile): MESSAGE.ArtifactReference {
  return {
    displayName: file.name,
    resourceKey: file.resourceKey,
    previewUrl: file.url,
    downloadUrl: file.downloadUrl,
    fileSize: file.size,
    mimeType: file.mimeType,
    missing: file.missing,
    missingReason: file.missingReason,
  };
}

function buildConclusionTask(
  tool: CHAT.Task,
  content: string,
  source?: CHAT.Task,
  fallbackFiles: CHAT.TFile[] = []
): CHAT.Task {
  const id = `${tool.id || tool.messageId || "subagent"}:conclusion`;
  const sourceRecord = source as unknown as Record<string, unknown> | undefined;
  const sourceMap = source ? resolveTaskResultMap(source) : {};
  const sourceFileList = Array.isArray(sourceRecord?.fileList)
    ? sourceRecord.fileList
    : Array.isArray(sourceMap.fileList)
      ? sourceMap.fileList
      : undefined;
  const artifactRefs =
    source?.artifactRefs?.length
      ? [...source.artifactRefs]
      : Array.isArray(sourceMap.artifactRefs) && sourceMap.artifactRefs.length
        ? [...sourceMap.artifactRefs]
        : fallbackFiles.map(toArtifactRef);
  return {
    id,
    messageId: id,
    messageType: "result",
    result: content,
    messageTime: tool.messageTime,
    requestId: tool.requestId,
    taskId: tool.taskId,
    finish: true,
    isFinal: true,
    ...(artifactRefs.length ? { artifactRefs } : {}),
    ...(sourceFileList?.length ? { fileList: sourceFileList } : {}),
    resultMap: {
      ...(source?.resultMap || {}),
      isFinal: true,
      taskSummary: content,
      result: content,
      ...(sourceFileList?.length ? { fileList: sourceFileList } : {}),
    },
  } as CHAT.Task;
}

function resolveNestedConclusion(children: CHAT.Task[]): {
  text: string;
  task?: CHAT.Task;
} {
  for (let i = children.length - 1; i >= 0; i -= 1) {
    const child = children[i];
    if (child?.messageType !== "result") {
      continue;
    }
    const resultMap = (child.resultMap || {}) as Record<string, unknown>;
    const resolvedResultMap = resolveTaskResultMap(child) as Record<string, unknown>;
    const text =
      asText(child.result) ||
      asText(resultMap.result) ||
      asText(resultMap.taskSummary) ||
      asText(resolvedResultMap.result) ||
      asText(resolvedResultMap.taskSummary) ||
      asText(child.taskSummary);
    if (text) {
      return {
        text,
        task: child,
      };
    }
  }
  return { text: "" };
}

function mergeProcessChildren(
  children: CHAT.Task[],
  liveTask: CHAT.Task | null
): CHAT.Task[] {
  if (!liveTask || hasAssistantReply(children)) {
    return children;
  }
  let insertAt = 0;
  while (
    insertAt < children.length &&
    isNativeReasoningTask(children[insertAt])
  ) {
    insertAt += 1;
  }
  return [
    ...children.slice(0, insertAt),
    liveTask,
    ...children.slice(insertAt),
  ];
}

function isRenderedTask(task: CHAT.Task): boolean {
  return Boolean(
    (task as CHAT.Task & { __timelineRendered?: boolean }).__timelineRendered
  );
}

function searchAgentByIdentity(
  chat: CHAT.ChatItem,
  tool: CHAT.Task
): CHAT.Task | undefined {
  for (const key of identityKeys(readTaskIdentity(tool))) {
    const hit = findBestAgentTask(chat, key);
    if (hit) {
      return hit;
    }
  }
  return undefined;
}

function resolveDetailAgent(tool: CHAT.Task, parentChat: CHAT.ChatItem): CHAT.Task {
  const fromParent = searchAgentByIdentity(parentChat, tool);
  if (fromParent?.children?.length) {
    return fromParent;
  }
  const hasProjected = (parentChat.tasks || []).some((group) => group?.length);
  const hasFacts = (parentChat.multiAgent?.tasks || []).some((group) => group?.length);
  if (hasFacts && !hasProjected) {
    const projected = buildConversationTaskData(parentChat, false).currentChat;
    const fromFacts = searchAgentByIdentity(projected, tool);
    if (fromFacts) {
      return fromFacts;
    }
  }
  return fromParent || tool;
}

function projectSubAgentChildren(children: CHAT.Task[], tool: CHAT.Task): CHAT.Task[] {
  return children.flatMap((child, index) => {
    if (isRenderedTask(child)) {
      return [child];
    }
    return processTaskForRender(
      child,
      `${tool.id || tool.messageId || "subagent"}:child:${index}:`
    );
  });
}

export function projectChat(
  facts: CHAT.ChatItem,
  scope?: string,
  deepThink = false
): CHAT.ChatItem {
  const projected = buildConversationTaskData(facts, deepThink).currentChat;
  if (!scope) {
    return projected;
  }
  const agent = findBestAgentTask(projected, scope);
  return agent ? chatItemFromSubAgent(agent, projected) : projected;
}

/**
 * 子 Agent 详情 = 主投影里该 Agent 的子树，再包一层 query/conclusion。
 */
export function chatItemFromSubAgent(
  tool: CHAT.Task,
  parentChat: CHAT.ChatItem
): CHAT.ChatItem {
  const detailAgent = resolveDetailAgent(tool, parentChat);
  const sub = resolveSubAgentDisplay(detailAgent);
  const running = sub.status === "running";
  const nested = Array.isArray(detailAgent.children) ? detailAgent.children : [];
  const nestedConclusion = resolveNestedConclusion(nested);
  const nestedFiles = collectTaskFiles(detailAgent);
  const parentFiles = getTaskFiles(detailAgent);
  const conclusionText =
    stripArtifactSection(
      nestedConclusion.text || stripArtifactSection(asText(sub.content))
    );
  const processNested = projectSubAgentChildren(
    nested.filter((child) => child.messageType !== "result"),
    detailAgent
  );
  // liveText 是真实过程增量。heartbeat 进度行不能冒充轨迹；
  // 只有还没有任何子步骤时才拿来占位，避免把「running · …」当成执行过程。
  const heartbeatText = Array.isArray(sub.progressLines)
    ? sub.progressLines.filter(Boolean).join("\n")
    : "";
  const liveText =
    asText(sub.liveText) ||
    (processNested.length === 0 ? heartbeatText : "");
  const liveTask = liveText
    ? buildLiveTextTask(detailAgent, liveText, running)
    : null;
  const children = mergeProcessChildren(processNested, liveTask);

  const container = {
    id: detailAgent.id || detailAgent.messageId || "subagent-container",
    messageId: detailAgent.messageId || detailAgent.id || "subagent-container",
    messageType: "task",
    messageTime: detailAgent.messageTime,
    requestId: parentChat.requestId,
    taskId: tool.taskId,
    finish: !running,
    isFinal: !running,
    resultMap: { isFinal: !running },
    children,
  } as CHAT.Task;

  return {
    sessionId: parentChat.sessionId,
    requestId: detailAgent.id || detailAgent.messageId || parentChat.requestId,
    query: sub.prompt,
    files: parentFiles,
    forceStop: false,
    loading: running,
    tasks: children.length ? [[container]] : [],
    timeline: [],
    multiAgent: { tasks: [] },
    generatedFiles: nestedFiles,
    conclusion: conclusionText || nestedFiles.length
      ? buildConclusionTask(
        detailAgent,
        conclusionText,
        nestedConclusion.task,
        nestedFiles
      )
      : undefined,
    startedAt: detailAgent.messageTime,
    finishedAt: running ? undefined : detailAgent.messageTime,
    tip: sub.errorMsg || undefined,
  } as CHAT.ChatItem;
}
