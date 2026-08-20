/**
 * Kimi-web 对齐的 Agent 运行时 UI 投影模型。
 * 事实层仍是 multiAgent.tasks；本文件只描述渲染契约。
 */

export type ToolStatus = "ok" | "running" | "error";

export type ToolMedia = {
  kind: "image" | "video" | "audio";
  url: string;
  path?: string;
  mimeType?: string;
  bytes?: number;
  dimensions?: string;
  fileId?: string;
};

export type ToolCall = {
  id: string;
  /** 规范化工具名，如 read / bash / Agent */
  name: string;
  /** JSON 字符串化的工具入参 */
  arg: string;
  status: ToolStatus;
  timing?: string;
  /** 展开区按行展示的输出 */
  output?: string[];
  media?: ToolMedia;
  defaultExpanded?: boolean;
  planPath?: string;
  /** 后台子 Agent 不进主时间线 Agent 卡的「Open」关联 */
  runInBackground?: boolean;
  /** 原始 task 引用 id，便于面板回查 */
  sourceTaskId?: string;
};

export type AgentPhase =
  | "queued"
  | "working"
  | "suspended"
  | "completed"
  | "failed";

export type AgentMember = {
  id: string;
  toolCallId?: string;
  name: string;
  subagentType?: string;
  phase: AgentPhase;
  status: "running" | "completed" | "failed" | "cancelled";
  prompt?: string;
  summary?: string;
  /** Calling… / heartbeat 进度行 */
  outputLines?: string[];
  /** 子 Agent 直播文本（assistant 增量） */
  text?: string;
  suspendedReason?: string;
  swarmIndex?: number;
  runInBackground?: boolean;
  elapsedMs?: number;
  totalToolUseCount?: number;
  totalDurationMs?: number;
  errorMsg?: string;
};

/** 助手轮次有序块：think → tool → text，不重排 */
export type TurnBlock =
  | { kind: "text"; text: string }
  | { kind: "thinking"; thinking: string }
  | { kind: "tool"; tool: ToolCall };

export type TurnRole = "user" | "assistant" | "compaction" | "cron";

export type ChatTurn = {
  id: string;
  role: TurnRole;
  no: number;
  text: string;
  thinking?: string;
  tools?: ToolCall[];
  blocks?: TurnBlock[];
  durationMs?: number;
  createdAt?: string;
};

export type TaskState = "run" | "done" | "fail";

/** 底部 Dock 后台任务（bash / 后台 subagent） */
export type DockTaskItem = {
  id: string;
  name: string;
  kind: "subagent" | "bash" | "task";
  state: TaskState;
  timing: string;
  meta?: string;
  output?: string[];
  runInBackground?: boolean;
  parentToolCallId?: string;
};

/**
 * SSE 契约补充（与后端 subagent_progress / 未来 tool_output 对齐）。
 * 仍挂在既有 resultMap 上，不另开第二套协议。
 */
export type SubAgentProgressKind = "heartbeat" | "text" | "line";

export type SubAgentProgressPayload = {
  kind?: SubAgentProgressKind;
  agentId?: string;
  agentType?: string;
  description?: string;
  status?: string;
  phase?: AgentPhase | string;
  elapsedMs?: number;
  /** kind=text 时追加到 live text */
  text?: string;
  /** kind=line 时追加一行进度 */
  line?: string;
  parentToolUseId?: string;
  subAgentId?: string;
  runInBackground?: boolean;
};
