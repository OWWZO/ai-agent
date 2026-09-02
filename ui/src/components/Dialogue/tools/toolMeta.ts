const SUMMARY_MAX = 80;
const BASH_MAX = 72;

const NAME_ALIASES: Record<string, string> = {
  multiedit: "multi_edit",
  multiedits: "multi_edit",
  shell: "bash",
  run: "bash",
  exec: "bash",
  execute_command: "bash",
  code_interpreter: "bash",
  ripgrep: "grep",
  rg: "grep",
  find: "glob",
  fetch: "web_fetch",
  webfetch: "web_fetch",
  url_fetch: "web_fetch",
  urlfetch: "web_fetch",
  list: "ls",
  listdir: "ls",
  list_dir: "ls",
  todowrite: "todo",
  todo_write: "todo",
  todoread: "todo",
  todolist: "todo",
  todo_list: "todo",
  agent: "task",
  subagent: "task",
  agent_dispatch: "task",
  websearch: "search",
  web_search: "search",
  internal_search: "search",
  read_file: "read",
  read_tool: "read",
  write_file: "write",
  edit_file: "edit",
  file_edit: "edit",
};

const TOOL_LABELS: Record<string, string> = {
  read: "Read",
  bash: "Bash",
  edit: "Edit",
  multi_edit: "Edit",
  write: "Write",
  grep: "Grep",
  glob: "Glob",
  ls: "LS",
  web_fetch: "Fetch",
  search: "Search",
  todo: "Todo",
  task: "Agent",
  askuserquestion: "提问",
  canvas_publish: "发布画布",
  html: "发布画布",
};

export type ToolChipInput = {
  name: string;
  arg: string;
  output?: string[];
  timing?: string;
  status?: string;
};

export function normalizeToolName(name: string): string {
  const lower = (name ?? "").trim().toLowerCase().replace(/[\s-]+/g, "_");
  return NAME_ALIASES[lower] ?? lower;
}

export function toolLabel(name: string): string {
  const key = normalizeToolName(name);
  return TOOL_LABELS[key] ?? (name?.trim() || "Tool");
}

function clip(s: string, max = SUMMARY_MAX): string {
  const trimmed = s.trim();
  return trimmed.length > max ? `${trimmed.slice(0, max - 1)}…` : trimmed;
}

function isEmptyArg(arg: string, d: Record<string, unknown> | null): boolean {
  const s = arg.trim();
  if (s === "" || s === "{}" || s === "[]" || s === "null") return true;
  if (d && Object.keys(d).length === 0) return true;
  return false;
}

