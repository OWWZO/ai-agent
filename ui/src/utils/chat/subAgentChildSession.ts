import type {
  ConversationHistoryPage,
  ConversationReplayFrame,
  ConversationRunReplay,
  ConversationRunSummary,
} from "@/services/agentConversation";
import {
  buildConversationTaskData,
  buildTaskFromEventData,
  combineData,
  normalizeEventData,
} from "@/utils/chat";
import { identityKeys, readTaskIdentity } from "./taskIdentity";

/**
 * 真实子会话（child session）→ 可渲染 ChatItem 的投影层。
 *
 * <p>设计约束（来自 subagent-child-session-plan.md Phase 4）：</p>
 * <ul>
 *   <li>复用现有 {@link combineData} / {@link buildConversationTaskData} 渲染链路，
 *       不新写 ChildSessionPane —— 子会话与主会话同构。</li>
 *   <li>子会话没有父 conclusion 可比，父链那套「与父结论去重」的判据在这里全部失效，
 *       因此子会话里的 result / task_summary 一律视为本会话结论。</li>
 *   <li>历史回放与实时流互斥：回放来自账本（终态、权威），实时流只在没有回放时使用，
 *       避免同一段 agent_stream 增量被追加两次（见 §5.4 heartbeat 不能冒充轨迹）。</li>
 * </ul>
 */

/** 会话控制帧不携带轨迹事件，直接丢弃（与 useConversationStream 的控制包集合同源）。 */
const SESSION_CONTROL_PACKAGES = new Set([
  "heartbeat",
  "follow_idle",
  "follow_pending",
]);

/** 终态 run：没有活投影，SSE 会立刻 follow_idle，必须走 run replay 恢复。 */
const TERMINAL_STATUSES = new Set([
  "SUCCESS",
  "FAILED",
  "STOPPED",
  "TIMEOUT",
  "WAITING_INPUT",
]);

const CONCLUSION_MESSAGE_TYPES = new Set(["result", "task_summary"]);

export type ChildSessionRun =
  | ConversationRunSummary
  | ConversationRunReplay
  | undefined;

