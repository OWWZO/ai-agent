/**
 * Splits one assistant message into markdown runs and openui-lang program blocks.
 *
 * The wire format is the marker protocol from `@openuidev/lang-core`:
 *
 *     ]]>openui:content
 *     root = Col([head])
 *     head = Title("Q3")
 *     ]]>openui:end
 *
 * `parseMessage` from lang-core cannot be reused here: it keeps only the last
 * content section and drops everything outside the markers, while a mixed reply
 * is exactly "markdown, one program block, markdown".
 *
 * v1 rules (see plans/036-openui-integration-execution.md §T2):
 *  - at most one program block per reply; a second block is kept verbatim as
 *    markdown so nothing the model wrote is silently lost
 *  - a `context` section is skipped entirely (reserved for a later phase)
 *  - while streaming, a trailing line that is a prefix of a marker is held back
 *    so the half-typed marker never flashes as text
 *  - a program block that has no `end` yet is `closed: false` and swallows the
 *    rest of the text, because a half-written program may well contain lines
 *    that look like markdown
 *  - a ```openui fenced code block is normalized into the marker protocol first
 *    (see `normalizeOpenUiFences`) — the prompt forbids fences, but models wrap
 *    code in one anyway, and without this the whole reply degrades into a plain
 *    markdown code block that never reaches the renderer or Autofix
 *  - an inline `]]>openui:content` (or `:end`) stuck on the same line as code is
 *    split onto its own line first (see `normalizeOpenUiMarkers`) — the parser
 *    requires the marker to be the whole line, otherwise the block is invisible
 */

export type Segment =
  | { type: "markdown"; text: string }
  | { type: "openui"; text: string; closed: boolean };

// Only the three known section kinds count as markers; `]]>openui:cont` must stay
// "a truncated `content`" rather than becoming an unknown marker.
const MARKER = /^]]>openui:(content|context|end)(\?.*)?$/;
const MARKER_PREFIX = "]]>openui:";
const KNOWN_KINDS = ["content", "context", "end"];

/** Opening fence of a code block tagged as openui: ```openui / ```openui-lang */
const FENCE_OPEN = /^\s*`{3,}\s*openui(-lang)?\s*$/i;
/** Any closing fence: ``` (or more backticks) on its own line */
const FENCE_CLOSE = /^\s*`{3,}\s*$/;

/**
 * Rewrites a ```openui fenced code block into the marker protocol.
 *
 * The system prompt says "never inside a code fence", but models wrap DSL in one
 * anyway. Left alone, the fence makes the whole reply land in a single markdown
 * segment: `isRenderableProgram` never runs, so backend Autofix never fires and
 * the user just sees syntax-highlighted source.
 *
 * Only the FIRST fenced block is converted — the protocol allows at most one
 * program per reply, and a second one stays verbatim rather than being lost.
 * A fence with no closing line yet (still streaming) is emitted unclosed.
 */
export function normalizeOpenUiFences(raw: string): string {
  // Already speaking the marker protocol: nothing to rewrite.
  if (raw.includes(MARKER_PREFIX)) return raw;

  const { lines, trailingNewline } = splitLines(raw);
  const out: string[] = [];
  let inBlock = false;
  let converted = false;

  for (const line of lines) {
    if (!converted && !inBlock && FENCE_OPEN.test(line)) {
      out.push(`${MARKER_PREFIX}content`);
      inBlock = true;
      continue;
    }
    if (inBlock && FENCE_CLOSE.test(line)) {
      out.push(`${MARKER_PREFIX}end`);
      inBlock = false;
      converted = true;
      continue;
    }
    out.push(line);
  }

  // Stream cut off before the closing fence: keep the block open so the
  // segmenter reports `closed: false` instead of swallowing it as markdown.
  if (inBlock) converted = true;
  if (!converted) return raw;

  return out.join("\n") + (trailingNewline ? "\n" : "");
}

/**
 * Rewrites an inline marker (`]]>openui:content root = Col(...)`) into the
 * marker-on-its-own-line shape the parser requires.
 *
 * <p>提示词里只说 marker 独占一行，模型偏偏把 `]]>openui:content` 紧跟代码挤在同行。
 * 整段就会被当成 markdown，渲染器永不触发。</p>
 *
 * <p>更糟的是模型连 marker 形态都记不清：见过 `]]>`、`]]`、`[[`、`[[[`、甚至完全无括号
 * 裸写 `openui:content`。本函数把这些全部规范成 `]]>openui:(content|end|context)`。</p>
 *
 * <p>三段处理：marker 前的内容 → 独立行；marker → 独立行（canonical 形态）；marker 后内容 → 独立行。
 * 独占一行的非 canonical marker 也直接改写为 canonical。</p>
 */
const MARKER_KIND_GROUP = "(content|end|context)";
/**
 * 在行内任意位置找 marker。前缀可以是：
 *   - `]]>`（canonical，逐字匹配）
 *   - `]]` 一个或多个（缺 `>`）
 *   - `[[` 一个或多个（模型有时把方括号朝开）
 *   - 无 / 仅前导空白
 * 注意：`\]\]>` 必须排在 `\]+` 前面，否则正则会先吃掉 `]]`、把 `>` 落单。
 *
 * <p>捕获组：1 前缀、2 `openui:kind` 整段、3 仅 kind、4 `?attrs`。</p>
 */
const MARKER_LOOSE = new RegExp(
  `((?:\\s|\\]\\]>|\\[+|\\]+)*)(openui:${MARKER_KIND_GROUP})(\\?[^ \\t\\n]*)?`
);

