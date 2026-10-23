import { describe, expect, it } from "vitest";

import {
  ASSISTANT_RUNNING_PREVIEW,
  DEFAULT_MAX_PREVIEW_CHARS,
  buildNavigatorItems,
  buildPreviewText,
  resolveActiveItemIndex,
  resolveActiveItemIndexFromVirtualRows,
  resolveBarVisualState,
} from "./conversationNavigator";

function createChat(overrides: Partial<CHAT.ChatItem> = {}): CHAT.ChatItem {
  return {
    sessionId: "session-1",
    requestId: "run-1",
    query: "问题",
    files: [],
    forceStop: false,
    multiAgent: { tasks: [] },
    loading: false,
    tasks: [],
    ...overrides,
  } as CHAT.ChatItem;
}

describe("buildPreviewText", () => {
  it("最多两段", () => {
    const text = "第一段\n\n第二段\n\n第三段";
    expect(buildPreviewText([text], { fallback: "空" })).toBe("第一段\n第二段");
  });

  it("超过 220 字符用 ... 截断", () => {
    const long = "a".repeat(300);
    const result = buildPreviewText([long], { fallback: "空" });
    expect(result.length).toBe(DEFAULT_MAX_PREVIEW_CHARS);
    expect(result.endsWith("...")).toBe(true);
  });

  it("空内容走 fallback", () => {
    expect(buildPreviewText(["", "   "], { fallback: "兜底" })).toBe("兜底");
  });

  it("段落内换行压成空格", () => {
    expect(buildPreviewText(["第一行\n第二行"], { fallback: "空" })).toBe(
      "第一行 第二行"
    );
  });
});

describe("buildNavigatorItems", () => {
  it("跳过没有用户 query 的轮次", () => {
    const items = buildNavigatorItems([
      createChat({ requestId: "a", query: "你好" }),
      createChat({ requestId: "b", query: "   " }),
      createChat({ requestId: "c", query: "再见" }),
    ]);
    expect(items.map((item) => item.requestId)).toEqual(["a", "c"]);
  });

  it("剥掉用户正文里的上下文尾块", () => {
    const query = [
      "帮我看看这段代码",
      "",
      "# Code comments:",
      "",
      "## Comment 1",
      "File: src/components/A.tsx",
      "Side: R",
      "Lines: 12-15",
      "Selected text:",
      "```",
      "const total = items.reduce(...)",
      "```",
      "Comment:",
      "这里没处理空数组",
    ].join("\n");
    const items = buildNavigatorItems([createChat({ query })]);
    expect(items[0].userPreview).toBe("帮我看看这段代码");
  });

  it("助手回复三态：有正文 / 运行中 / 无正文", () => {
    const items = buildNavigatorItems([
      createChat({ requestId: "a", response: "已经改好了" }),
      createChat({ requestId: "b", loading: true }),
      createChat({ requestId: "c" }),
    ]);
    expect(items[0].assistantPreview).toBe("已经改好了");
    expect(items[1].assistantPreview).toBe(ASSISTANT_RUNNING_PREVIEW);
    expect(items[1].isRunning).toBe(true);
    expect(items[2].assistantPreview).toBe("");
  });

  it("无 requestId 时用下标兜底 key", () => {
    const items = buildNavigatorItems([createChat({ requestId: "" })]);
    expect(items[0].key).toBe("turn-0");
  });
});

describe("resolveBarVisualState", () => {
  it("焦点项 peak：scaleX 2.6 / opacity 1", () => {
    expect(resolveBarVisualState(3, 3)).toEqual({
      colorTone: "focus",
      opacity: 1,
      scaleX: 2.6,
      tone: "peak",
    });
  });

  it("距离 1 / 2 / 更远逐级衰减", () => {
    expect(resolveBarVisualState(4, 3).scaleX).toBe(1.7);
    expect(resolveBarVisualState(4, 3).opacity).toBe(0.86);
    expect(resolveBarVisualState(5, 3).scaleX).toBe(1.25);
    expect(resolveBarVisualState(5, 3).opacity).toBe(0.72);
    expect(resolveBarVisualState(9, 3).scaleX).toBe(1);
    expect(resolveBarVisualState(9, 3).opacity).toBe(0.58);
  });

  it("无焦点时全部 idle", () => {
    expect(resolveBarVisualState(0, undefined)).toEqual({
      colorTone: "muted",
      opacity: 0.58,
      scaleX: 1,
      tone: "idle",
    });
  });
});

describe("resolveActiveItemIndex", () => {
  it("取离顶部最近的锚点", () => {
    expect(resolveActiveItemIndex([120, 30, -10, 400])).toBe(2);
  });

  it("空数组返回 undefined", () => {
    expect(resolveActiveItemIndex([])).toBeUndefined();
  });

  it("忽略非有限值", () => {
    expect(resolveActiveItemIndex([Number.NaN, 50])).toBe(1);
  });
});

describe("resolveActiveItemIndexFromVirtualRows", () => {
  // 虚拟化后离屏轮次没有 DOM，导航轨必须能只凭行位置 + 滚动偏移定位。
  const rows = [
    { index: 0, start: 0, size: 100 },
    { index: 1, start: 100, size: 200 },
    { index: 2, start: 300, size: 400 },
  ];

  it("挑与视口重叠且起点离视口顶部最近的行", () => {
    // 视口 0-500：三行都重叠，起点 0 最近 → 第 0 行。
    expect(resolveActiveItemIndexFromVirtualRows(rows, 0, 500)).toBe(0);
    // 视口 150-650：第 0 行（0-100）已滚出，第 1 行起点 100 < 150 → 距离 0。
    expect(resolveActiveItemIndexFromVirtualRows(rows, 150, 500)).toBe(1);
    // 视口 350-850：只剩第 2 行（300-700）重叠。
    expect(resolveActiveItemIndexFromVirtualRows(rows, 350, 500)).toBe(2);
  });

  it("行起点在视口之后时按距离取最近", () => {
    expect(resolveActiveItemIndexFromVirtualRows(rows, 250, 500)).toBe(1);
  });

  it("无重叠行时回退到 live tail 索引", () => {
    expect(
      resolveActiveItemIndexFromVirtualRows(rows, 5000, 500, { liveTailIndex: 3 })
    ).toBe(3);
  });

  it("空行列表直接返回 live tail 索引", () => {
    expect(
      resolveActiveItemIndexFromVirtualRows([], 0, 500, { liveTailIndex: 5 })
    ).toBe(5);
    expect(resolveActiveItemIndexFromVirtualRows([], 0, 500)).toBeUndefined();
  });

  it("视口高度退化时不崩：仍取与起点重叠的第一行", () => {
    // scrollTop=100 时行 0(0-100) 与行 1(100-300) 都贴着起点，距离为 0，取在前的行 0。
    expect(resolveActiveItemIndexFromVirtualRows(rows, 100, 0)).toBe(0);
  });
});