function parseArg(arg: string): Record<string, unknown> | null {
  const s = arg.trim();
  if (!s.startsWith("{")) return null;
  try {
    const v = JSON.parse(s);
    return v && typeof v === "object" && !Array.isArray(v)
      ? (v as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

function str(v: unknown): string | undefined {
  return typeof v === "string" && v.length > 0 ? v : undefined;
}

function num(v: unknown): number | undefined {
  return typeof v === "number" && Number.isFinite(v) ? v : undefined;
}

function urlHost(url: string): string {
  try {
    const u = new URL(url);
    const seg = u.pathname.split("/").filter(Boolean)[0];
    return seg ? `${u.host}/${seg}` : u.host;
  } catch {
    return url.replace(/^https?:\/\//, "");
  }
}

function filePath(d: Record<string, unknown>): string | undefined {
  return (
    str(d.path) ??
    str(d.file_path) ??
    str(d.filePath) ??
    str(d.filename) ??
    str(d.fileName) ??
    str(d.file_name) ??
    str(d.targetPath) ??
    str(d.outputFileName) ??
    str(d.displayName) ??
    str(d.name)
  );
}

export function toolSummary(name: string, arg: string, full = false): string {
  const c = (s: string, max = SUMMARY_MAX): string =>
    full ? s.trim() : clip(s, max);
  try {
    const d = parseArg(arg);
    if (!full && isEmptyArg(arg, d)) return "";
    const fallback = () => c(arg.replace(/^·\s*/, ""));
    if (!d) return fallback();

    switch (normalizeToolName(name)) {
      case "read": {
        const path = filePath(d);
        if (!path) return fallback();
        const start =
          num(d.offset) ?? num(d.line_start) ?? num(d.start_line);
        const len = num(d.limit) ?? num(d.length);
        const end =
          num(d.line_end) ??
          num(d.end_line) ??
          (start !== undefined && len !== undefined
            ? start + len
            : undefined);
        if (start !== undefined && end !== undefined) {
          return c(`${path}:${start}-${end}`);
        }
        if (start !== undefined) return c(`${path}:${start}`);
        return c(path);
      }
      case "write": {
        const path = filePath(d);
        return path ? c(`${path}  created`) : fallback();
      }
      case "canvas_publish":
      case "html": {
        const path = str(d.html_path) ?? str(d.htmlPath) ?? filePath(d);
        return path ? c(path) : fallback();
      }
      case "edit":
      case "multi_edit": {
        const path = filePath(d);
        return path ? c(path) : fallback();
      }
      case "bash": {
        const cmd = str(d.command) ?? str(d.cmd) ?? str(d.script) ?? str(d.code);
        return cmd ? c(cmd, BASH_MAX) : fallback();
      }
      case "grep":
      case "search": {
        const pattern =
          str(d.pattern) ?? str(d.query) ?? str(d.regex) ?? str(d.q);
        const path = str(d.path) ?? str(d.glob) ?? str(d.include);
        if (pattern && path) return c(`${pattern}  in ${path}`);
        return pattern ? c(pattern) : fallback();
      }
      case "glob": {
        const pattern = str(d.pattern) ?? str(d.glob) ?? str(d.query);
        const path = str(d.path) ?? str(d.cwd);
        if (pattern && path) return c(`${pattern}  in ${path}`);
        return pattern ? c(pattern) : str(d.path) ? c(str(d.path)!) : fallback();
      }
      case "ls": {
        const dir =
          str(d.path) ?? str(d.dir) ?? str(d.directory) ?? str(d.cwd);
        return dir ? c(dir) : fallback();
      }
      case "web_fetch": {
        const url = str(d.url) ?? str(d.uri);
        return url ? c(urlHost(url)) : fallback();
      }
      case "todo":
      case "task": {
        const label =
          str(d.description) ??
          str(d.title) ??
          str(d.prompt) ??
          str(d.name) ??
          str(d.subagent_type) ??
          str(d.subAgentType);
        if (label) return c(label);
        const items = Array.isArray(d.todos)
          ? d.todos
          : Array.isArray(d.items)
            ? d.items
            : undefined;
        if (items) return c(`${items.length} todos`);
        return fallback();
      }
      default:
        return fallback();
    }
  } catch {
    return arg;
  }
}

export function toolChip(tool: ToolChipInput): string {
  try {
    switch (normalizeToolName(tool.name)) {
      case "bash":
        return tool.timing || "";
      case "read":
        if (tool.output && tool.output.length > 0) {
          return `${tool.output.length} lines`;
        }
        return "";
      case "edit":
      case "multi_edit":
      case "write": {
        // 精确 +A −B 由 EditToolCall.formatEditDiffChip 负责；此处兜底 output 文本
        if (tool.output) {
          for (const line of tool.output) {
            const match = line.match(/\+(\d+).*[-−](\d+)/);
            if (match) return `+${match[1]} −${match[2]}`;
          }
          if (tool.status !== "error") return "edited";
        }
        return "";
      }
      case "grep":
      case "search":
        if (tool.output && tool.output.length > 0) {
          return `${tool.output.length} results`;
        }
        return "";
      default:
        return "";
    }
  } catch {
    return "";
  }
}

/** Generic 卡点击优先开工作区的工具（Edit/Agent 已由专用渲染器接管） */
export function opensWorkspacePreferentially(name: string): boolean {
  const key = normalizeToolName(name);
  return key === "browser" || key === "html" || key === "markdown" || key === "ppt";
}
