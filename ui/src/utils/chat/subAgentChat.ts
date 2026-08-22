import { resolveSubAgentDisplay } from "./subagent";

function asText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
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

function buildConclusionTask(tool: CHAT.Task, content: string): CHAT.Task {
  const id = `${tool.id || tool.messageId || "subagent"}:conclusion`;
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
    resultMap: {
      isFinal: true,
      taskSummary: content,
      result: content,
    },
  } as CHAT.Task;
}

function resolveNestedConclusion(children: CHAT.Task[]): string {
  for (let i = children.length - 1; i >= 0; i -= 1) {
    const child = children[i];
    if (child?.messageType !== "result") {
      continue;
    }
    const resultMap = (child.resultMap || {}) as Record<string, unknown>;
    const text =
      asText(child.result) ||
      asText(resultMap.result) ||
      asText(resultMap.taskSummary);
    if (text) {
      return text;
    }
  }
  return "";
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

/**
 * 子 Agent 任务原地突变（children / liveText / status），对象引用不变。
 * 详情面板用这个指纹打破 memo，才能边跑边刷轨迹。
 */
export function subAgentLiveRevision(tool: CHAT.Task): string {
  const children = Array.isArray(tool.children) ? tool.children : [];
  const childKey = children
    .map((child) => {
      const map = (child.resultMap || {}) as Record<string, unknown>;
      return [
        child.messageId || child.id || "",
        child.messageType || "",
        child.finish ? "1" : "0",
        String(map.status || ""),
        String(child.toolThought || "").length,
        String(child.result || "").length,
        Array.isArray(child.children) ? child.children.length : 0,
      ].join(":");
    })
    .join("|");
  const map = (tool.resultMap || {}) as Record<string, unknown>;
  return [
    children.length,
    childKey,
    String(map.status || ""),
    String(map.subAgentLiveText || "").length,
    Array.isArray(map.subAgentProgressLines)
      ? map.subAgentProgressLines.length
      : 0,
  ].join("#");
}

/**
 * 把子 Agent 任务投影成主对话同款 ChatItem，供时间线 / 终答复用。
 * 不改 SSE 事实，只做展示层派生。
 */
export function chatItemFromSubAgent(
  tool: CHAT.Task,
  parentChat: CHAT.ChatItem
): CHAT.ChatItem {
  const sub = resolveSubAgentDisplay(tool);
  const running = sub.status === "running";
  const nested = Array.isArray(tool.children) ? tool.children : [];
  const conclusionText = resolveNestedConclusion(nested) || asText(sub.content);
  const processNested = nested.filter((child) => child.messageType !== "result");
  // liveText 是真实过程增量。heartbeat 进度行不能冒充轨迹；
  // 只有还没有任何子步骤时才拿来占位，避免把「running · …」当成执行过程。
  const heartbeatText = Array.isArray(sub.progressLines)
    ? sub.progressLines.filter(Boolean).join("\n")
    : "";
  const liveText =
    asText(sub.liveText) ||
    (processNested.length === 0 ? heartbeatText : "");
  const liveTask = liveText
    ? buildLiveTextTask(tool, liveText, running)
    : null;
  const children = mergeProcessChildren(processNested, liveTask);

  const container = {
    id: tool.id || tool.messageId || "subagent-container",
    messageId: tool.messageId || tool.id || "subagent-container",
    messageType: "task",
    messageTime: tool.messageTime,
    requestId: parentChat.requestId,
    taskId: tool.taskId,
    finish: !running,
    isFinal: !running,
    resultMap: { isFinal: !running },
    children,
  } as CHAT.Task;

  return {
    sessionId: parentChat.sessionId,
    requestId: tool.id || tool.messageId || parentChat.requestId,
    query: sub.prompt,
    files: [],
    forceStop: false,
    loading: running,
    tasks: children.length ? [[container]] : [],
    timeline: [],
    multiAgent: { tasks: [] },
    conclusion: conclusionText
      ? buildConclusionTask(tool, conclusionText)
      : undefined,
    startedAt: tool.messageTime,
    finishedAt: running ? undefined : tool.messageTime,
    tip: sub.errorMsg || undefined,
  } as CHAT.ChatItem;
}
