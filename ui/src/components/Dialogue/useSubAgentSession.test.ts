import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { subscribeSubAgentSession } from "./useSubAgentSession";

const mockGetSessionDetail = vi.fn();
const mockGetRunReplay = vi.fn();
const mockQuerySSE = vi.fn();

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

vi.mock("@/utils/querySSE", () => ({default: (...args: unknown[]) => mockQuerySSE(...args),}));

type Patch = Parameters<Parameters<typeof subscribeSubAgentSession>[2]>[0];

type SseConfig = {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
  retryOnError?: boolean;
  handleMessage: (frame: unknown) => void;
  handleError: (error: Error) => void;
  handleClose: () => void;
};

function collect() {
  const patches: Patch[] = [];
  const push = (patch: Patch) => patches.push(patch);
  return {
    patches,
    push
  };
}

/** 取本次订阅传给 querySSE 的配置与 URL */
function lastSseCall() {
  const call = mockQuerySSE.mock.calls[mockQuerySSE.mock.calls.length - 1];
  return {
    config: call?.[0] as SseConfig,
    url: String(call?.[1])
  };
}

/**
 * 建流已改为「history 落地之后才决定」：先等流建立再取配置，
 * 否则拿到的是 undefined。这是契约本身的一部分，不要在测试里绕过。
 */
async function waitForStream() {
  await vi.waitFor(() => expect(mockQuerySSE).toHaveBeenCalledTimes(1));
  return lastSseCall();
}

function runningHistory() {
  return {
    sessionId: "sub_agent-1",
    latestRequestId: "req-1",
    runs: [{
      requestId: "req-1",
      status: "RUNNING",
      hasReplay: true
    }],
  };
}

function terminalHistory() {
  return {
    sessionId: "sub_agent-1",
    latestRequestId: "req-1",
    runs: [{
      requestId: "req-1",
      status: "SUCCESS",
      hasReplay: true
    }],
  };
}

describe("subscribeSubAgentSession", () => {
  beforeEach(() => {
    mockGetSessionDetail.mockReset();
    mockGetRunReplay.mockReset();
    mockQuerySSE.mockReset();
    mockGetSessionDetail.mockResolvedValue({
      sessionId: "sub_agent-1",
      runs: []
    });
    mockGetRunReplay.mockResolvedValue({
      requestId: "req-1",
      replayFrames: []
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("enabled=false 时既不建流也不拉 history", () => {
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", false, push);

    expect(patches).toHaveLength(1);
    expect(patches[0]).toMatchObject({
      ready: false,
      source: "idle"
    });
    expect(mockQuerySSE).not.toHaveBeenCalled();
    expect(mockGetSessionDetail).not.toHaveBeenCalled();
  });

  it("childSessionId 为空时同样不发起任何请求", () => {
    const { patches, push } = collect();

    subscribeSubAgentSession("", true, push);

    expect(patches).toHaveLength(1);
    expect(mockQuerySSE).not.toHaveBeenCalled();
    expect(mockGetSessionDetail).not.toHaveBeenCalled();
  });

  it("history 未落地前不建流，避免白建一条注定立刻 follow_idle 的连接", () => {
    const { push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    // 建流被显式排在 history 之后（promise 回调只在微任务里跑，此处必然还没建）
    expect(mockQuerySSE).not.toHaveBeenCalled();
  });

  it("enabled 时按 GET 订阅子会话流", async () => {
    const { push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    const { config, url } = await waitForStream();
    expect(url).toContain("/api/agent/session/sub_agent-1/stream");
    expect(config.method).toBe("GET");
    expect(config.signal).toBeInstanceOf(AbortSignal);
    expect(mockGetSessionDetail).toHaveBeenCalledTimes(1);
  });

  it("子会话流必须走带鉴权的 SSE 客户端，不得直连 fetch", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);
    await waitForStream();

    // 裸 fetch 不带 Authorization，会被后端 AuthenticationFilter 挡成 401
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(mockQuerySSE).toHaveBeenCalledTimes(1);
  });

  it("实时帧按序推送，控制帧不进轨迹缓冲", async () => {
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);
    const { config } = await waitForStream();

    config.handleMessage({ packageType: "heartbeat" });
    config.handleMessage({ packageType: "follow_pending" });
    config.handleMessage({
      packageType: "result",
      resultMap: { eventData: {} }
    });

    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.source === "live")).toBe(true);
    });
    const livePatches = patches.filter((patch) => patch.source === "live");
    expect(livePatches).toHaveLength(1);
    expect(livePatches[0]?.frames?.[0]).toMatchObject({ packageType: "result" });
  });

  it("终态 run 走账本 replay，且根本不建实时流", async () => {
    mockGetSessionDetail.mockResolvedValue(terminalHistory());
    mockGetRunReplay.mockResolvedValue({
      requestId: "req-1",
      status: "SUCCESS",
      replayFrames: [{
        reqId: "req-1",
        resultMap: { eventData: {} }
      }],
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
    // 终态 run 的实时流只会立刻 follow_idle：这条连接是纯浪费，必须不建
    expect(mockQuerySSE).not.toHaveBeenCalled();
  });

  it("运行中 run 拿到实时帧后不拉 replay，避免与回放重复", async () => {
    mockGetSessionDetail.mockResolvedValue(runningHistory());
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);
    const { config } = await waitForStream();
    config.handleMessage({
      packageType: "result",
      resultMap: { eventData: {} }
    });

    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.source === "live")).toBe(true);
    });
    expect(mockGetRunReplay).not.toHaveBeenCalled();
  });

  it("follow_idle 且实时帧为空时补拉账本回放", async () => {
    mockGetSessionDetail.mockResolvedValue(runningHistory());
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);
    const { config } = await waitForStream();
    config.handleMessage({
      packageType: "follow_idle",
      finished: true
    });

    await vi.waitFor(() => expect(mockGetRunReplay).toHaveBeenCalledWith("req-1"));
    expect(patches.some((patch) => patch.source === "live")).toBe(false);
  });

  it("返回的 abort 句柄会中断订阅", async () => {
    const { push } = collect();

    const abort = subscribeSubAgentSession("sub_agent-1", true, push);
    const { config } = await waitForStream();
    abort();

    expect(config.signal?.aborted).toBe(true);
  });

  it("history 失败不阻断流订阅", async () => {
    mockGetSessionDetail.mockRejectedValue(new Error("会话不存在"));
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);

    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.error?.includes("会话不存在"))).toBe(true);
    });
    // 拿不到 run 状态时不能连流都放弃，否则面板彻底空白
    expect(mockQuerySSE).toHaveBeenCalledTimes(1);
  });

  it("流异常时上报错误且不抛", async () => {
    const { patches, push } = collect();

    subscribeSubAgentSession("sub_agent-1", true, push);
    const { config } = await waitForStream();
    config.handleError(new Error("SSE 请求失败，状态码: 500"));

    await vi.waitFor(() => {
      expect(patches.some((patch) => patch.error?.includes("500"))).toBe(true);
    });
  });

  it("abort 后的收流回调不再上报错误", async () => {
    const { patches, push } = collect();

    const abort = subscribeSubAgentSession("sub_agent-1", true, push);
    const { config } = await waitForStream();
    abort();
    config.handleClose();
    config.handleError(new Error("boom"));

    expect(patches.some((patch) => patch.error?.includes("boom"))).toBe(false);
  });
});
