import { describe, expect, it, beforeEach } from "vitest";
import {
  applyLocalUiPatches,
  clearLocalUiPatches,
  genUiLocalScopeKey,
  getLocalUiPatches,
  getLocalUiVersion,
} from "./genUiLocalTreeStore";

describe("genUiLocalTreeStore", () => {
  const key = genUiLocalScopeKey("s1", "m1");

  beforeEach(() => {
    clearLocalUiPatches(key);
  });

  it("accumulates local patches per scope", () => {
    applyLocalUiPatches(key, [
      { op: "replace", path: "/root/props/title", value: "A" },
    ]);
    applyLocalUiPatches(key, [
      { op: "replace", path: "/root/props/value", value: 2 },
    ]);
    expect(getLocalUiPatches(key)).toHaveLength(2);
    expect(getLocalUiVersion(key)).toBe(2);
  });
});
