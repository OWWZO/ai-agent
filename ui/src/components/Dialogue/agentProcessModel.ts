import { buildAction } from "@/utils/chat";
import {
  formatSubAgentDuration,
  isAgentDispatchTask,
  resolveSubAgentDisplay,
} from "@/utils/chat/subagent";
import { isTimelineToolActive } from "@/components/ChatView/streamState";

export type ProcessStepKind =
  | "thinking"
  | "assistant_reply"
  | "read"
  | "edit"
  | "terminal"
  | "search"
  | "browser"
  | "code"
  | "file"
  | "agent"
  | "interactive"
  | "user_message"
  | "tool"
  | "artifact";

export type ProcessStepRow = {
  id: string;
  kind: ProcessStepKind;
  title: string;
  detail?: string;
  expandable: boolean;
  active: boolean;
  completed: boolean;
  durationMs?: number;
  durationLabel?: string;
  tool: CHAT.Task;
  startedAtMs?: number;
};

export type ProcessStepGroup = {
  id: string;
  title: string;
  taskLabel?: string;
  digitalEmployee?: string;
  stepCount: number;
  active: boolean;
  completed: boolean;
  /** 可折叠；user_brief 分隔出的工作段为 true */
  collapsible: boolean;
  durationMs?: number;
  durationLabel?: string;
  steps: ProcessStepRow[];
  /** PlanSolve 容器原始 task，用于折叠标题 */
  container?: CHAT.Task;
};

export type ProcessThoughtBlock = {
  text: string;
  streaming: boolean;
  durationMs?: number;
  durationLabel?: string;
  versionLabel?: string;
  versionIndex: number;
  versionTotal: number;
};

/**
 * 时间线分段（对齐截图）：
 * 深度思考(折叠) → 助手过程回复(常显) → 执行了 N 步(折叠组) → …
 */
export type ProcessSegment =
  | { type: "group"; group: ProcessStepGroup }
  | { type: "thinking"; step: ProcessStepRow }
  | { type: "assistant_reply"; step: ProcessStepRow; text: string }
  | { type: "user_message"; step: ProcessStepRow }
  | { type: "final_reply"; step: ProcessStepRow; text: string };

export type AgentProcessModel = {
  hasProcess: boolean;
  loading: boolean;
  thought?: ProcessThoughtBlock;
  /** 短意图句：思考首行或 tip，不重复 thought 全文 */
  intentLine?: string;
  /** 有序分段（主渲染源） */
  segments: ProcessSegment[];
  /** 兼容：仅 collapsible 工作组 */
  groups: ProcessStepGroup[];
  /** 末轮无 tool 的思考，作为最终回复（不进折叠组） */
  finalReply?: { text: string; step: ProcessStepRow };
  totalStepCount: number;
  totalDurationMs?: number;
  totalDurationLabel?: string;
};

function asText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function resolveToolName(tool: CHAT.Task): string {
  return asText(
    tool.toolResult?.toolName ||
      (tool.resultMap as Record<string, unknown> | undefined)?.toolName
  ).toLowerCase();
}

/** SendUserMessage / Brief 主可见输出，永不进折叠组，并作为步骤组分界 */
export function isUserBriefTask(tool?: CHAT.Task): boolean {
  if (!tool) {
    return false;
  }
  if (tool.messageType === "user_brief") {
    return true;
  }
  const toolName = resolveToolName(tool);
  return (
    toolName === "sendusermessage" ||
    toolName === "brief" ||
    toolName === "send_user_message"
  );
}

export function resolveUserBriefText(tool: CHAT.Task): string {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  const toolAny = tool as unknown as Record<string, unknown>;
  return asText(
    nested.message || resultMap.message || toolAny.message || tool.toolThought
  );
}

/** 仅原生 CoT / plan 思考 → 深度思考（tool_thought 永不当思考） */
function isNativeReasoningTask(tool: CHAT.Task): boolean {
  return (
    tool.messageType === "llm_reasoning" || tool.messageType === "plan_thought"
  );
}

/**
 * 助手过程回复：有 tool 时的 content（tool_thought）。
 * 常显切分点；不是思考。
 */
function isAssistantProcessReplyTask(tool: CHAT.Task): boolean {
  return tool.messageType === "tool_thought";
}

