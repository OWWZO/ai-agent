import { beforeEach, describe, expect, it, vi } from "vitest";

const { getDeviceIdMock, getAccessTokenMock, refreshAccessTokenMock } = vi.hoisted(() => ({
  getDeviceIdMock: vi.fn(() => "device-1"),
  getAccessTokenMock: vi.fn<() => string | null>(() => "access-token-1"),
  refreshAccessTokenMock: vi.fn(),
}));

vi.mock("@/services/agentConversation", () => ({
  getDeviceId: getDeviceIdMock,
}));

vi.mock("@/utils/origin", () => ({
  resolveServiceBaseUrl: (url: string) => url || "http://localhost",
}));

vi.mock("@/stores/auth", () => ({
  getAccessToken: getAccessTokenMock,
  clearAuthSession: vi.fn(),
}));

vi.mock("@/services/authTransport", () => ({
  refreshAccessToken: refreshAccessTokenMock,
}));

vi.mock("@/services/authSessionExpired", () => ({
  emitAuthSessionExpired: vi.fn(),
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
    getAccessTokenMock.mockReturnValue("access-token-1");
    refreshAccessTokenMock.mockReset();
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

  it("提交 Agent 指令时携带 access token", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      status: 200,
      ok: true,
      json: async () => ({
        code: "0000",
        data: {
          accepted: true,
          sessionId: "s1",
          requestId: "r1"
        },
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await submitAgentQuery({ requestId: "r1" });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer access-token-1",
        }),
      })
    );
  });

  it("提交返回 401 时刷新 token 后只重试一次", async () => {
    getAccessTokenMock.mockReturnValue(null);
    refreshAccessTokenMock.mockResolvedValue("access-token-2");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        status: 401,
        ok: false
      })
      .mockResolvedValueOnce({
        status: 200,
        ok: true,
        json: async () => ({
          code: "0000",
          data: {
            accepted: true,
            sessionId: "s1",
            requestId: "r1"
          },
        }),
      });
    vi.stubGlobal("fetch", fetchMock);

    await submitAgentQuery({ requestId: "r1" });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][1]).toEqual(
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer access-token-2",
        }),
      })
    );
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
