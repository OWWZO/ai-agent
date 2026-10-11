/**
 * MCP 工具名解析（对齐后端 `McpToolNamePolicy`：`mcp__{server}__{tool}`）。
 *
 * 注意：**不要**用 `normalizeToolName` —— 它会把名字小写化，而 server / tool 段
 * 来自 MCP 服务端原始标识，大小写有意义（且后端只做了非 `[a-zA-Z0-9_-]` → `_` 的替换）。
 */
export const MCP_TOOL_PREFIX = "mcp__";
export const MCP_TOOL_SEPARATOR = "__";

export type McpToolIdentity = {
  /** FQ 名（原始大小写） */
  qualifiedName: string;
  serverKey: string;
  toolName: string;
};

/** 是否是 MCP 的 FQ 工具名。 */
export function isMcpToolName(name: string | null | undefined): boolean {
  if (!name || !name.startsWith(MCP_TOOL_PREFIX)) return false;
  const body = name.slice(MCP_TOOL_PREFIX.length);
  const idx = body.indexOf(MCP_TOOL_SEPARATOR);
  return idx > 0 && idx < body.length - MCP_TOOL_SEPARATOR.length;
}

/**
 * 解析 MCP FQ 名。非 FQ 返回 null。
 *
 * server 段可能自身含 `_`（后端把非白名单字符换成 `_`），因此只能按**第一个**
 * `__` 切分 —— 不能 split 后取 [1]/[2]。
 */
export function parseMcpToolName(
  name: string | null | undefined
): McpToolIdentity | null {
  if (!isMcpToolName(name)) return null;
  const qualifiedName = name as string;
  const body = qualifiedName.slice(MCP_TOOL_PREFIX.length);
  const idx = body.indexOf(MCP_TOOL_SEPARATOR);
  return {
    qualifiedName,
    serverKey: body.slice(0, idx),
    toolName: body.slice(idx + MCP_TOOL_SEPARATOR.length),
  };
}

/** 结果是否短到可以当一句话展示（ZCode `isCompactResult`）。 */
const COMPACT_RESULT_MAX_LENGTH = 160;

export function looksLikeJson(value: string): boolean {
  const trimmed = value.trim();
  if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return false;
  try {
    JSON.parse(trimmed);
    return true;
  } catch {
    return false;
  }
}

export function isCompactMcpResult(value: string): boolean {
  const trimmed = value.trim();
  return (
    trimmed.length > 0 &&
    trimmed.length <= COMPACT_RESULT_MAX_LENGTH &&
    !trimmed.includes("\n") &&
    !looksLikeJson(trimmed)
  );
}
