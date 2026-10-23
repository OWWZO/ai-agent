import { describe, expect, it, vi } from "vitest";
import { SessionManager } from "@/session-manager/manager";
import type { CdpRunner } from "@/tools/shared";
import { handleEvaluate } from "../evaluate";

function fakeAgentWindow(ids: number[]) {
  let i = 0;
  return {
    create: vi.fn(async () => {
      const id = ids[i++];
      if (id === undefined) throw new Error("ran out of fake ids");
      return { windowId: id, initialTabIds: [] };
    }),
    remove: vi.fn(async () => {}),
    ensureActiveTab: vi.fn(async () => 4),
  };
}

interface FakeCdpOptions {
  windowId?: number;
  evaluate?: (params: unknown) => unknown;
  onDialogOpening?: () => void;
  dialogBlocking?: boolean;
}

function makeFakeCdp(opts: FakeCdpOptions = {}) {
  const sent: Array<{ tabId: number; method: string; params?: object }> = [];
  let dialogBlocking = opts.dialogBlocking ?? false;
  const dialogRecords: Array<{
    tab_id: number;
    type: "alert";
    message: string;
    handled: "accepted";
    sequence: number;
  }> = [];
  let sequence = 0;
  const sendImpl = async (tabId: number, method: string, params?: object) => {
    sent.push({ tabId, method, params });
    if (method === "Runtime.evaluate") {
      if (dialogBlocking) {
        opts.onDialogOpening?.();
        dialogBlocking = false;
        sequence += 1;
        dialogRecords.push({
          tab_id: tabId,
          type: "alert",
          message: "blocked",
          handled: "accepted",
          sequence,
        });
        if (opts.evaluate) return opts.evaluate(params);
        return { result: { type: "number", value: 2 } };
      }
      if (opts.evaluate) return opts.evaluate(params);
      return { result: { type: "number", value: 2 } };
    }
    throw new Error(`unexpected CDP call ${method}`);
  };
  const send = vi.fn(sendImpl);
  const sendToTarget = vi.fn(sendImpl);
  const cdp: CdpRunner = {
    send: send as unknown as <T = unknown>(
      tabId: number,
      method: string,
      params?: object,
    ) => Promise<T>,
    sendToTarget: sendToTarget as unknown as <T = unknown>(
      target: { tabId: number; sessionId?: string },
      method: string,
      params?: object,
    ) => Promise<T>,
    trackSessionTab: vi.fn(),
    dialogCursor: vi.fn(() => 0),
    dialogsSince: vi.fn((_tabId: number, cursor: number) =>
      dialogRecords.filter((d) => d.sequence > cursor),
    ),
  };
  const windowId = opts.windowId ?? 100;
  const tabsApi = {
    get: vi.fn(async (tabId: number) => ({ id: tabId, windowId, active: true }) as chrome.tabs.Tab),
    query: vi.fn(async () => [{ id: 4, windowId, active: true } as chrome.tabs.Tab]),
  };
  return { cdp, tabsApi, sent, sendToTarget };
}

