import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { subscribeSubAgentSession } from "./useSubAgentSession";

const mockGetSessionDetail = vi.fn();
const mockGetRunReplay = vi.fn();
const mockFetch = vi.fn();

vi.mock("@/services/agentConversation", () => ({
  conversationHistoryApi: {
    getSessionDetail: (...args: unknown[]) => mockGetSessionDetail(...args),
    getRunReplay: (...args: unknown[]) => mockGetRunReplay(...args),
  },
}));

vi.mock("@/services/agentRun", () => ({
  buildAgentSessionStreamUrl: (params: { sessionId: string; lastEventSeq?: number }) =>
    `/api/agent/session/${encodeURIComponent(params.sessionId)}/stream?lastEventSeq=${
      params.lastEventSeq || 0
    }`,
}));

function makeSseBody(chunks: string[]) {
  const encoder = new TextEncoder();
  let index = 0;
  return {
    getReader() {
      return {
        read: async () => {
          if (index >= chunks.length) {
            return { done: true, value: undefined };
          }
          const value = encoder.encode(chunks[index]);
          index += 1;
          return { done: false, value };
        },
        cancel: async () => undefined,
      };
    },
  } as unknown as ReadableStream<Uint8Array>;
}

type Patch = Parameters<Parameters<typeof subscribeSubAgentSession>[2]>[0];

function collect() {
  const patches: Patch[] = [];
  const push = (patch: Patch) => patches.push(patch);
  return { patches, push };
}

describe("subscribeSubAgentSession", () => {
  beforeEach(() => {
    mockGetSessionDetail.mockReset();
    mockGetRunReplay.mockReset();
    mockFetch.mockReset();
    mockGetSessionDetail.mockResolvedValue({ sessionId: "sub_agent-1", runs: [] });
    mockGetRunReplay.mockResolvedValue({ requestId: "req-1", replayFrames: [] });
    mockFetch.mockResolvedValue({
      ok: true,
      body: makeSseBody([
        'data: {"messageType":"tool_call","resultMap":{}}\n\n',
        'data: {"messageType":"subagent_progress"}\n\n',
      ]),
    });
    vi.stubGlobal("fetch", mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("enabled=false 时既不建流也不拉 history", () => {
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", false, push);

    expect(patches).toHaveLength(1);
    expect(patches[0]).toMatchObject({ ready: false, source: "idle" });
    expect(mockFetch).not.toHaveBeenCalled();
    expect(mockGetSessionDetail).not.toHaveBeenCalled();
  });

  it("childSessionId 为空时同样不发起任何请求", () => {
    const { patches, push } = collect();

    subscribeSubAgentSession("", true, push);

    expect(patches).toHaveLength(1);
    expect(mockFetch).not.toHaveBeenCalled();
    expect(mockGetSessionDetail).not.toHaveBeenCalled();
  });

  it("enabled 时订阅子会话流并按序推送帧", async () => {
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(String(mockFetch.mock.calls[0]?.[0])).toContain(
      "/api/agent/session/sub_agent-1/stream"
    );
    expect(mockGetSessionDetail).toHaveBeenCalledTimes(1);

    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.source === "live")).toBe(true);
    });

    const livePatches = patches.filter((patch) => patch.source === "live");
    expect(livePatches).toHaveLength(2);
    expect(livePatches[0]?.frames?.[0]).toMatchObject({ messageType: "tool_call" });
    expect(livePatches[1]?.frames?.[0]).toMatchObject({ messageType: "subagent_progress" });
  });

  it("返回的 abort 句柄会中断订阅", async () => {
    const { push } = collect();

    const abort = subscribeSubAgentSession("sub_agent-1", true, push);
    await vi.waitFor(() => expect(mockFetch).toHaveBeenCalled());
    abort();

    expect(mockFetch.mock.calls[0]?.[1]?.signal?.aborted).toBe(true);
  });

  it("history 失败不阻断流订阅", async () => {
    mockGetSessionDetail.mockRejectedValue(new Error("会话不存在"));
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.error?.includes("会话不存在"))).toBe(true);
    });
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it("流响应异常时上报错误且不抛", async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 403, body: null });
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.error?.includes("403"))).toBe(true);
    });
  });

  it("终态 run 走账本 replay，不依赖实时帧", async () => {
    mockGetSessionDetail.mockResolvedValue({
      sessionId: "sub_agent-1",
      latestRequestId: "req-1",
      runs: [{ requestId: "req-1", status: "SUCCESS", hasReplay: true }],
    });
    mockGetRunReplay.mockResolvedValue({
      requestId: "req-1",
      status: "SUCCESS",
      replayFrames: [{ reqId: "req-1", resultMap: { eventData: {} } }],
    });
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    await vi.waitFor(() => expect(mockGetRunReplay).toHaveBeenCalledWith("req-1"));
    expect(
      patches.some(
        (patch) =>
          patch.replay &&
          (patch.replay as { replayFrames: unknown[] }).replayFrames.length === 1
      )
    ).toBe(true);
  });

  it("运行中 run 不拉 replay，避免与实时回放重复", async () => {
    mockGetSessionDetail.mockResolvedValue({
      sessionId: "sub_agent-1",
      latestRequestId: "req-1",
      runs: [{ requestId: "req-1", status: "RUNNING", hasReplay: true }],
    });
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    // 等实时帧确实到达（SSE 已给出轨迹），此时不应再补拉账本回放
    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.source === "live")).toBe(true);
    });
    expect(mockGetRunReplay).not.toHaveBeenCalled();
  });

  it("follow_idle 且实时帧为空时补拉账本回放", async () => {
    mockGetSessionDetail.mockResolvedValue({
      sessionId: "sub_agent-1",
      latestRequestId: "req-1",
      runs: [{ requestId: "req-1", status: "RUNNING", hasReplay: true }],
    });
    mockFetch.mockResolvedValue({
      ok: true,
      body: makeSseBody(['data: {"packageType":"follow_idle","finished":false}\n\n']),
    });
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    await vi.waitFor(() => expect(mockGetRunReplay).toHaveBeenCalledWith("req-1"));
    expect(patches.some((patch) => patch.source === "live")).toBe(false);
  });

  it("控制帧不进轨迹缓冲", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      body: makeSseBody([
        'data: {"packageType":"heartbeat"}\n\n',
        'data: {"messageType":"tool_call","resultMap":{}}\n\n',
      ]),
    });
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.source === "live")).toBe(true);
    });
    const livePatches = patches.filter((patch) => patch.source === "live");
    expect(livePatches).toHaveLength(1);
    expect(livePatches[0]?.frames?.[0]).toMatchObject({ messageType: "tool_call" });
  });
});
