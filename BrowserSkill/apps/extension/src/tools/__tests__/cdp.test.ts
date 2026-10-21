import { describe, expect, it, vi } from "vitest";
import { SessionManager } from "@/session-manager/manager";
import { CDP_ALLOWLIST, handleCdp } from "../cdp";
import type { CdpRunner, ChromeTabsApi } from "../shared";

function fakeAgentWindow(ids: number[]) {
  let i = 0;
  return {
    create: vi.fn(async () => {
      const id = ids[i++];
      if (id === undefined) throw new Error("ran out of fake ids");
      return { windowId: id, initialTabIds: [] };
    }),
    remove: vi.fn(async () => {}),
    ensureActiveTab: vi.fn(async () => 1),
  };
}

async function makeManager(): Promise<SessionManager> {
  const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
  await sm.start("aa11");
  return sm;
}

function fakeTabs(windowId = 100): ChromeTabsApi {
  const tab = { id: 4, windowId, active: true } as chrome.tabs.Tab;
  return { get: vi.fn(async () => tab), query: vi.fn(async () => [tab]) };
}

function fakeCdp(opts: { throw?: boolean; reply?: unknown } = {}) {
  const calls: Array<{ tabId: number; method: string; params?: object }> = [];
  const cdp: CdpRunner = {
    send: vi.fn(async (tabId: number, method: string, params?: object) => {
      calls.push({ tabId, method, params });
      if (opts.throw) throw new Error("simulated cdp failure");
      return opts.reply ?? { ok: true };
    }) as unknown as CdpRunner["send"],
  };
  return { cdp, calls };
}

describe("handleCdp", () => {
  it("dispatches an allowlisted method and echoes it back", async () => {
    const sm = await makeManager();
    const { cdp, calls } = fakeCdp({ reply: { contentSize: { width: 1280 } } });
    const result = await handleCdp(
      sm,
      { session_id: "aa11", method: "Page.getLayoutMetrics", params: { a: 1 } },
      { cdp, tabsApi: fakeTabs() },
    );

    expect(calls).toEqual([{ tabId: 4, method: "Page.getLayoutMetrics", params: { a: 1 } }]);
    expect(result).toEqual({
      tab_id: 4,
      method: "Page.getLayoutMetrics",
      result: { contentSize: { width: 1280 } },
    });
  });

  it("rejects a method outside the allowlist before touching CDP", async () => {
    const sm = await makeManager();
    const { cdp, calls } = fakeCdp();
    const result = await handleCdp(
      sm,
      { session_id: "aa11", method: "Network.setCookie" },
      { cdp, tabsApi: fakeTabs() },
    );

    expect(result).toMatchObject({ code: "permission_denied" });
    expect(calls).toEqual([]);
  });

  it("does not allow Runtime.evaluate — that path is tool.evaluate", () => {
    // The allowlist is the safety boundary; keeping this out means the
    // sandbox checks in tool.evaluate cannot be bypassed here.
    expect(CDP_ALLOWLIST.has("Runtime.evaluate")).toBe(false);
  });

  it("rejects a missing method", async () => {
    const sm = await makeManager();
    const { cdp, calls } = fakeCdp();
    const result = await handleCdp(
      sm,
      { session_id: "aa11" } as unknown as { session_id: string; method: string },
      { cdp, tabsApi: fakeTabs() },
    );

    expect(result).toMatchObject({ code: "invalid_params" });
    expect(calls).toEqual([]);
  });

  it("refuses a tab outside the Agent Window even on a local session", async () => {
    const sm = await makeManager();
    const { cdp, calls } = fakeCdp();
    // `browser_mutation` always resolves through `enforceAgentWindow`, which
    // checks the window unconditionally — only passive reads relax it.
    const result = await handleCdp(
      sm,
      { session_id: "aa11", method: "DOM.getDocument", tab_id: 7 },
      { cdp, tabsApi: fakeTabs(999) },
    );

    expect(result).toMatchObject({ code: "permission_denied" });
    expect(calls).toEqual([]);
  });

  it("also refuses an unborrowed tab on a remote session", async () => {
    const sm = await makeManager();
    // Remote is the mode the Java gateway runs in.
    const session = sm.get("aa11");
    Object.defineProperty(session, "remote", { value: true, configurable: true });
    const { cdp, calls } = fakeCdp();
    const result = await handleCdp(
      sm,
      { session_id: "aa11", method: "DOM.getDocument", tab_id: 7 },
      { cdp, tabsApi: fakeTabs(999) },
    );

    expect(result).toMatchObject({ code: "permission_denied" });
    expect(calls).toEqual([]);
  });

  it("surfaces a CDP failure as cdp_failed", async () => {
    const sm = await makeManager();
    const { cdp } = fakeCdp({ throw: true });
    const result = await handleCdp(
      sm,
      { session_id: "aa11", method: "DOM.getDocument" },
      { cdp, tabsApi: fakeTabs() },
    );
    expect(result).toMatchObject({ code: "cdp_failed" });
  });

  it("rejects an unknown session", async () => {
    const sm = await makeManager();
    const { cdp } = fakeCdp();
    const result = await handleCdp(
      sm,
      { session_id: "zz99", method: "DOM.getDocument" },
      { cdp, tabsApi: fakeTabs() },
    );
    expect(result).toMatchObject({ code: "not_found" });
  });
});
