import { describe, expect, it } from "vitest";

import {
  DEFAULT_ROW_HEIGHT_ESTIMATE_PX,
  TimelineRowHeightCache,
} from "./timelineRowHeightCache";

describe("TimelineRowHeightCache", () => {
  it("falls back to the estimate for unmeasured keys", () => {
    const cache = new TimelineRowHeightCache();
    expect(cache.estimate("turn-1")).toBe(DEFAULT_ROW_HEIGHT_ESTIMATE_PX);
    expect(cache.estimate(undefined, 42)).toBe(42);
  });

  it("returns measured heights and keeps them across lookups", () => {
    const cache = new TimelineRowHeightCache();
    cache.set("turn-1", 320.4);
    cache.set("turn-2", 88);
    expect(cache.estimate("turn-1")).toBe(320.4);
    expect(cache.estimate("turn-2")).toBe(88);
    expect(cache.size).toBe(2);
  });

  it("ignores invalid measurements", () => {
    const cache = new TimelineRowHeightCache();
    cache.set("zero", 0);
    cache.set("negative", -10);
    cache.set("nan", Number.NaN);
    cache.set("infinite", Number.POSITIVE_INFINITY);
    expect(cache.size).toBe(0);
  });

  it("evicts the oldest entries once the cap is exceeded", () => {
    const cache = new TimelineRowHeightCache(3);
    cache.set("a", 10);
    cache.set("b", 20);
    cache.set("c", 30);
    // 重复写入会刷新淘汰顺序，`a` 变成最新。
    cache.set("a", 11);
    cache.set("d", 40);
    expect(cache.size).toBe(3);
    expect(cache.get("b")).toBeUndefined();
    expect(cache.get("a")).toBe(11);
  });

  it("clears on session switch", () => {
    const cache = new TimelineRowHeightCache();
    cache.set("turn-1", 200);
    cache.clear();
    expect(cache.size).toBe(0);
    expect(cache.estimate("turn-1")).toBe(DEFAULT_ROW_HEIGHT_ESTIMATE_PX);
  });
});
