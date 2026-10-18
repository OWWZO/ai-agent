import { describe, expect, it } from "vitest";
import { normalizeOpenUiFences, splitOpenUiSegments } from "./segments";

const program = `root = Col([head, kpis])
head = Title("Q3 营收报告", "xl")
kpis = Grid([k1, k2], 2, 180, 4)`;

describe("splitOpenUiSegments", () => {
  it("returns a single markdown segment when there is no block", () => {
    const segments = splitOpenUiSegments("普通的一段回复\n第二行");
    expect(segments).toEqual([{ type: "markdown", text: "普通的一段回复\n第二行" }]);
  });

  it("splits markdown + block + markdown into three segments", () => {
    const raw = `这是说明\n\n]]>openui:content\n${program}\n]]>openui:end\n\n继续看明细`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(3);
    expect(segments[0].type).toBe("markdown");
    expect(segments[0].text.trim()).toBe("这是说明");
    expect(segments[1]).toEqual({ type: "openui", text: program, closed: true });
    expect(segments[2].type).toBe("markdown");
    expect(segments[2].text.trim()).toBe("继续看明细");
  });

  it("handles a block with no text before it", () => {
    const segments = splitOpenUiSegments(`]]>openui:content\n${program}\n]]>openui:end`);
    expect(segments).toEqual([{ type: "openui", text: program, closed: true }]);
  });

  it("handles a block with no text after it", () => {
    const segments = splitOpenUiSegments(`先说明\n]]>openui:content\n${program}\n]]>openui:end`);
    expect(segments).toHaveLength(2);
    expect(segments[0].text.trim()).toBe("先说明");
    expect(segments[1]).toEqual({ type: "openui", text: program, closed: true });
  });

  it("does not mistake markdown characters inside DSL strings for markdown", () => {
    const dsl = [
      'root = Col([t, c])',
      't = Text("**bold** and `code` and {a: 1} and <b>x</b>")',
      'c = Card([t])',
    ].join("\n");
    const segments = splitOpenUiSegments(`]]>openui:content\n${dsl}\n]]>openui:end`);
    expect(segments).toEqual([{ type: "openui", text: dsl, closed: true }]);
  });

  it("keeps the block open while streaming and swallows following lines", () => {
    const raw = `说明\n]]>openui:content\nroot = Col([head])\nhead = Title("Q3")`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(2);
    expect(segments[0].text.trim()).toBe("说明");
    expect(segments[1]).toEqual({
      type: "openui",
      text: 'root = Col([head])\nhead = Title("Q3")',
      closed: false,
    });
  });

  it("treats markdown-looking lines inside an unclosed block as DSL", () => {
    const raw = "]]>openui:content\nroot = Col([a])\n## not a heading\n- not a list";
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(1);
    expect(segments[0]).toEqual({
      type: "openui",
      text: "root = Col([a])\n## not a heading\n- not a list",
      closed: false,
    });
  });

  it("holds back a truncated trailing marker", () => {
    const raw = "前言\n]]>openui:content\nroot = Col([])\n]]>openui:cont";
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(2);
    expect(segments[0].text.trim()).toBe("前言");
    expect(segments[1]).toEqual({ type: "openui", text: "root = Col([])", closed: false });
  });

  it("holds back a bare marker prefix", () => {
    const segments = splitOpenUiSegments("前言\n]]>open");
    expect(segments).toEqual([{ type: "markdown", text: "前言" }]);
  });

  it("ignores a stray end marker", () => {
    const segments = splitOpenUiSegments("前\n]]>openui:end\n后");
    expect(segments).toHaveLength(1);
    expect(segments[0].type).toBe("markdown");
    expect(segments[0].text).toContain("前");
    expect(segments[0].text).toContain("后");
  });

  it("handles CRLF line endings", () => {
    const raw = "a\r\n]]>openui:content\r\nroot = Col([])\r\n]]>openui:end\r\nb";
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(3);
    expect(segments[0].text.trim()).toBe("a");
    expect(segments[1]).toEqual({ type: "openui", text: "root = Col([])", closed: true });
    expect(segments[2].text.trim()).toBe("b");
  });

  it("skips context sections", () => {
    const raw = `]]>openui:content\nroot = Col([])\n]]>openui:end\n]]>openui:context\n{"revision":1}\n]]>openui:end\n尾`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(2);
    expect(segments[0]).toEqual({ type: "openui", text: "root = Col([])", closed: true });
    expect(segments[1].text.trim()).toBe("尾");
    expect(JSON.stringify(segments)).not.toContain("revision");
  });

  it("keeps a second block verbatim as markdown instead of dropping it", () => {
    const raw = `]]>openui:content\nroot = Col([])\n]]>openui:end\n]]>openui:content\nroot = Col([x])\n]]>openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(2);
    expect(segments[0]).toEqual({ type: "openui", text: "root = Col([])", closed: true });
    expect(segments[1].type).toBe("markdown");
    expect(segments[1].text).toContain("root = Col([x])");
    expect(segments[1].text).toContain("]]>openui:content");
  });

  it("accepts attributes on the content marker", () => {
    const segments = splitOpenUiSegments("]]>openui:content?kind=patch\nroot = Col([])\n]]>openui:end");
    expect(segments).toEqual([{ type: "openui", text: "root = Col([])", closed: true }]);
  });

  it("returns nothing for an empty string", () => {
    expect(splitOpenUiSegments("")).toEqual([]);
  });
});

describe("normalizeOpenUiFences", () => {
  it("rewrites a fenced block into the marker protocol", () => {
    const raw = `先说明\n\n\`\`\`openui\n${program}\n\`\`\`\n\n后说明`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(3);
    expect(segments[0].type).toBe("markdown");
    expect(segments[0].text.trim()).toBe("先说明");
    expect(segments[1]).toEqual({ type: "openui", text: program, closed: true });
    expect(segments[2].text.trim()).toBe("后说明");
  });

  it("accepts the openui-lang tag and extra backticks", () => {
    const raw = `\`\`\`\`openui-lang\n${program}\n\`\`\`\``;
    expect(splitOpenUiSegments(raw)).toEqual([
      { type: "openui", text: program, closed: true },
    ]);
  });

  it("leaves a real markdown reply untouched", () => {
    const text = "这是说明\n\n```ts\nconst a = 1;\n```";
    expect(normalizeOpenUiFences(text)).toBe(text);
    expect(splitOpenUiSegments(text)).toEqual([{ type: "markdown", text }]);
  });

  it("keeps an unclosed fence open while streaming", () => {
    const raw = `\`\`\`openui\nroot = Col([head])`;
    expect(splitOpenUiSegments(raw)).toEqual([
      { type: "openui", text: "root = Col([head])", closed: false },
    ]);
  });

  it("does not rewrite when the reply already uses markers", () => {
    const raw = `说明\n]]>openui:content\n${program}\n]]>openui:end`;
    expect(normalizeOpenUiFences(raw)).toBe(raw);
  });

  it("converts only the first fenced block", () => {
    const raw = `\`\`\`openui\nroot = Col([])\n\`\`\`\n\n\`\`\`openui\nroot = Row([])\n\`\`\``;
    const segments = splitOpenUiSegments(raw);
    expect(segments[0]).toEqual({ type: "openui", text: "root = Col([])", closed: true });
    expect(segments[1].type).toBe("markdown");
    expect(segments[1].text).toContain("root = Row([])");
  });
});

