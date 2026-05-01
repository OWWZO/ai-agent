import { describe, expect, it } from "vitest";

import {
  isPlanSolveConversation,
  isStructuredConversation,
} from "./agentMode";

describe("agentMode", () => {
  it("兼容后端真实的 PlanSolve 枚举值", () => {
    expect(isPlanSolveConversation(3, false)).toBe(true);
    expect(isPlanSolveConversation(1, false)).toBe(true);
    expect(isPlanSolveConversation(5, false)).toBe(false);
    expect(isPlanSolveConversation(0, false)).toBe(false);
  });

  it("结构化总结判定同时兼容前后端枚举", () => {
    expect(isStructuredConversation(3, false)).toBe(true);
    expect(isStructuredConversation(5, false)).toBe(true);
    expect(isStructuredConversation(1, false)).toBe(true);
    expect(isStructuredConversation(2, false)).toBe(true);
    expect(isStructuredConversation(0, false)).toBe(false);
    expect(isStructuredConversation(undefined, true)).toBe(true);
  });
});