/** 是否计入「执行了 N 个步骤」：思考/过程回复/user_brief 不计入 */
function countsAsExecutableStep(tool: CHAT.Task): boolean {
  if (
    isUserBriefTask(tool) ||
    isNativeReasoningTask(tool) ||
    isAssistantProcessReplyTask(tool)
  ) {
    return false;
  }
  return true;
}

export function resolveAssistantReplyText(tool: CHAT.Task): string {
  return asText(tool.toolThought);
}

export function parseMessageTimeMs(value?: string): number | undefined {
  if (!value) {
    return undefined;
  }
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) {
    // 兼容秒级时间戳
    return numeric < 1e12 ? numeric * 1000 : numeric;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function formatProcessDuration(ms?: number): string {
  if (ms == null || !Number.isFinite(ms) || ms < 0) {
    return "";
  }
  if (ms < 100) {
    return "0.1s";
  }
  if (ms < 1000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  if (ms < 60_000) {
    const seconds = ms / 1000;
    return Number.isInteger(seconds) || seconds >= 10
      ? `${Math.round(seconds)}s`
      : `${seconds.toFixed(1)}s`;
  }
  return formatSubAgentDuration(ms) || `${Math.round(ms / 1000)}s`;
}

function isStepCompleted(tool: CHAT.Task): boolean {
  return Boolean(tool.finish || tool.isFinal || tool.resultMap?.isFinal);
}

function resolveStepId(tool: CHAT.Task, index: number): string {
  return (
    tool.id ||
    tool.messageId ||
    resolveToolCallId(tool) ||
    `${tool.messageType || "step"}-${tool.taskId || index}`
  );
}

function resolveToolCallId(tool: CHAT.Task): string {
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested = (resultMap.resultMap || {}) as Record<string, unknown>;
  return asText(
    resultMap.toolCallId ||
      nested.toolCallId ||
      tool.toolResult?.toolName
  );
}

export function resolveProcessStepKind(tool: CHAT.Task): ProcessStepKind {
  const messageType = tool.messageType || "";
  if (messageType === "llm_reasoning" || messageType === "plan_thought") {
    return "thinking";
  }
  if (messageType === "tool_thought") {
    return "assistant_reply";
  }
  if (isUserBriefTask(tool)) {
    return "user_message";
  }
  if (
    messageType === "ask_user_question" ||
    messageType === "plan_approval" ||
    messageType === "session_tasks"
  ) {
    return "interactive";
  }
  if (isAgentDispatchTask(tool)) {
    return "agent";
  }
  if (messageType === "browser") {
    return "browser";
  }
  if (messageType === "deep_search" || messageType === "knowledge") {
    return "search";
  }
  if (messageType === "code" || messageType === "html") {
    return "code";
  }
  if (
    messageType === "file" ||
    messageType === "markdown" ||
    messageType === "ppt" ||
    messageType === "data_analysis" ||
    messageType === "ui_tree" ||
    messageType === "ui_patch"
  ) {
    return messageType === "file" ? "file" : "artifact";
  }

  const toolName = resolveToolName(tool);
  if (
    toolName.includes("read") ||
    toolName.includes("cat") ||
    toolName.includes("view") ||
    toolName.includes("open")
  ) {
    return "read";
  }
  if (
    toolName.includes("edit") ||
    toolName.includes("write") ||
    toolName.includes("create") ||
    toolName.includes("patch") ||
    toolName.includes("replace")
  ) {
    return "edit";
  }
  if (
    toolName.includes("bash") ||
    toolName.includes("shell") ||
    toolName.includes("terminal") ||
    toolName.includes("exec") ||
    toolName.includes("run")
  ) {
    return "terminal";
  }
  if (
    toolName.includes("search") ||
    toolName.includes("grep") ||
    toolName.includes("web") ||
    toolName.includes("crawl")
  ) {
    return "search";
  }
  if (messageType === "tool_call" || messageType === "tool_result") {
    return "tool";
  }
  return "tool";
}

function resolveStepTitle(tool: CHAT.Task, kind: ProcessStepKind): {
  title: string;
  detail?: string;
} {
  if (kind === "thinking") {
    return { title: "深度思考" };
  }

  if (kind === "assistant_reply") {
    const text = resolveAssistantReplyText(tool);
    return {
      title: "助手回复",
      detail: text ? (text.length > 48 ? `${text.slice(0, 45)}…` : text) : undefined,
    };
  }

  if (kind === "user_message") {
    const text = resolveUserBriefText(tool);
    return {
      title: "给用户的消息",
      detail: text ? (text.length > 48 ? `${text.slice(0, 45)}…` : text) : undefined,
    };
  }

  if (isAgentDispatchTask(tool)) {
    const sub = resolveSubAgentDisplay(tool);
    return {
      title: sub.status === "running" ? "派发子智能体" : "子智能体",
      detail: sub.description || sub.subagentType,
    };
  }

  const action = buildAction(tool);
  const title = asText(action.action) || "调用工具";
  const detail = asText(action.name) || asText(action.tool) || undefined;

  // Cursor 风格：动词 + 目标更短
  if (kind === "read" && detail) {
    return {
      title: `读取 ${detail}`,
      detail: undefined
    };
  }
  if (kind === "edit" && detail) {
    return {
      title: `编辑 ${detail}`,
      detail: undefined
    };
  }
  if (kind === "file" && detail) {
    const command = asText(tool.resultMap?.command);
    if (command) {
      return {
        title: `${command} ${detail}`,
        detail: undefined
      };
    }
    return {
      title: detail,
      detail: undefined
    };
  }

  return {
    title,
    detail
  };
}

function estimateDurations(
  steps: Array<{ tool: CHAT.Task; startedAtMs?: number }>,
  options: {
    loading: boolean;
    nowMs: number;
    finishedAtMs?: number;
  }
): Array<number | undefined> {
  const times = steps.map((step) => step.startedAtMs);
  return steps.map((step, index) => {
    const tool = step.tool;
    if (isAgentDispatchTask(tool)) {
      const sub = resolveSubAgentDisplay(tool);
      if (sub.totalDurationMs != null) {
        return sub.totalDurationMs;
      }
    }

    const start = times[index];
    if (start == null) {
      return undefined;
    }

    const nextStart = times.slice(index + 1).find((value) => value != null);
    if (nextStart != null && nextStart >= start) {
      return Math.max(nextStart - start, 0);
    }

    if (isTimelineToolActive(tool) || (options.loading && !isStepCompleted(tool))) {
      return Math.max(options.nowMs - start, 0);
    }

    if (options.finishedAtMs != null && options.finishedAtMs >= start) {
      return Math.max(options.finishedAtMs - start, 0);
    }

    // 无可靠结束时间时，给已完成步骤一个保守下限，避免空白
    if (isStepCompleted(tool)) {
      return 400;
    }

    return undefined;
  });
}

function flattenGroupChildren(container: CHAT.Task): CHAT.Task[] {
  return Array.isArray(container.children) ? container.children : [];
}

function buildStepRows(
  tools: CHAT.Task[],
  options: {
    loading: boolean;
    nowMs: number;
    finishedAtMs?: number;
  }
): ProcessStepRow[] {
  const prepared = tools.map((tool, index) => ({
    tool,
    startedAtMs: parseMessageTimeMs(tool.messageTime),
    index,
  }));
  const durations = estimateDurations(prepared, options);

  return prepared.map((item, index) => {
    const kind = resolveProcessStepKind(item.tool);
    const { title, detail } = resolveStepTitle(item.tool, kind);
    // 整轮结束后禁止残留 active，否则深度思考/步骤会一直 shimmer
    const active = options.loading && isTimelineToolActive(item.tool);
    const completed =
      !active && (isStepCompleted(item.tool) || !options.loading);
    const durationMs = durations[index];
    const expandable =
      kind === "thinking" ||
      kind === "assistant_reply" ||
      kind === "interactive" ||
      kind === "user_message" ||
      kind === "agent" ||
      kind === "artifact" ||
      item.tool.messageType === "deep_search" ||
      item.tool.messageType === "browser" ||
      Boolean(item.tool.children?.length);

    return {
      id: resolveStepId(item.tool, index),
      kind,
      title,
      detail,
      expandable,
      active,
      completed,
      durationMs,
      durationLabel: formatProcessDuration(durationMs),
      tool: item.tool,
      startedAtMs: item.startedAtMs,
    };
  });
}

function resolveDigitalEmployee(container: CHAT.Task): string | undefined {
  return container.children?.find((child) => child.digitalEmployee)?.digitalEmployee;
}

function buildGroupTitle(
  container: CHAT.Task | undefined,
  stepCount: number,
  isPlanSolve: boolean,
  active: boolean
): string {
  if (isPlanSolve && container?.task?.trim()) {
    return container.task.trim();
  }
  if (active) {
    return stepCount > 0 ? `正在执行 · ${stepCount} 个步骤` : "正在执行";
  }
  return `执行了 ${stepCount} 个步骤`;
}

/**
 * 分段规则（对齐产品截图）：
 * - 非空 assistant_reply → 先吐出缓冲中的「深度思考」为外层块，再冲刷工具组，再常显回复（切开）
 * - 空 assistant_reply → 不切开、不展示
 * - thinking 无后续非空回复时 → 融入「执行了 N 步」组内作为一步
 * - user_brief → 常显并切开
 * - 末尾仅非空过程文且无 tool → final_reply
 */
export function segmentProcessSteps(
  steps: ProcessStepRow[],
  options: {
    loading: boolean;
    isPlanSolve: boolean;
    container?: CHAT.Task;
    groupIndexBase?: number;
  }
): ProcessSegment[] {
  const segments: ProcessSegment[] = [];
  /** 缓冲：thinking + tools；有非空助手回复时 thinking 提出外层，否则 thinking 进组 */
  let buffer: ProcessStepRow[] = [];
  let groupSerial = options.groupIndexBase ?? 0;

  const isThinkingStep = (step: ProcessStepRow) =>
    step.kind === "thinking" || isNativeReasoningTask(step.tool);

  const emitStandaloneThinking = (items: ProcessStepRow[]) => {
    for (const step of items) {
      if (isThinkingStep(step)) {
        segments.push({ type: "thinking", step });
      }
    }
  };

  const flushWorkGroup = (forceActive?: boolean) => {
    if (!buffer.length) {
      return;
    }
    const executables = buffer.filter((step) => countsAsExecutableStep(step.tool));
    const thinkings = buffer.filter((step) => isThinkingStep(step));

    // 无真实工具：仅深度思考 → 外层折叠块（不当空组）
    if (!executables.length) {
      emitStandaloneThinking(thinkings);
      buffer = [];
      return;
    }

    // 有工具：thinking 与 tools 按原序进组（助手为空时深度思考是步骤之一）
    const ordered = buffer.filter(
      (step) => countsAsExecutableStep(step.tool) || isThinkingStep(step)
    );
    const displayCount = executables.length;
    // 仅按组内步骤是否仍在执行判定 active，避免整轮 loading 期间步骤组无法折叠
    const active =
      Boolean(forceActive) || ordered.some((step) => step.active);
    const completed = ordered.every((step) => step.completed) && !active;
    const durationMs = sumDurations(ordered.map((step) => step.durationMs));
    groupSerial += 1;
    segments.push({
      type: "group",
      group: {
        id:
          options.container?.id ||
          options.container?.messageId ||
          `work-group-${groupSerial}`,
        title: buildGroupTitle(
          options.container,
          displayCount,
          options.isPlanSolve,
          active
        ),
        taskLabel: options.container?.task,
        digitalEmployee: options.container
          ? resolveDigitalEmployee(options.container)
          : undefined,
        stepCount: displayCount,
        active,
        completed,
        collapsible: true,
        durationMs,
        durationLabel: formatProcessDuration(durationMs),
        steps: ordered,
        container: options.container,
      },
    });
    buffer = [];
  };

  /**
   * 非空助手回复前的切开：
   * 1) 缓冲里先出现的 thinking 提出为外层「深度思考」
   * 2) 剩余 tools（及夹在中间的 thinking）冲成步骤组
   */
  const flushBeforeAssistantReply = () => {
    if (!buffer.length) {
      return;
    }
    // 前缀连续 thinking → 外层
    const prefixThinking: ProcessStepRow[] = [];
    let i = 0;
    while (i < buffer.length && isThinkingStep(buffer[i])) {
      prefixThinking.push(buffer[i]);
      i += 1;
    }
    const rest = buffer.slice(i);
    buffer = [];
    emitStandaloneThinking(prefixThinking);
    buffer = rest;
    flushWorkGroup(false);
  };

  for (const step of steps) {
    // 1) 深度思考：先进缓冲（是否外提取决于后续有无非空助手回复）
    if (isThinkingStep(step)) {
      buffer.push(step);
      continue;
    }

    // 2) 助手过程回复
    if (step.kind === "assistant_reply" || isAssistantProcessReplyTask(step.tool)) {
      const text = resolveAssistantReplyText(step.tool);
      // 空回复：不切开、不展示
      if (!text) {
        continue;
      }
      flushBeforeAssistantReply();
      segments.push({ type: "assistant_reply", step, text });
      continue;
    }

    // 3) user_brief：切开并独立展示
    if (step.kind === "user_message" || isUserBriefTask(step.tool)) {
      flushWorkGroup(false);
      segments.push({ type: "user_message", step });
      continue;
    }

    // 4) 真实工具
    buffer.push(step);
  }

  // 末组是否 active 由组内步骤状态决定，不因整轮 loading 强制保持展开
  flushWorkGroup(false);

  // 终答只走 chat.conclusion / result，不再把过程文提升为 final_reply（避免与结论重复）
  return segments;
}

function extractIntentLine(thoughtText?: string, tip?: string): string | undefined {
  const tipText = asText(tip);
  if (tipText && !/正在|加载|排队|请稍候|思考中/i.test(tipText)) {
    // tip 往往是系统状态，不当作意图句
  }
  const thought = asText(thoughtText);
  if (!thought) {
    return undefined;
  }
  const firstLine = thought
    .split(/\n/)
    .map((line) => line.trim())
    .find((line) => line.length > 0);
  if (!firstLine) {
    return undefined;
  }
  // 过长时截断为意图句
  if (firstLine.length <= 80) {
    return firstLine;
  }
  return `${firstLine.slice(0, 77)}…`;
}

export type DeriveAgentProcessModelInput = {
  chat: CHAT.ChatItem;
  isPlanSolve: boolean;
  thoughtText?: string;
  thoughtStreaming?: boolean;
  thoughtVersionLabel?: string;
  thoughtVersionIndex?: number;
  thoughtVersionTotal?: number;
  nowMs?: number;
};

/**
 * 把现有 chat.tasks / thought 投影为 Cursor 风格过程叙事模型。
 * 不改 SSE / ledger，只做展示层派生。
 *
 * 规则：
 * 1. SendUserMessage(user_brief) 永不折叠，并切开前后「执行了 N 步」
 * 2. 终答只走 chat.conclusion；过程文/CoT 不提升为 final_reply
 */
export function deriveAgentProcessModel(
  input: DeriveAgentProcessModelInput
): AgentProcessModel {
  const {
    chat,
    isPlanSolve,
    thoughtText = "",
    thoughtStreaming = false,
    thoughtVersionLabel,
    thoughtVersionIndex = 0,
    thoughtVersionTotal = 0,
    nowMs = Date.now(),
  } = input;

  const loading = Boolean(chat.loading);
  const finishedAtMs =
    parseMessageTimeMs(chat.finishedAt) ||
    (loading ? undefined : nowMs);
  const startedAtMs = parseMessageTimeMs(chat.startedAt);

  const segments: ProcessSegment[] = [];
  let groupSerial = 0;
  const taskGroups = chat.tasks || [];

  // ReAct / 普通任务：跨 container 合并 children 再切分，避免历史回放「一工具一组」时
  // 永远落成 stepCount=1 的已完成组而被时间线压成平铺（折叠「执行了 N 步」消失）。
  // PlanSolve 仍按 plan task 容器边界分段，保留步骤标题。
  type ContainerSlice = {
    children: CHAT.Task[];
    container?: CHAT.Task;
    groupIndex: number;
    containerIndex: number;
    isLastContainer: boolean;
  };
  const slices: ContainerSlice[] = [];

  if (!isPlanSolve) {
    const merged: CHAT.Task[] = [];
    let lastMeta: Omit<ContainerSlice, "children"> | undefined;
    taskGroups.forEach((group, groupIndex) => {
      (group || []).forEach((container, containerIndex) => {
        const children = flattenGroupChildren(container);
        if (!children.length) {
          return;
        }
        merged.push(...children);
        lastMeta = {
          container,
          groupIndex,
          containerIndex,
          isLastContainer:
            groupIndex === taskGroups.length - 1 &&
            containerIndex === (group || []).length - 1,
        };
      });
    });
    if (merged.length && lastMeta) {
      slices.push({ children: merged, ...lastMeta });
    }
  } else {
    taskGroups.forEach((group, groupIndex) => {
      (group || []).forEach((container, containerIndex) => {
        const children = flattenGroupChildren(container);
        if (!children.length) {
          return;
        }
        slices.push({
          children,
          container,
          groupIndex,
          containerIndex,
          isLastContainer:
            groupIndex === taskGroups.length - 1 &&
            containerIndex === (group || []).length - 1,
        });
      });
    });
  }

  for (const slice of slices) {
    const steps = buildStepRows(slice.children, {
      loading,
      nowMs,
      finishedAtMs,
    });
    if (!steps.length) {
      continue;
    }

    const sliced = segmentProcessSteps(steps, {
      loading: loading && slice.isLastContainer,
      isPlanSolve,
      container: slice.container,
      groupIndexBase: groupSerial,
    });
    for (const segment of sliced) {
      if (segment.type === "group") {
        groupSerial += 1;
        segment.group.id =
          segment.group.id ||
          slice.container?.id ||
          slice.container?.messageId ||
          slice.container?.taskId ||
          `group-${slice.groupIndex}-${slice.containerIndex}-${groupSerial}`;
      }
      segments.push(segment);
    }
  }

  const groups = segments
    .filter((segment): segment is Extract<ProcessSegment, { type: "group" }> =>
      segment.type === "group"
    )
    .map((segment) => segment.group);

  const finalReplySegment = [...segments]
    .reverse()
    .find((segment) => segment.type === "final_reply") as
    | Extract<ProcessSegment, { type: "final_reply" }>
    | undefined;

  const totalStepCount = groups.reduce((sum, group) => sum + group.stepCount, 0);

  const thought = asText(thoughtText)
    ? {
      text: thoughtText,
      streaming: Boolean(thoughtStreaming),
      durationMs: undefined as number | undefined,
      durationLabel: undefined as string | undefined,
      versionLabel: thoughtVersionLabel,
      versionIndex: thoughtVersionIndex,
      versionTotal: thoughtVersionTotal,
    }
    : undefined;

  // 思考块耗时：优先用 startedAt → 首个工具 / finishedAt
  if (thought) {
    const firstStepStart = segments
      .flatMap((segment) => {
        if (segment.type === "group") {
          return segment.group.steps;
        }
        if (
          segment.type === "user_message" ||
          segment.type === "final_reply" ||
          segment.type === "thinking" ||
          segment.type === "assistant_reply"
        ) {
          return [segment.step];
        }
        return [];
      })
      .map((step) => step.startedAtMs)
      .find((value) => value != null);
    if (thought.streaming && startedAtMs != null) {
      thought.durationMs = Math.max(nowMs - startedAtMs, 0);
    } else if (startedAtMs != null && firstStepStart != null && firstStepStart >= startedAtMs) {
      thought.durationMs = firstStepStart - startedAtMs;
    } else if (startedAtMs != null && finishedAtMs != null) {
      thought.durationMs = Math.max(finishedAtMs - startedAtMs, 0);
    }
    thought.durationLabel = formatProcessDuration(thought.durationMs);
  }

  const totalDurationMs =
    startedAtMs != null && (finishedAtMs != null || loading)
      ? Math.max((finishedAtMs ?? nowMs) - startedAtMs, 0)
      : sumDurations([
        thought?.durationMs,
        ...groups.map((group) => group.durationMs),
      ]);

  const intentLine = extractIntentLine(thoughtText, chat.tip);

  return {
    hasProcess: Boolean(thought || segments.length),
    loading,
    thought,
    intentLine,
    segments,
    groups,
    finalReply: finalReplySegment
      ? {
        text: finalReplySegment.text,
        step: finalReplySegment.step,
      }
      : undefined,
    totalStepCount,
    totalDurationMs,
    totalDurationLabel: formatProcessDuration(totalDurationMs),
  };
}

function sumDurations(values: Array<number | undefined>): number | undefined {
  const valid = values.filter(
    (value): value is number => value != null && Number.isFinite(value) && value >= 0
  );
  if (!valid.length) {
    return undefined;
  }
  return valid.reduce((sum, value) => sum + value, 0);
}