function normalizeOpenUiMarkers(raw: string): string {
  if (!new RegExp(`openui:${MARKER_KIND_GROUP}`).test(raw)) return raw;
  return raw
    .split("\n")
    .map((line) => {
      const m = MARKER_LOOSE.exec(line);
      if (!m) return line;
      const before = line.slice(0, m.index).trimEnd();
      const after = line.slice(m.index + m[0].length).trim();
      const attrs = m[4] ?? "";
      const canonical = `${MARKER_PREFIX}${m[3]}${attrs}`;
      // marker 独占一行：已经是 canonical 就保持，否则改写
      if (!before && !after) {
        return line === canonical ? line : canonical;
      }
      const parts: string[] = [];
      if (before) parts.push(before);
      parts.push(canonical);
      if (after) parts.push(after);
      return parts.join("\n");
    })
    .join("\n");
}

/**
 * True when `line` could still grow into a fence: `` ` ``, `` ``` ``, `` ```ope ``.
 * Held back on the last line so a half-typed fence never flashes as prose.
 */
function isTruncatedFence(line: string): boolean {
  const trimmed = line.trim();
  if (!trimmed.startsWith("`")) return false;
  if (FENCE_OPEN.test(trimmed) || FENCE_CLOSE.test(trimmed)) return false;
  return /^`{2,}$/.test(trimmed) || "```openui".startsWith(trimmed);
}

/**
 * True when `line` could still grow into a marker with more tokens to come:
 * `]]`, `]]>`, `]]>openui:`, `]]>openui:cont`, `[[[openui:cont`, `openui:cont`, ...
 */
function isTruncatedMarker(line: string): boolean {
  if (line.startsWith("]]")) {
    if (MARKER.test(line)) return false;
    if (MARKER_PREFIX.startsWith(line)) return true;
    if (!line.startsWith(MARKER_PREFIX)) return false;
    const rest = line.slice(MARKER_PREFIX.length);
    return KNOWN_KINDS.some((kind) => kind.startsWith(rest));
  }
  // 任何 bracket 形态（`]]` / `[[` / `[[[` / 无括号）+ 半截 openui:kind
  const m = line.match(/^[\s\[\]]*openui:(.*)$/);
  if (!m) return false;
  const rest = m[1];
  const firstToken = rest.split(/[ \t?]/, 1)[0];
  if (KNOWN_KINDS.includes(firstToken)) return false; // 完整 marker，不 hold
  return KNOWN_KINDS.some((kind) => kind.startsWith(rest));
}

/** Splits into lines, dropping the `\r` of `\r\n` and reporting the trailing newline. */
function splitLines(raw: string): { lines: string[]; trailingNewline: boolean } {
  const parts = raw.split("\n");
  const trailingNewline = parts.length > 1 && parts[parts.length - 1] === "";
  if (trailingNewline) parts.pop();
  return {
    lines: parts.map((line) => (line.endsWith("\r") ? line.slice(0, -1) : line)),
    trailingNewline,
  };
}

export function splitOpenUiSegments(raw: string): Segment[] {
  const source = normalizeOpenUiMarkers(normalizeOpenUiFences(raw));
  const { lines, trailingNewline } = splitLines(source);
  const out: Segment[] = [];

  let markdown: string[] = [];
  let program: string[] = [];
  let extra: string[] = [];
  let mode: "markdown" | "program" | "context" | "extra" = "markdown";
  let sawProgram = false;

  const flushMarkdown = () => {
    if (!markdown.length) return;
    const text = markdown.join("\n");
    markdown = [];
    if (text.trim()) out.push({ type: "markdown", text });
  };

  const flushExtra = () => {
    if (!extra.length) return;
    const text = extra.join("\n");
    extra = [];
    if (text.trim()) out.push({ type: "markdown", text });
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // A marker can arrive split across chunks; hold the partial line back until
    // the next chunk completes it. Only the very last line can be incomplete.
    if (
      i === lines.length - 1 &&
      !trailingNewline &&
      (isTruncatedMarker(line) || isTruncatedFence(line))
    ) {
      break;
    }

    const marker = MARKER.exec(line);
    if (marker) {
      const kind = marker[1];
      if (kind === "content") {
        if (!sawProgram) {
          sawProgram = true;
          flushMarkdown();
          mode = "program";
        } else {
          // Second block in one reply: keep it verbatim as markdown.
          flushMarkdown();
          extra.push(line);
          mode = "extra";
        }
        continue;
      }
      if (kind === "context") {
        // Not rendered; reserved for program state in a later phase.
        if (mode === "program") {
          flushMarkdown();
          out.push({ type: "openui", text: program.join("\n"), closed: true });
          program = [];
        }
        mode = "context";
        continue;
      }
      if (kind === "end") {
        if (mode === "program") {
          flushMarkdown();
          const text = program.join("\n");
          program = [];
          if (text.trim()) out.push({ type: "openui", text, closed: true });
          mode = "markdown";
        } else if (mode === "context") {
          mode = "markdown";
        } else if (mode === "extra") {
          extra.push(line);
          flushExtra();
          mode = "markdown";
        }
        // An `end` with nothing open is a stray marker: ignore it.
        continue;
      }
    }

    if (mode === "program") program.push(line);
    else if (mode === "extra") extra.push(line);
    else if (mode === "markdown") markdown.push(line);
    // mode === "context": dropped on purpose
  }

  flushExtra();
  if (mode === "program") {
    out.push({ type: "openui", text: program.join("\n"), closed: false });
  } else {
    flushMarkdown();
  }

  return out;
}
