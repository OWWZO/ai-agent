import { describe, expect, it } from "vitest";
import { formatTabRelativeTime, rankTabSearch, scoreTabSearch } from "./workspaceTabsModel";

const NOW = Date.parse("2026-01-01T12:00:00Z");

describe("scoreTabSearch", () => {
  it("按 ZCode 的权重分档：完全匹配 > 词首 > 包含 > 副标题 > 类型词", () => {
    const entry = {
      key: "k",
      title: "preview-test.mp3",
      hint: "assets/preview-test.mp3",
      typeHint: "音频"
    };
    expect(scoreTabSearch(entry, "preview-test.mp3")).toBe(120);
    expect(scoreTabSearch(entry, "preview")).toBe(90);
    expect(scoreTabSearch(entry, "test")).toBe(70);
    expect(scoreTabSearch(entry, "assets")).toBe(40);
    expect(scoreTabSearch(entry, "音频")).toBe(20);
    expect(scoreTabSearch(entry, "zzz")).toBe(0);
  });

  it("大小写不敏感，空查询得 0（由 rankTabSearch 负责「不过滤」）", () => {
    const entry = {
      key: "k",
      title: "Preview-Test.Opus"
    };
    expect(scoreTabSearch(entry, "PREVIEW")).toBe(90);
    expect(scoreTabSearch(entry, "")).toBe(0);
  });
});

describe("rankTabSearch", () => {
  const entries = [
    {
      key: "a",
      title: "report.md",
      typeHint: "md"
    },
    {
      key: "b",
      title: "preview-test.mp3",
      typeHint: "mp3"
    },
    {
      key: "c",
      title: "assets/preview-test.mp4",
      hint: "assets/preview-test.mp4",
      typeHint: "mp4"
    },
  ];

  it("空查询原样返回，保持打开顺序", () => {
    expect(rankTabSearch(entries, "  ").map((item) => item.key)).toEqual([
      "a",
      "b",
      "c",
    ]);
  });

  it("命中标题的排在只命中类型词的前面，同分保持原序", () => {
    expect(rankTabSearch(entries, "preview").map((item) => item.key)).toEqual([
      "b",
      "c",
    ]);
    expect(rankTabSearch(entries, "mp3").map((item) => item.key)).toEqual(["b"]);
    expect(rankTabSearch(entries, "md").map((item) => item.key)).toEqual(["a"]);
  });

  it("都不命中时返回空数组（浮层据此显示空态）", () => {
    expect(rankTabSearch(entries, "no-such-tab")).toEqual([]);
  });
});

describe("formatTabRelativeTime", () => {
  it("四档阈值：刚刚 / X 分 / X 小时 / X 天", () => {
    expect(formatTabRelativeTime(NOW - 5_000, NOW)).toBe("刚刚");
    expect(formatTabRelativeTime(NOW - 59_000, NOW)).toBe("刚刚");
    expect(formatTabRelativeTime(NOW - 60_000, NOW)).toBe("1 分");
    expect(formatTabRelativeTime(NOW - 59 * 60_000, NOW)).toBe("59 分");
    expect(formatTabRelativeTime(NOW - 60 * 60_000, NOW)).toBe("1 小时");
    expect(formatTabRelativeTime(NOW - 23 * 3_600_000, NOW)).toBe("23 小时");
    expect(formatTabRelativeTime(NOW - 24 * 3_600_000, NOW)).toBe("1 天");
  });

  it("未来时间戳按「刚刚」处理，不出现负数", () => {
    expect(formatTabRelativeTime(NOW + 10_000, NOW)).toBe("刚刚");
  });

  it("时间戳缺失或非法时返回空串（调用方据此不渲染这一段）", () => {
    expect(formatTabRelativeTime(undefined, NOW)).toBe("");
    expect(formatTabRelativeTime(Number.NaN, NOW)).toBe("");
  });
});
