import { beforeEach, describe, expect, it, vi } from "vitest";

const { getDeviceIdMock } = vi.hoisted(() => ({
  getDeviceIdMock: vi.fn(() => "device-1"),
}));

vi.mock("@/services/agentConversation", () => ({
  getDeviceId: getDeviceIdMock,
}));

vi.mock("@/utils/origin", () => ({
  resolveServiceBaseUrl: (url: string) => url || "http://localhost",
}));

import {
  AgentQuerySubmitError,
  buildAgentSessionStreamUrl,
  submitAcceptedCommand,
  submitAgentQuery,
} from "./agentRun";

describe("agentRun submit/observe", () => {
  beforeEach(() => {
    vi.stubGlobal("SERVICE_BASE_URL", "http://localhost");
  });

  it("session stream URL 只带 sessionId 和 lastEventSeq", () => {
    expect(
      buildAgentSessionStreamUrl({ sessionId: "sess-1", lastEventSeq: 9 })
    ).toContain("/api/agent/session/sess-1/stream?lastEventSeq=9");
  });

  it("submit 409 不当成 SSE 断连", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        status: 409,
        ok: false,
        json: async () => ({
          code: "0001",
          info: "已有任务在进行中，请等待完成或先停止后再试",
          data: { accepted: false },
        }),
      })
    );

    await expect(submitAgentQuery({ requestId: "r1" })).rejects.toMatchObject({
      name: "AgentQuerySubmitError",
      status: 409,
      concurrent: true,
    } satisfies Partial<AgentQuerySubmitError>);
  });

  it("HITL resume 409 不当成 SSE 断连", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        status: 409,
        ok: false,
        json: async () => ({
          info: "续跑已在进行中",
          data: { accepted: false },
        }),
      })
    );

    await expect(
      submitAcceptedCommand("http://localhost/api/agent/ask-user/resume", {
        resumeRequestId: "r-resume",
      })
    ).rejects.toMatchObject({
      name: "AgentQuerySubmitError",
      status: 409,
      concurrent: true,
    } satisfies Partial<AgentQuerySubmitError>);
  });
});