describe("splitOpenUiSegments — inline marker normalization", () => {
  it("splits a content marker stuck on the same line as code", () => {
    const raw = `]]>openui:content root = Col([head])\n]]>openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toEqual([{ type: "openui", text: "root = Col([head])", closed: true }]);
  });

  it("splits an end marker stuck on the same line as code", () => {
    const raw = `]]>openui:content\nroot = Col([head]) ]]>openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments[0]).toEqual({ type: "openui", text: "root = Col([head])", closed: true });
  });

  it("splits a marker that has code both before and after it", () => {
    const raw = `前言 ]]>openui:content root = Col([head]) 尾巴\n]]>openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toHaveLength(2);
    expect(segments[0].type).toBe("markdown");
    expect(segments[0].text.trim()).toBe("前言");
    expect(segments[1]).toEqual({ type: "openui", text: "root = Col([head]) 尾巴", closed: true });
  });

  it("preserves the ?attrs suffix on an inline marker", () => {
    const raw = `]]>openui:content?kind=patch root = Col([])\n]]>openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toEqual([{ type: "openui", text: "root = Col([])", closed: true }]);
  });

  it("leaves a marker that is already alone on its line unchanged", () => {
    const raw = `]]>openui:content\nroot = Col([])\n]]>openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments).toEqual([{ type: "openui", text: "root = Col([])", closed: true }]);
  });

  it("reproduces the symptom from the user screenshot", () => {
    // 模型把整段 openui 块挤在以 `]]>openui:content` 开头的一行（或视觉上挤在一起），
    // 旧 parser 会整段当 markdown，新实现必须识别出程序段。
    const raw = `]]>openui:content root = Col([header, kpis], 3)\nheader = Card([Text("hi")])\n]]>openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments[0].type).toBe("openui");
    if (segments[0].type !== "openui") {
      throw new Error("expected the first segment to be an openui block");
    }
    expect(segments[0].closed).toBe(true);
    expect(segments[0].text).toContain("root = Col([header, kpis], 3)");
  });

  it("still holds a half-typed marker back while streaming", () => {
    const segments = splitOpenUiSegments("hello ]]>openui:cont");
    // 整行被 isTruncatedMarker 拦下，不应有 openui 段
    expect(segments.find((s) => s.type === "openui")).toBeUndefined();
  });
});

