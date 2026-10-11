import { describe, expect, it } from "vitest";

import {
  formatWorkDuration,
  formatWorkDurationParts,
  parseTimestampMs,
  workDurationParts,
} from "./workDuration";

describe("workDurationParts", () => {
  it("最多两段：1 小时 3 分 30 秒只写前两段", () => {
    // 3780s = 1h 3m 0s；再加 30s 变成 1h 3m 30s
    expect(workDurationParts(3_810_000)).toEqual([
      { value: 1, unit: "小时" },
      { value: 3, unit: "分" },
    ]);
  });

  it("秒向最近取整", () => {
    expect(workDurationParts(2_400)).toEqual([{ value: 2, unit: "秒" }]);
    expect(workDurationParts(2_600)).toEqual([{ value: 3, unit: "秒" }]);
  });

  it("至少 1 秒", () => {
    expect(workDurationParts(0)).toEqual([{ value: 1, unit: "秒" }]);
  });
});

describe("formatWorkDuration", () => {
  it.each([
    [264_000, "4 分 24 秒"],
    [3_600_000, "1 小时"],
    [42_000, "42 秒"],
    [90_000, "1 分 30 秒"],
    [0, "1 秒"],
    [3_780_000, "1 小时 3 分"],
  ])("%dms → %s", (ms, expected) => {
    expect(formatWorkDuration(ms)).toBe(expected);
  });

  it("非法输入返回 undefined", () => {
    expect(formatWorkDuration(undefined)).toBeUndefined();
    expect(formatWorkDuration(Number.NaN)).toBeUndefined();
    expect(formatWorkDuration(Number.POSITIVE_INFINITY)).toBeUndefined();
    expect(formatWorkDuration(-1)).toBeUndefined();
  });
});

describe("formatWorkDurationParts", () => {
  it("数字与中文单位之间留空格", () => {
    expect(
      formatWorkDurationParts([
        { value: 2, unit: "分" },
        { value: 5, unit: "秒" },
      ])
    ).toBe("2 分 5 秒");
  });
});

describe("parseTimestampMs", () => {
  it("解析毫秒字符串", () => {
    expect(parseTimestampMs("1714041600000")).toBe(1_714_041_600_000);
  });

  it("解析 ISO 字符串", () => {
    expect(parseTimestampMs("2026-07-06T10:00:00")).toBe(
      Date.parse("2026-07-06T10:00:00")
    );
  });

  it("解析数字", () => {
    expect(parseTimestampMs(1_714_041_600_000)).toBe(1_714_041_600_000);
  });

  it("空值与非数值返回 undefined", () => {
    expect(parseTimestampMs(undefined)).toBeUndefined();
    expect(parseTimestampMs(null)).toBeUndefined();
    expect(parseTimestampMs("")).toBeUndefined();
    expect(parseTimestampMs("   ")).toBeUndefined();
    expect(parseTimestampMs("not-a-date")).toBeUndefined();
  });
});