describe("handleEvaluate", () => {
  it("rejects empty expression as invalid_params", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const fake = makeFakeCdp();
    const res = await handleEvaluate(
      sm,
      { session_id: "aa11", expression: "" },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    expect(res).toMatchObject({ code: "invalid_params" });
  });

  it("evaluates 1+1 and returns ok with value 2", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const fake = makeFakeCdp({
      evaluate: () => ({ result: { type: "number", value: 2 } }),
    });
    const res = await handleEvaluate(
      sm,
      { session_id: "aa11", expression: "1+1" },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
    expect(res.ok).toBe(true);
    expect(res.value).toBe(2);
    expect(res.tab_id).toBe(4);
    expect(res.error).toBeUndefined();
  });

  it("forwards await_promise/return_by_value with their defaults filled in", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    let observed: Record<string, unknown> | undefined;
    const fake = makeFakeCdp({
      evaluate: (params) => {
        observed = params as Record<string, unknown>;
        return { result: { type: "string", value: "ok" } };
      },
    });
    await handleEvaluate(
      sm,
      { session_id: "aa11", expression: "Promise.resolve('ok')" },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    expect(observed).toMatchObject({
      expression: "Promise.resolve('ok')",
      awaitPromise: true,
      returnByValue: true,
      throwOnSideEffect: false,
    });
  });

  it("respects explicit await_promise=false and return_by_value=false overrides", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    let observed: Record<string, unknown> | undefined;
    const fake = makeFakeCdp({
      evaluate: (params) => {
        observed = params as Record<string, unknown>;
        return { result: { type: "string", value: "kept" } };
      },
    });
    await handleEvaluate(
      sm,
      {
        session_id: "aa11",
        expression: "'kept'",
        await_promise: false,
        return_by_value: false,
      },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    expect(observed).toMatchObject({
      awaitPromise: false,
      returnByValue: false,
    });
  });

  it("returns a JSON-safe RemoteObject descriptor when return_by_value=false", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const fake = makeFakeCdp({
      evaluate: () => ({
        result: {
          type: "object",
          subtype: "promise",
          className: "Promise",
          objectId: "remote-object-1",
          description: "Promise",
        },
      }),
    });
    const res = await handleEvaluate(
      sm,
      {
        session_id: "aa11",
        expression: "Promise.resolve(1)",
        await_promise: false,
        return_by_value: false,
      },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
    expect(res.ok).toBe(true);
    expect(res.value).toEqual({
      type: "object",
      subtype: "promise",
      className: "Promise",
      objectId: "remote-object-1",
      description: "Promise",
    });
  });

  it("returns ok=false with error text when the expression throws", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const fake = makeFakeCdp({
      evaluate: () => ({
        result: { type: "object", subtype: "error" },
        exceptionDetails: {
          text: "Uncaught Error",
          lineNumber: 2,
          columnNumber: 8,
          exception: {
            description: "Error: boom\n    at <anonymous>:1:7",
          },
        },
      }),
    });
    const res = await handleEvaluate(
      sm,
      { session_id: "aa11", expression: 'throw new Error("boom")' },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
    expect(res.ok).toBe(false);
    expect(res.value).toBeUndefined();
    expect(res.error?.text).toContain("boom");
    expect(res.error?.line).toBe(3);
    expect(res.error?.column).toBe(8);
  });

  it("stringifies unserializableValue (Infinity / NaN / BigInt)", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const fake = makeFakeCdp({
      evaluate: () => ({
        result: { type: "number", unserializableValue: "Infinity" },
      }),
    });
    const res = await handleEvaluate(
      sm,
      { session_id: "aa11", expression: "1/0" },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
    expect(res.ok).toBe(true);
    expect(res.value).toBe("Infinity");
  });

  it("allows evaluating against a borrowed tab inside the Agent Window", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    const ctx = await sm.start("aa11");
    ctx.borrowedTabs.set(7, { tabId: 7, originalWindowId: 200, originalIndex: 0 });
    const fake = makeFakeCdp();
    fake.tabsApi.get = vi.fn(
      async () => ({ id: 7, windowId: 100, active: true }) as chrome.tabs.Tab,
    );
    const res = await handleEvaluate(
      sm,
      { session_id: "aa11", expression: "1+1", tab_id: 7 },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
    expect(res.ok).toBe(true);
  });

  describe("frame targeting", () => {
    /** Frame graph with one same-process child and one out-of-process child. */
    function frameGraph(windowId: number) {
      return {
        rootFrameId: "root",
        frames: [
          { frameId: "root", target: { tabId: 4, windowId } },
          { frameId: "child", parentFrameId: "root", target: { tabId: 4, windowId } },
          {
            frameId: "oopif",
            parentFrameId: "root",
            target: { tabId: 4, sessionId: "session-1", windowId },
          },
        ],
      };
    }

    function withFrameGraph(fake: ReturnType<typeof makeFakeCdp>, windowId = 100) {
      fake.cdp.getFrameGraph = vi.fn(async () => frameGraph(windowId));
      return fake;
    }

    it("evaluates in a same-process child frame", async () => {
      const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
      await sm.start("aa11");
      const fake = withFrameGraph(makeFakeCdp());
      const res = await handleEvaluate(
        sm,
        { session_id: "aa11", expression: "1+1", frame_id: "child" },
        { cdp: fake.cdp, tabsApi: fake.tabsApi },
      );
      if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
      expect(res.ok).toBe(true);
      expect(res.frame_id).toBe("child");
      expect(fake.cdp.send).toHaveBeenCalledWith(4, "Runtime.evaluate", expect.any(Object));
      expect(fake.cdp.sendToTarget).not.toHaveBeenCalled();
    });

    it("routes an out-of-process frame through its own CDP session", async () => {
      const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
      await sm.start("aa11");
      const fake = withFrameGraph(makeFakeCdp());
      const res = await handleEvaluate(
        sm,
        { session_id: "aa11", expression: "1+1", frame_id: "oopif" },
        { cdp: fake.cdp, tabsApi: fake.tabsApi },
      );
      if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
      expect(res.ok).toBe(true);
      expect(res.frame_id).toBe("oopif");
      expect(fake.cdp.sendToTarget).toHaveBeenCalledWith(
        { tabId: 4, sessionId: "session-1" },
        "Runtime.evaluate",
        expect.any(Object),
      );
    });

    it("omits frame_id from the result for the main frame", async () => {
      const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
      await sm.start("aa11");
      const fake = withFrameGraph(makeFakeCdp());
      const res = await handleEvaluate(
        sm,
        { session_id: "aa11", expression: "1+1" },
        { cdp: fake.cdp, tabsApi: fake.tabsApi },
      );
      if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
      expect(res).not.toHaveProperty("frame_id");
      // The main frame never needs the frame graph.
      expect(fake.cdp.getFrameGraph).not.toHaveBeenCalled();
    });

    it("reports not_found for a frame that left the graph", async () => {
      const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
      await sm.start("aa11");
      const fake = withFrameGraph(makeFakeCdp());
      const res = await handleEvaluate(
        sm,
        { session_id: "aa11", expression: "1+1", frame_id: "gone" },
        { cdp: fake.cdp, tabsApi: fake.tabsApi },
      );
      expect(res).toMatchObject({ code: "not_found" });
      expect(fake.cdp.send).not.toHaveBeenCalled();
    });

    it("rejects an empty frame_id instead of falling back to the main frame", async () => {
      const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
      await sm.start("aa11");
      const fake = withFrameGraph(makeFakeCdp());
      const res = await handleEvaluate(
        sm,
        { session_id: "aa11", expression: "1+1", frame_id: "" },
        { cdp: fake.cdp, tabsApi: fake.tabsApi },
      );
      expect(res).toMatchObject({ code: "invalid_params" });
      expect(fake.cdp.send).not.toHaveBeenCalled();
    });

    it("still refuses a frame in a tab outside the Agent Window", async () => {
      const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
      await sm.start("aa11");
      const fake = withFrameGraph(makeFakeCdp());
      // Tab 9 lives in a user window and was never borrowed.
      fake.tabsApi.get = vi.fn(
        async () => ({ id: 9, windowId: 300, active: true }) as chrome.tabs.Tab,
      );
      const res = await handleEvaluate(
        sm,
        { session_id: "aa11", expression: "1+1", tab_id: 9, frame_id: "child" },
        { cdp: fake.cdp, tabsApi: fake.tabsApi },
      );
      expect(res).toMatchObject({ code: "permission_denied" });
      expect(fake.cdp.send).not.toHaveBeenCalled();
      // The sandbox check runs before frame resolution.
      expect(fake.cdp.getFrameGraph).not.toHaveBeenCalled();
    });
  });

  it("returns dialogs observed during evaluate", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const fake = makeFakeCdp({ dialogBlocking: true });
    const res = await handleEvaluate(
      sm,
      { session_id: "aa11", expression: "alert('hi'); 1+1" },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
    expect(res.ok).toBe(true);
    expect(res.dialogs).toHaveLength(1);
    expect(res.dialogs?.[0]?.message).toBe("blocked");
    expect(res.dialogs?.[0]?.type).toBe("alert");
  });

  it("rejects tabs outside the Agent Window with permission_denied", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const fake = makeFakeCdp();
    fake.tabsApi.get = vi.fn(
      async () => ({ id: 11, windowId: 200, active: true }) as chrome.tabs.Tab,
    );
    const res = await handleEvaluate(
      sm,
      { session_id: "aa11", expression: "1+1", tab_id: 11 },
      { cdp: fake.cdp, tabsApi: fake.tabsApi },
    );
    expect(res).toMatchObject({
      code: "permission_denied",
      data: { reason: "agent_window_scope" },
    });
  });
});
