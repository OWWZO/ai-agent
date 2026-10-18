import { describe, expect, it } from 'vitest';

import { parseChartNumber, toSpec } from './chart';

describe('parseChartNumber', () => {
  it('passes clean numbers and numeric strings through', () => {
    expect(parseChartNumber(12)).toBe(12);
    expect(parseChartNumber("12")).toBe(12);
    expect(parseChartNumber("-3.5")).toBe(-3.5);
  });

  it('strips thousand separators, spaces and percent signs', () => {
    expect(parseChartNumber("1,234.5")).toBe(1234.5);
    expect(parseChartNumber("45%")).toBe(45);
    expect(parseChartNumber("12 元")).toBe(12);
    expect(parseChartNumber(" 88 ")).toBe(88);
  });

  it('converts Chinese magnitude suffixes', () => {
    expect(parseChartNumber("12.4万")).toBe(124000);
    expect(parseChartNumber("1.2亿")).toBe(120000000);
    expect(parseChartNumber("12")).toBe(12);
  });

  it('normalizes full-width digits and punctuation', () => {
    expect(parseChartNumber("１２３")).toBe(123);
    expect(parseChartNumber("１，２３４")).toBe(1234);
  });

  it('falls back to zero for unparseable input without guessing', () => {
    expect(parseChartNumber("abc")).toBe(0);
    expect(parseChartNumber(null)).toBe(0);
    expect(parseChartNumber(undefined)).toBe(0);
    expect(parseChartNumber("１月")).toBe(0);
  });

  it('keeps non-finite numbers at zero', () => {
    expect(parseChartNumber(Number.NaN)).toBe(0);
    expect(parseChartNumber(Number.POSITIVE_INFINITY)).toBe(0);
  });
});

describe('toSpec series guard', () => {
  const data = [
    {
      month: "1月",
      营收: 120,
      成本: 80
    },
    {
      month: "2月",
      营收: 150,
      成本: 90
    },
  ];

  it('keeps explicit series that exist in the data', () => {
    const spec = toSpec({
      type: "bar",
      data,
      x: "month",
      series: ["营收"]
    } as never);
    expect(spec?.series).toEqual(["营收"]);
  });

  it('drops phantom series and keeps the real ones', () => {
    // 模型给 series 补了单位，data key 没有 —— 脏 key 剔除，真 key 保留。
    const spec = toSpec({
      type: "bar",
      data,
      x: "month",
      series: ["营收(元)", "成本"]
    } as never);
    expect(spec?.series).toEqual(["成本"]);
  });

  it('falls back to every number key when all explicit series are phantoms', () => {
    const spec = toSpec({
      type: "bar",
      data,
      x: "month",
      series: ["营收(元)", "成本(元)"]
    } as never);
    expect(spec?.series).toEqual(["营收", "成本"]);
  });

  it('defaults to number keys when series is omitted', () => {
    const spec = toSpec({
      type: "bar",
      data
    } as never);
    expect(spec?.series).toEqual(["营收", "成本"]);
    expect(spec?.x).toBe("month");
  });

  it('returns null for empty data', () => {
    expect(toSpec({
      type: "bar",
      data: []
    } as never)).toBeNull();
  });
});
