import { describe, expect, it } from "vitest";
import {
  buildPromptWithCodeComments,
  parsePromptCodeComments,
  type CodeCommentAttachment,
} from "./codeCommentContext";
import {
  buildPromptWithConversationSelections,
  parsePromptConversationSelections,
  type ConversationSelectionAttachment,
} from "./conversationSelectionContext";
import {
  buildPromptWithWebElementContexts,
  parsePromptWebElementContexts,
  type WebElementAttachment,
} from "./webElementContext";
import {
  hasComposerPromptContexts,
  parseComposerPromptContexts,
  serializeComposerPromptContexts,
} from "./serialize";

const comment = (overrides: Partial<CodeCommentAttachment> = {}): CodeCommentAttachment => ({
  id: "c1",
  sourcePath: "src/components/A.tsx",
  sourceTitle: "A.tsx",
  startLine: 12,
  endLine: 15,
  selectedText: "const total = items.reduce((a, b) => a + b, 0)",
  comment: "这里没处理空数组",
  ...overrides,
});

const element = (overrides: Partial<WebElementAttachment> = {}): WebElementAttachment => ({
  id: "w1",
  pageUrl: "http://localhost:3000/index.html",
  pageTitle: "首页",
  tagName: "button",
  selector: "#submit",
  text: "提交订单",
  htmlExcerpt: '<button id="submit" class="btn btn-primary">提交订单</button>',
  style: { color: "#FFFFFF", backgroundColor: "#2563EB", fontSize: "14px", fontFamily: "Inter" },
  rect: { x: 120, y: 340, width: 88, height: 32 },
  capturedAt: 1_700_000_000_000,
  ...overrides,
});

const selection = (
  overrides: Partial<ConversationSelectionAttachment> = {}
): ConversationSelectionAttachment => ({
  id: "s1",
  path: "README.md",
  text: "选中一段正文",
  ...overrides,
});

describe("code comment context", () => {
  it("序列化成 ZCode 同款尾块", () => {
    const result = buildPromptWithCodeComments("帮我看看这段", [comment()]);
    expect(result).toBe(
      [
        "帮我看看这段",
        "",
        "# Code comments:",
        "",
        "## Comment 1",
        "File: src/components/A.tsx",
        "Side: R",
        "Lines: 12-15",
        "Selected text:",
        "```",
        "const total = items.reduce((a, b) => a + b, 0)",
        "```",
        "Comment:",
        "这里没处理空数组",
      ].join("\n")
    );
  });

  it("单行范围折叠成单个行号", () => {
    const result = buildPromptWithCodeComments("", [comment({ startLine: 7, endLine: 7 })]);
    expect(result).toContain("Lines: 7");
    expect(result).not.toContain("Lines: 7-7");
  });

  it("往返解析回同一份附件", () => {
    const serialized = buildPromptWithCodeComments("正文", [comment()]);
    const parsed = parsePromptCodeComments(serialized);
    expect(parsed.visibleContent).toBe("正文");
    expect(parsed.codeCommentAttachments).toHaveLength(1);
    expect(parsed.codeCommentAttachments[0]).toMatchObject({
      sourcePath: "src/components/A.tsx",
      sourceTitle: "A.tsx",
      startLine: 12,
      endLine: 15,
      selectedText: "const total = items.reduce((a, b) => a + b, 0)",
      comment: "这里没处理空数组",
    });
  });

  it("多行选中文本与中文评论往返", () => {
    const payload = comment({
      selectedText: "第一行\n第二行\n第三行",
      comment: "第 2 行有个拼写错误",
    });
    const parsed = parsePromptCodeComments(buildPromptWithCodeComments("", [payload]));
    expect(parsed.codeCommentAttachments[0]?.selectedText).toBe("第一行\n第二行\n第三行");
    expect(parsed.codeCommentAttachments[0]?.comment).toBe("第 2 行有个拼写错误");
  });

  it("无尾块时原样返回正文", () => {
    const parsed = parsePromptCodeComments("普通提问，没有上下文");
    expect(parsed.visibleContent).toBe("普通提问，没有上下文");
    expect(parsed.codeCommentAttachments).toHaveLength(0);
  });
});

describe("web element context", () => {
  it("序列化成 ZCode 同款尾块", () => {
    const result = buildPromptWithWebElementContexts("看看这个按钮", [element()]);
    expect(result).toContain("# Web page elements:");
    expect(result).toContain("## Element 1");
    expect(result).toContain("URL: http://localhost:3000/index.html");
    expect(result).toContain("Title: 首页");
    expect(result).toContain("Tag: button");
    expect(result).toContain("Selector: #submit");
    expect(result).toContain("Background: #2563EB");
    expect(result).toContain("Font: 14px Inter");
    expect(result).toContain("Rect: x=120, y=340, width=88, height=32");
    expect(result).toContain("Text:");
    expect(result).toContain("提交订单");
    expect(result).toContain("HTML excerpt:");
    expect(result).toContain("```html");
  });

  it("往返解析回同一份附件", () => {
    const parsed = parsePromptWebElementContexts(
      buildPromptWithWebElementContexts("正文", [element()])
    );
    expect(parsed.visibleContent).toBe("正文");
    expect(parsed.webElementContexts).toHaveLength(1);
    expect(parsed.webElementContexts[0]).toMatchObject({
      pageUrl: "http://localhost:3000/index.html",
      pageTitle: "首页",
      tagName: "button",
      selector: "#submit",
      text: "提交订单",
      htmlExcerpt: '<button id="submit" class="btn btn-primary">提交订单</button>',
    });
    expect(parsed.webElementContexts[0]?.style).toMatchObject({
      color: "#FFFFFF",
      backgroundColor: "#2563EB",
      fontSize: "14px",
      fontFamily: "Inter",
    });
  });

  it("空标题写成 (untitled) 再解析回空串", () => {
    const serialized = buildPromptWithWebElementContexts("", [element({ pageTitle: "" })]);
    expect(serialized).toContain("Title: (untitled)");
    const parsed = parsePromptWebElementContexts(serialized);
    expect(parsed.webElementContexts[0]?.pageTitle).toBe("");
  });
});