export type BuildChildSessionChatInput = {
  childSessionId: string;
  /** 会话级历史（runs 摘要），决定 query / status / requestId */
  history?: ConversationHistoryPage | null;
  /** 单 run 回放（账本事件），终态 run 的唯一轨迹来源 */
  replay?: ConversationRunReplay | null;
  /** 子会话 SSE 实时帧（MESSAGE.Answer 信封） */
  frames?: unknown[];
  /** 父卡兜底文案：history 还没回来时填 query，避免头部空白 */
  fallbackQuery?: string;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function asText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeRunStatus(status?: string | null): string {
  return String(status || "").trim().toUpperCase() || "RUNNING";
}

function isRunReplay(
  run: ConversationRunSummary | ConversationRunReplay
): run is ConversationRunReplay {
  return "replayFrames" in run;
}

export function isTerminalRunStatus(status?: string | null): boolean {
  return TERMINAL_STATUSES.has(normalizeRunStatus(status));
}

function getRunQuery(run: ChildSessionRun): string {
  if (!run) {
    return "";
  }
  return isRunReplay(run)
    ? asText(run.queryText)
    : asText(run.queryPreview);
}

function getRunSummary(run: ChildSessionRun): string {
  if (!run) {
    return "";
  }
  return isRunReplay(run)
    ? asText(run.finalSummaryText)
    : asText(run.finalSummaryText) || asText(run.finalSummaryPreview);
}

/**
 * 取子会话当前关注的 run：优先 `latestRequestId`，否则取最后一条。
 * 子会话通常只有一条 run（一次派发），多轮 SendMessage 复用同一子会话时按最新一轮。
 */
export function pickChildSessionRun(
  history?: ConversationHistoryPage | null
): ChildSessionRun {
  const runs = history?.runs;
  if (!Array.isArray(runs) || runs.length === 0) {
    return undefined;
  }
  const latestRequestId = asText(history?.latestRequestId);
  const hit = latestRequestId
    ? runs.find((run) => run?.requestId === latestRequestId)
    : undefined;
  return hit || runs[runs.length - 1];
}

/**
 * 终态 run 没有活投影，必须靠 replay 恢复轨迹；运行中 run 的 SSE 自带全量回放。
 * 运行中却已 follow_idle 的漏网情形由订阅侧在收流结束后补拉，不在这里判定。
 */
export function shouldFetchChildSessionReplay(run: ChildSessionRun): boolean {
  if (!run || isRunReplay(run)) {
    return false;
  }
  return isTerminalRunStatus(run.status);
}

/**
 * 从子会话帧里取出 eventData。
 * 兼容两种信封：SSE 的 `MESSAGE.Answer`（resultMap.eventData）与账本回放帧（同构）。
 */
export function readChildSessionEventData(
  frame: unknown
): MESSAGE.EventData | undefined {
  if (!isRecord(frame)) {
    return undefined;
  }
  if (SESSION_CONTROL_PACKAGES.has(asText(frame.packageType))) {
    return undefined;
  }
  const resultMap = isRecord(frame.resultMap) ? frame.resultMap : undefined;
  // 优先信封里的 eventData；裸 eventData（自身带 messageType）也直接收下。
  const raw =
    resultMap?.eventData ?? (asText(frame.messageType) ? frame : frame.eventData);
  if (!raw) {
    return undefined;
  }
  return normalizeEventData(raw);
}

/** 父链嵌套标签：把帧挂到「派发它的那张 Agent 卡」下。两种别名都来自历史链路。 */
const PARENT_ID_KEYS = ["parentToolUseId", "parentToolCallId"] as const;

function readFrameParentId(eventData: MESSAGE.EventData): string {
  const source = eventData as unknown as Record<string, unknown>;
  const resultMap = isRecord(source.resultMap) ? source.resultMap : undefined;
  const nested = isRecord(resultMap?.resultMap) ? resultMap.resultMap : undefined;
  const toolResult = isRecord(source.toolResult) ? source.toolResult : undefined;
  return asText(
    resultMap?.parentToolUseId ??
      resultMap?.parentToolCallId ??
      nested?.parentToolUseId ??
      nested?.parentToolCallId ??
      source.parentToolUseId ??
      toolResult?.parentToolUseId
  );
}

function removeParentIdKeys(target: Record<string, unknown>): void {
  for (const key of PARENT_ID_KEYS) {
    delete target[key];
  }
}

/**
 * 本子会话自身出现过的全部身份键（toolCallId / streamToolKey / messageId / id）。
 * 与派生层的嵌套判据同源，保证「父 id 能不能在本会话里找到卡」的结论一致。
 */
function collectChildSessionOwnKeys(events: ChildSessionFrameEvent[]): Set<string> {
  const keys = new Set<string>();
  for (const event of events) {
    for (const key of identityKeys(
      readTaskIdentity(event.eventData as unknown as Record<string, unknown>)
    )) {
      keys.add(key);
    }
  }
  return keys;
}

/**
 * 剥离指向本会话之外的父链标签，让子 Agent 的事件回到根时间线。
 * <p>
 * 账本回放会给子会话自己的 run 里每条帧都打上派发者的 tool call id
 * （后端 {@code AbstractToolInvocationProjector#appendSubAgentNestingTags} 读 ledger 的
 * {@code parent_tool_call_id}），但那张 Agent 卡属于**父会话**，在本子会话的时间线里并不存在。
 * 派生层遇到「有父 id 但父卡找不到」的子事件会把它塞进 pending 桶，父卡始终不出现
 * → 工具卡整批从时间线消失，只剩终答。实时流没有这个标签，所以运行中反而正常。
 * <p>
 * 判据是「父 id 能否在本会话自身的帧里找到对应卡」：找不到才剥，因此子 Agent 自己
 * 再派子 Agent 的嵌套结构照旧保留。
 */
function detachExternalParentTags(
  eventData: MESSAGE.EventData,
  ownKeys: Set<string>
): MESSAGE.EventData {
  const parentId = readFrameParentId(eventData);
  if (!parentId || ownKeys.has(parentId)) {
    return eventData;
  }
  const source = eventData as unknown as Record<string, unknown>;
  const next: Record<string, unknown> = { ...source };
  removeParentIdKeys(next);

  const resultMap = isRecord(source.resultMap) ? { ...source.resultMap } : undefined;
  if (resultMap) {
    removeParentIdKeys(resultMap);
    const nested = isRecord(resultMap.resultMap) ? { ...resultMap.resultMap } : undefined;
    if (nested) {
      removeParentIdKeys(nested);
      resultMap.resultMap = nested;
    }
    next.resultMap = resultMap;
  }

  const toolResult = isRecord(source.toolResult) ? { ...source.toolResult } : undefined;
  if (toolResult) {
    removeParentIdKeys(toolResult);
    next.toolResult = toolResult;
  }

  return next as unknown as MESSAGE.EventData;
}

function createChildSessionShell(
  childSessionId: string,
  run: ChildSessionRun,
  fallbackQuery?: string
): CHAT.ChatItem {
  const status = normalizeRunStatus(run?.status);
  return {
    sessionId: childSessionId,
    requestId: run?.requestId,
    query: getRunQuery(run) || asText(fallbackQuery),
    files: [],
    responseType: "txt",
    loading: status === "RUNNING",
    forceStop: status === "STOPPED",
    tasks: [],
    thought: "",
    response: "",
    taskStatus: 0,
    tip: "",
    multiAgent: { tasks: [] },
    timeline: [],
    startedAt: run?.startedAt,
    finishedAt: run?.finishedAt,
    replayLoaded: false,
    replayAvailable: run ? (isRunReplay(run) || run.hasReplay) : false,
    metrics: { status },
  } as CHAT.ChatItem;
}

/**
 * 子会话的 result / task_summary 一律是本会话结论。
 * 父链用 `parentToolUseId` 判「不是主会话终态」，子会话没有父终答可比，必须无条件收下。
 */
function applyChildSessionEvent(
  chat: CHAT.ChatItem,
  eventData: MESSAGE.EventData,
  remember: (task: CHAT.Task) => void
) {
  combineData(eventData, chat);
  const innerType =
    asText(eventData.resultMap?.messageType) || asText(eventData.messageType);
  if (CONCLUSION_MESSAGE_TYPES.has(innerType)) {
    const conclusion = buildTaskFromEventData(eventData) as unknown as CHAT.Task;
    chat.conclusion = conclusion;
    remember(conclusion);
  }
}

function applyFallbackConclusion(
  chat: CHAT.ChatItem,
  run: ChildSessionRun,
  remember: (task: CHAT.Task) => void
) {
  if (!run || chat.conclusion) {
    return;
  }
  const summaryText = getRunSummary(run);
  if (!summaryText) {
    return;
  }
  const messageId = `${run.requestId || "child"}-summary`;
  const conclusion = {
    id: messageId,
    messageId,
    messageType: "result",
    result: summaryText,
    finish: true,
    isFinal: true,
    resultMap: {
      result: summaryText,
      taskSummary: summaryText,
      isFinal: true,
      fileList: [],
    } as unknown as MESSAGE.ResultMap,
  } as unknown as CHAT.Task;
  chat.conclusion = conclusion;
  remember(conclusion);
}

function applyRunStatus(
  chat: CHAT.ChatItem,
  run: ChildSessionRun,
  liveSettled?: { status: string }
) {
  if (liveSettled) {
    // 实时流已收口：以流的终态为准，不要被滞后的账本 RUNNING 拉回 loading。
    chat.metrics = {
      ...(chat.metrics || {}),
      status: liveSettled.status
    };
    chat.loading = false;
  } else {
    const status = normalizeRunStatus(run?.status);
    chat.metrics = {
      ...(chat.metrics || {}),
      status
    };
    // RUNNING 必须覆盖事件里的暂态字段：刷新后不要把仍在跑的子 Agent 显示成已完成。
    chat.loading = status === "RUNNING";
  }
  if (typeof run?.durationMs === "number" && Number.isFinite(run.durationMs)) {
    chat.runDurationMs = run.durationMs;
    chat.runTimingSource = "ledger";
  }
}

/** 一帧可渲染的子会话事件；实时路径额外带原始帧用于判定收口。 */
type ChildSessionFrameEvent = {
  eventData: MESSAGE.EventData;
  frame?: Record<string, unknown>;
};

function readReplayFrameEvents(
  replayFrames: ConversationReplayFrame[]
): ChildSessionFrameEvent[] {
  const events: ChildSessionFrameEvent[] = [];
  for (const frame of replayFrames) {
    const eventData = readChildSessionEventData(frame);
    if (eventData) {
      events.push({ eventData });
    }
  }
  return events;
}

/** 实时帧按 eventSeq 去重；投影自带全量回放，重复序号直接丢弃。 */
function readLiveFrameEvents(frames?: unknown[]): ChildSessionFrameEvent[] {
  const events: ChildSessionFrameEvent[] = [];
  let lastSeq = 0;
  for (const frame of frames || []) {
    if (!isRecord(frame)) {
      continue;
    }
    const seq = Number(frame.eventSeq || 0);
    if (seq > 0) {
      if (seq <= lastSeq) {
        continue;
      }
      lastSeq = seq;
    }
    const eventData = readChildSessionEventData(frame);
    if (eventData) {
      events.push({
        eventData,
        frame
      });
    }
  }
  return events;
}

/**
 * 把子会话的历史回放 / 实时帧合成一个可渲染的 ChatItem。
 * 纯函数：不触碰网络与 React 状态，便于单测直接驱动帧序列。
 */
export function buildChildSessionChat(
  input: BuildChildSessionChatInput
): CHAT.ChatItem {
  const { childSessionId, history, replay, frames, fallbackQuery } = input;
  const run = replay || pickChildSessionRun(history);
  const chat = createChildSessionShell(childSessionId, run, fallbackQuery);

  // 派生层 handleTaskData 会按「result 且无 parentToolUseId」重建 conclusion。
  // 子会话里这条判据会把带父标记的子终答丢掉，所以显式结论必须在派生之后覆盖回来。
  let explicitConclusion: CHAT.Task | undefined;
  const remember = (task: CHAT.Task) => {
    explicitConclusion = task;
  };

  const replayFrames: ConversationReplayFrame[] =
    replay && Array.isArray(replay.replayFrames) ? replay.replayFrames : [];

  // 回放为空才吃实时帧：两者同源会重复追加同一段流式增量。
  const frameEvents =
    replayFrames.length > 0
      ? readReplayFrameEvents(replayFrames)
      : readLiveFrameEvents(frames);

  // 先收集本会话自身的身份键，再逐帧剥离指向会话之外的父链标签。
  // 顺序很重要：剥离判据要看全量帧，不能边读边判。
  const ownKeys = collectChildSessionOwnKeys(frameEvents);

  let liveSettled: { status: string } | undefined;
  for (const { eventData, frame } of frameEvents) {
    applyChildSessionEvent(chat, detachExternalParentTags(eventData, ownKeys), remember);
    if (frame?.finished === true) {
      liveSettled = { status: frame.errorMsg ? "FAILED" : "SUCCESS" };
    }
  }

  applyFallbackConclusion(chat, run, remember);
  applyRunStatus(chat, run, liveSettled);

  const derived = buildConversationTaskData(chat, false).currentChat;
  if (explicitConclusion) {
    derived.conclusion = explicitConclusion;
  }
  return derived;
}
