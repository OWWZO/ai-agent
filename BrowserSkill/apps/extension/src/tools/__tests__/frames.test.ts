import { describe, expect, it, vi } from "vitest";
import type { CdpFrameGraph } from "@/browser-driver/frame-graph";
import { SessionManager } from "@/session-manager/manager";
import { handleFrames } from "../frames";
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

function fakeTabs(): ChromeTabsApi {
  const tab = { id: 4, windowId: 100, active: true, url: "https://example.test/" } as chrome.tabs.Tab;
  return { get: vi.fn(async () => tab), query: vi.fn(async () => [tab]) };
}

function fakeCdp(graph: CdpFrameGraph | undefined, opts: { throw?: boolean } = {}) {
  const send = vi.fn(async () => {
    if (opts.throw) throw new Error("simulated cdp failure");
    return { frameTree: { frame: { id: "root", url: "https://example.test/" } } };
  });
  const cdp: CdpRunner = {
    send: send as unknown as CdpRunner["send"],
    ...(graph === undefined ? {} : { getFrameGraph: vi.fn(async () => graph) }),
  };
  return { cdp, send };
}

describe("handleFrames", () => {
  it("flattens the frame graph and marks out-of-process frames", async () => {
    const sm = await makeManager();
    const graph: CdpFrameGraph = {
      rootFrameId: "root",
      frames: [
        { frameId: "root", url: "https://example.test/", target: { tabId: 4 } },
        {
          frameId: "child",
          parentFrameId: "root",
          url: "https://other.test/",
          target: { tabId: 4, sessionId: "sess-1" },
        },
      ],
    };
    const { cdp } = fakeCdp(graph);
    const result = await handleFrames(sm, { session_id: "aa11" }, { cdp, tabsApi: fakeTabs() });

    expect(result).toEqual({
      tab_id: 4,
      root_frame_id: "root",
      frames: [
        { frame_id: "root", url: "https://example.test/", out_of_process: false },
        {
          frame_id: "child",
          parent_frame_id: "root",
          url: "https://other.test/",
          out_of_process: true,
        },
      ],
      unavailable_frames: [],
    });
  });

  it("reports unavailable subtrees instead of hiding them", async () => {
    const sm = await makeManager();
    const graph: CdpFrameGraph = {
      rootFrameId: "root",
      frames: [{ frameId: "root", target: { tabId: 4 } }],
      unavailableFrames: [{ frameId: "blocked", target: { tabId: 4, sessionId: "sess-2" } }],
    };
    const { cdp } = fakeCdp(graph);
    const result = await handleFrames(sm, { session_id: "aa11" }, { cdp, tabsApi: fakeTabs() });

    expect(result).toMatchObject({
      unavailable_frames: [{ frame_id: "blocked", out_of_process: true }],
    });
  });

  it("falls back to the root frame when the driver has no frame graph", async () => {
    const sm = await makeManager();
    const { cdp, send } = fakeCdp(undefined);
    const result = await handleFrames(sm, { session_id: "aa11" }, { cdp, tabsApi: fakeTabs() });

    expect(send).toHaveBeenCalledWith(4, "Page.getFrameTree", {});
    expect(result).toEqual({
      tab_id: 4,
      root_frame_id: "root",
      frames: [{ frame_id: "root", url: "https://example.test/", out_of_process: false }],
      unavailable_frames: [],
    });
  });

  it("surfaces a CDP failure as cdp_failed", async () => {
    const sm = await makeManager();
    const { cdp } = fakeCdp(undefined, { throw: true });
    const result = await handleFrames(sm, { session_id: "aa11" }, { cdp, tabsApi: fakeTabs() });
    expect(result).toMatchObject({ code: "cdp_failed" });
  });

  it("rejects an unknown session", async () => {
    const sm = await makeManager();
    const { cdp } = fakeCdp({ rootFrameId: "root", frames: [] });
    const result = await handleFrames(sm, { session_id: "zz99" }, { cdp, tabsApi: fakeTabs() });
    expect(result).toMatchObject({ code: "not_found" });
  });
});