describe("conversation selection context", () => {
  it("序列化成 userselect JSON 块", () => {
    const result = buildPromptWithConversationSelections("正文", [selection()]);
    expect(result).toBe(
      ['正文', "", "# userselect:", "```userselect", '[{"path":"README.md","text":"选中一段正文"}]', "```"].join(
        "\n"
      )
    );
  });

  it("往返解析回同一份引用", () => {
    const parsed = parsePromptConversationSelections(
      buildPromptWithConversationSelections("正文", [selection(), selection({ id: "s2", text: "第二段", path: undefined })])
    );
    expect(parsed.visibleContent).toBe("正文");
    expect(parsed.references).toHaveLength(2);
    expect(parsed.references[0]).toMatchObject({ path: "README.md", text: "选中一段正文" });
    expect(parsed.references[1]?.path).toBeUndefined();
  });

  it("非法 JSON 时不剥正文", () => {
    const broken = "正文\n\n# userselect:\n```userselect\nnot-json\n```";
    const parsed = parsePromptConversationSelections(broken);
    expect(parsed.visibleContent).toBe(broken);
    expect(parsed.references).toHaveLength(0);
  });
});

describe("serializeComposerPromptContexts / parseComposerPromptContexts", () => {
  it("空上下文时只返回 trim 后的正文", () => {
    const result = serializeComposerPromptContexts("  只有正文  ", {
      codeComments: [],
      conversationSelections: [],
      webElements: [],
    });
    expect(result).toBe("只有正文");
  });

  it("三类混排：序列化顺序与解析顺序严格相反", () => {
    const serialized = serializeComposerPromptContexts("请一起改", {
      conversationSelections: [selection()],
      codeComments: [comment()],
      webElements: [element()],
    });

    // 序列化顺序：正文 → userselect → Code comments → Web page elements
    const selectionIndex = serialized.indexOf("# userselect:");
    const commentIndex = serialized.indexOf("# Code comments:");
    const elementIndex = serialized.indexOf("# Web page elements:");
    expect(selectionIndex).toBeGreaterThan(-1);
    expect(commentIndex).toBeGreaterThan(selectionIndex);
    expect(elementIndex).toBeGreaterThan(commentIndex);

    const parsed = parseComposerPromptContexts(serialized);
    expect(parsed.visibleContent).toBe("请一起改");
    expect(parsed.codeComments).toHaveLength(1);
    expect(parsed.webElements).toHaveLength(1);
    expect(parsed.conversationSelections).toHaveLength(1);
    expect(hasComposerPromptContexts(serialized)).toBe(true);
  });

  it("正文含 ``` 围栏时仍能正确剥离尾块", () => {
    const body = "看下这段：\n\n```ts\nconst a = 1;\n```\n\n有问题吗";
    const serialized = serializeComposerPromptContexts(body, {
      codeComments: [comment()],
      conversationSelections: [],
      webElements: [],
    });
    const parsed = parseComposerPromptContexts(serialized);
    expect(parsed.visibleContent).toBe(body);
    expect(parsed.codeComments).toHaveLength(1);
  });

  it("正文为空、只有上下文时也能往返", () => {
    const serialized = serializeComposerPromptContexts("", {
      codeComments: [],
      conversationSelections: [],
      webElements: [element()],
    });
    expect(serialized.startsWith("# Web page elements:")).toBe(true);
    const parsed = parseComposerPromptContexts(serialized);
    expect(parsed.visibleContent).toBe("");
    expect(parsed.webElements).toHaveLength(1);
  });

  it("没有尾块时 visibleContent 原样保留、上下文为空", () => {
    const parsed = parseComposerPromptContexts("就是一句普通提问");
    expect(parsed.visibleContent).toBe("就是一句普通提问");
    expect(hasComposerPromptContexts("就是一句普通提问")).toBe(false);
  });

  it("上下文块不参与正文截断：正文先截断再拼接", () => {
    const longBody = "甲".repeat(9000);
    const truncated = longBody.slice(0, 8000);
    const serialized = serializeComposerPromptContexts(truncated, {
      codeComments: [comment()],
      conversationSelections: [],
      webElements: [],
    });
    // 尾块必须完整保留在 8000 字正文之后
    expect(serialized).toContain("# Code comments:");
    expect(serialized).toContain("Lines: 12-15");
    const parsed = parseComposerPromptContexts(serialized);
    expect(parsed.visibleContent).toBe(truncated);
    expect(parsed.codeComments).toHaveLength(1);
  });
});