describe("splitOpenUiSegments — loose marker normalization", () => {
  it("normalizes triple-bracket [[[openui:content to canonical", () => {
    const raw = `[[[openui:content\nroot = Col([head])\n]]]openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments[0]).toEqual({ type: "openui", text: "root = Col([head])", closed: true });
  });

  it("normalizes ]]openui:content (missing `>`)", () => {
    const raw = `]]openui:content\nroot = Col([head])\n]]openui:end`;
    expect(splitOpenUiSegments(raw)).toEqual([
      { type: "openui", text: "root = Col([head])", closed: true },
    ]);
  });

  it("normalizes a bare openui:content with no brackets at all", () => {
    const raw = `openui:content\nroot = Col([head])\nopenui:end`;
    expect(splitOpenUiSegments(raw)).toEqual([
      { type: "openui", text: "root = Col([head])", closed: true },
    ]);
  });

  it("normalizes inline bare openui:content followed by code", () => {
    const raw = `openui:content root = Col([head])\nopenui:end`;
    expect(splitOpenUiSegments(raw)).toEqual([
      { type: "openui", text: "root = Col([head])", closed: true },
    ]);
  });

  it("normalizes inline triple-bracket [[[openui:content followed by code", () => {
    const raw = `[[[openui:content root = Col([head])\n]]]openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments[0]).toEqual({ type: "openui", text: "root = Col([head])", closed: true });
  });

  it("reproduces the symptom in the user's first paste", () => {
    // 用户截图 1：模型吐出 [[[openui:content ... 整段挤在一行 ... ]]]openui:end
    // 我们的 parser 不可能解析 "root = Col(...) x = 0 x=0 y = 3" 这种单行 DSL，
    // 但 splitOpenUiSegments 至少要识别出 openui 段（让 isRenderableProgram 和
    // Autofix 接手），而不是退化成 markdown 字符。
    const raw = `[[[openui:content root = Col([x, y])\n]]]openui:end`;
    const segments = splitOpenUiSegments(raw);
    expect(segments[0].type).toBe("openui");
  });

  it("holds a half-typed bare-bracket marker back while streaming", () => {
    // 流式还没吐完 kind："openui:cont" 可能是 "openui:content" / "openui:context" 的前段
    const segments = splitOpenUiSegments("hello openui:cont");
    expect(segments.find((s) => s.type === "openui")).toBeUndefined();
  });

  it("holds a half-typed triple-bracket marker back while streaming", () => {
    const segments = splitOpenUiSegments("hello [[[openui:cont");
    expect(segments.find((s) => s.type === "openui")).toBeUndefined();
  });
});
