import { normalizeToolName } from "./toolMeta";
import {
  fallbackStreamRawPreview,
  pickJsonStringField,
  pickJsonStringFieldAny,
} from "@/utils/jsonStreamField";

const PATH_KEYS = [
  "path",
  "file_path",
  "filePath",
  "filename",
  "fileName",
  "file_name",
  "targetPath",
  "outputFileName",
  "displayName",
];

const BODY_KEYS_BY_TOOL: Record<string, string[]> = {
  write: ["content", "contents", "text", "body", "code", "html", "markdown"],
  edit: ["new_string", "newString", "replacement", "content", "text"],
  multi_edit: ["new_string", "newString", "replacement", "content", "text"],
  bash: ["command", "cmd", "script", "code"],
  code_execute: ["code", "script", "command", "source"],
  code_execution: ["code", "script", "command", "source"],
  code_interpreter: ["code", "script", "command", "source"],
  search: ["query", "q", "pattern", "regex"],
  grep: ["pattern", "query", "regex", "q"],
  web_fetch: ["url", "uri"],
  task: ["prompt", "description", "title", "query"],
  todo: ["description", "title", "prompt"],
};

const DEFAULT_BODY_KEYS = [
  "content",
  "code",
  "text",
  "body",
  "command",
  "new_string",
  "newString",
  "html",
  "markdown",
  "prompt",
  "query",
  "script",
  "source",
];

export type ToolStreamPreviewModel = {
  /** 工具卡标题行短摘要（path / 命令前缀等） */
  header: string;
  /** 展开区主预览正文（content/code 等） */
  body: string;
  /** 是否已从半截 JSON 抽到结构化正文 */
  hasStructuredBody: boolean;
};

/**
 * 参数是否仍在流式生成（逐字光标）。
 * 与执行 status 解耦：优先看 argsStreaming；兼容 status=streaming/preparing。
 */
export function isToolArgStreaming(tool?: CHAT.Task | MESSAGE.Task): boolean {
  if (!tool) {
    return false;
  }
  const top = tool as unknown as Record<string, unknown>;
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested =
    resultMap.resultMap &&
    typeof resultMap.resultMap === "object" &&
    !Array.isArray(resultMap.resultMap)
      ? (resultMap.resultMap as Record<string, unknown>)
      : {};
  if (resultMap.argsStreaming === true || nested.argsStreaming === true) {
    return true;
  }
  if (resultMap.argsStreaming === false || nested.argsStreaming === false) {
    return false;
  }
  if (resultMap.isFinal === true || nested.isFinal === true || tool.isFinal) {
    return false;
  }
  const status = String(
    resultMap.status || nested.status || top.status || ""
  ).toLowerCase();
  return status === "streaming" || status === "preparing";
}

/**
 * 是否应展示入参流面板（与 status 解耦）。
 * - 参数仍在生成：true
 * - running 且已有累计 args、尚无实质输出：true（避免 delta→running 一闪消失）
 * - success/failed 终态：false
 */
export function shouldShowToolArgStream(tool?: CHAT.Task | MESSAGE.Task): boolean {
  if (!tool) {
    return false;
  }
  if (isToolArgStreaming(tool)) {
    return true;
  }
  const resultMap = (tool.resultMap || {}) as Record<string, unknown>;
  const nested =
    resultMap.resultMap &&
    typeof resultMap.resultMap === "object" &&
    !Array.isArray(resultMap.resultMap)
      ? (resultMap.resultMap as Record<string, unknown>)
      : {};
  const status = String(
    resultMap.status || nested.status || ""
  ).toLowerCase();
  if (
    status === "success" ||
    status === "failed" ||
    status === "error" ||
    status === "ok" ||
    status === "completed" ||
    status === "done" ||
    resultMap.isFinal === true ||
    nested.isFinal === true ||
    tool.isFinal ||
    tool.finish
  ) {
    return false;
  }
  // running：若有累计入参且还没有工具输出，继续展示完整 args（LeAgent 观感）
  if (status === "running" || status === "") {
    const args =
      (typeof resultMap.argumentsRaw === "string" && resultMap.argumentsRaw) ||
      (typeof resultMap.argumentsText === "string" && resultMap.argumentsText) ||
      (typeof nested.argumentsRaw === "string" && nested.argumentsRaw) ||
      (typeof nested.argumentsText === "string" && nested.argumentsText) ||
      "";
    if (!args.trim()) {
      return false;
    }
    const hasOutputHint =
      typeof resultMap.codeOutput === "string" &&
      (resultMap.codeOutput as string).trim().length > 0;
    return !hasOutputHint;
  }
  return false;
}

export function resolveToolStreamPath(arg: string): string {
  return pickJsonStringFieldAny(PATH_KEYS, arg || "");
}

export function resolveToolStreamBody(name: string, arg: string): string {
  const key = normalizeToolName(name);
  const keys = BODY_KEYS_BY_TOOL[key] || DEFAULT_BODY_KEYS;
  return pickJsonStringFieldAny(keys, arg || "");
}

/**
 * 从累计 argumentsText（可未闭合）构建 streaming 预览模型。
 */
export function resolveToolStreamPreview(
  name: string,
  arg: string
): ToolStreamPreviewModel {
  const raw = arg || "";
  const path = resolveToolStreamPath(raw);
  const body = resolveToolStreamBody(name, raw);
  const key = normalizeToolName(name);

  let header = "";
  if (path) {
    header = path;
  } else if (key === "bash" || key === "code_execute" || key === "code_execution") {
    header = body ? body.split("\n")[0] || body : "";
  } else if (body) {
    const firstLine = body.split("\n")[0] || body;
    header = firstLine.length > 72 ? `${firstLine.slice(0, 71)}…` : firstLine;
  } else if (raw.trim()) {
    const compact = raw.replace(/\s+/g, " ").trim();
    header = compact.length > 72 ? `${compact.slice(0, 71)}…` : compact;
  }

  if (body) {
    return {
      header,
      body,
      hasStructuredBody: true,
    };
  }

  // 尚抽不到 content/code：展示累计 raw JSON，保证用户仍能看到入参增长
  const fallback = fallbackStreamRawPreview(raw);
  return {
    header,
    body: fallback,
    hasStructuredBody: false,
  };
}

/** streaming 时 chip 展示生成进度 */
export function formatToolStreamChip(arg: string, body: string): string {
  const n = (body || arg || "").length;
  if (n <= 0) {
    return "生成参数…";
  }
  if (n < 1024) {
    return `${n} chars`;
  }
  return `${(n / 1024).toFixed(n < 10 * 1024 ? 1 : 0)} KB`;
}

export function pickJsonFieldForTests(key: string, raw: string): string {
  return pickJsonStringField(key, raw);
}
