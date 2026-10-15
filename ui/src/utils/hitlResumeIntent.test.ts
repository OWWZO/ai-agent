import { describe, expect, it } from "vitest";
import { resolveHitlResumeIntent } from "./hitlResumeIntent";

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
