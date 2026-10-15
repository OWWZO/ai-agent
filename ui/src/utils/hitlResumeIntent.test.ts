import { beforeEach, describe, expect, it } from "vitest";
import {
  claimHitlResumeDispatch,
  resetHitlResumeDispatch,
  resolveHitlResumeIntent,
} from "./hitlResumeIntent";

describe("resolveHitlResumeIntent", () => {
  it("空 / RESUME_PENDING → 补发 resume", () => {
    expect(resolveHitlResumeIntent("")).toBe("submit");
    expect(resolveHitlResumeIntent(undefined)).toBe("submit");
    expect(resolveHitlResumeIntent("resume_pending")).toBe("submit");
  });

  it("RESUMING → 只挂观察，禁止重发", () => {
    expect(resolveHitlResumeIntent("RESUMING")).toBe("observe");
  });

  it("终态 → 什么都不做", () => {
    expect(resolveHitlResumeIntent("ANSWERED")).toBe("none");
    expect(resolveHitlResumeIntent("CANCELLED")).toBe("none");
    expect(resolveHitlResumeIntent("TIMEOUT")).toBe("none");
  });
});

describe("claimHitlResumeDispatch", () => {
  beforeEach(() => {
    resetHitlResumeDispatch();
  });

  it("同一个 resumeRequestId 在窗口内只放行一次", () => {
    expect(claimHitlResumeDispatch("resume_1")).toBe(true);
    expect(claimHitlResumeDispatch("resume_1")).toBe(false);
    expect(claimHitlResumeDispatch("resume_2")).toBe(true);
  });

  it("空 key 不做去重", () => {
    expect(claimHitlResumeDispatch("")).toBe(true);
    expect(claimHitlResumeDispatch("")).toBe(true);
  });
});
