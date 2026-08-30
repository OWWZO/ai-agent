import { describe, expect, it } from "vitest";

import {
  isPlanSolveConversation,
  isStructuredConversation,
} from "./agentMode";

describe("agentMode", () => {
  it("识别 PlanSolve 枚举值", () => {
    expect(isPlanSolveConversation(3, false)).toBe(true);
    expect(isPlanSolveConversation(5, false)).toBe(false);
    expect(isPlanSolveConversation(undefined, true)).toBe(true);
  });

  it("结构化总结判定只接受 PlanSolve", () => {
    expect(isStructuredConversation(3, false)).toBe(true);
    expect(isStructuredConversation(5, false)).toBe(false);
    expect(isStructuredConversation(undefined, true)).toBe(true);
  });
});
