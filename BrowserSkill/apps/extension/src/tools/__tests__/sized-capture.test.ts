import { describe, expect, it, vi } from "vitest";
import { SessionManager } from "@/session-manager/manager";
import { handleEmulate, type EmulateCdpRunner, type EmulateDeps } from "../emulate";
import { handleScreenshot, type ScreenshotDeps } from "../observation";
import {
  MAX_CAPTURE_DIMENSION,
  MIN_CAPTURE_DIMENSION,
  validateCaptureSize,
  withTemporaryViewport,
} from "../sized-capture";
import type { CdpRunner } from "../shared";

// 1x1 transparent PNG, base64-encoded. Width 1, height 1.
const TINY_PNG =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGBgAAAABQABh6FO1AAAAABJRU5ErkJggg==";

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

function makeDeps() {
  const sent: Array<{ method: string; params?: object }> = [];
  const cdp = {
    send: vi.fn(async (_tabId: number, method: string, params?: object) => {
      sent.push({ method, params });
      switch (method) {
        case "Emulation.setDeviceMetricsOverride":
        case "Emulation.clearDeviceMetricsOverride":
          return {};
        case "Page.getFrameTree":
          return { frameTree: { frame: { id: "main" } } };
        case "Page.createIsolatedWorld":
          return { executionContextId: 1 };
        case "Runtime.evaluate":
          return {
            result: { deepSerializedValue: { type: "node", value: { backendNodeId: 42 } } },
          };
        case "Runtime.releaseObjectGroup":
          return {};
        case "Page.captureScreenshot":
          return { data: TINY_PNG };
        default:
          throw new Error(`unexpected CDP call ${method}`);
      }
    }),
    getAttachmentId: vi.fn(() => "attachment"),
    trackSessionTab: vi.fn(),
  } as unknown as CdpRunner;
  const deps: ScreenshotDeps = {
    cdp,
    tabsApi: {
      get: vi.fn(async (tabId: number) => ({ id: tabId, windowId: 100, active: true }) as never),
      query: vi.fn(async () => [{ id: 4, windowId: 100, active: true }] as never),
    },
    // Never used by a sized capture: the size forces the CDP path.
    captureApi: {
      captureVisibleTab: vi.fn(async () => `data:image/png;base64,${TINY_PNG}`),
    } as unknown as ScreenshotDeps["captureApi"],
  };
  return { deps, cdp, sent };
}

function metricsCalls(sent: Array<{ method: string; params?: object }>) {
  return sent
    .filter((call) => call.method.startsWith("Emulation."))
    .map((call) => ({ method: call.method, params: call.params }));
}

describe("validateCaptureSize", () => {
  it("accepts nothing or a matched pair", () => {
    expect(validateCaptureSize(undefined, undefined)).toBeUndefined();
    expect(validateCaptureSize(390, 844)).toEqual({ width: 390, height: 844 });
  });

  it("requires both dimensions", () => {
    expect(validateCaptureSize(390, undefined)).toMatchObject({ code: "invalid_params" });
    expect(validateCaptureSize(undefined, 844)).toMatchObject({ code: "invalid_params" });
  });

  it("rejects non-positive, fractional and out-of-range sizes", () => {
    expect(validateCaptureSize(0, 844)).toMatchObject({ code: "invalid_params" });
    expect(validateCaptureSize(-1, 844)).toMatchObject({ code: "invalid_params" });
    expect(validateCaptureSize(390.5, 844)).toMatchObject({ code: "invalid_params" });
    expect(validateCaptureSize(MAX_CAPTURE_DIMENSION + 1, 844)).toMatchObject({
      code: "invalid_params",
    });
    expect(validateCaptureSize(MIN_CAPTURE_DIMENSION, 844)).toEqual({
      width: MIN_CAPTURE_DIMENSION,
      height: 844,
    });
  });
});

describe("screenshot with an explicit size", () => {
  it("pins the viewport, captures, then clears the override", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const { deps, cdp, sent } = makeDeps();
    const res = await handleScreenshot(sm, { session_id: "aa11", width: 390, height: 844 }, deps);
    if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
    expect(res.image_base64).toBe(TINY_PNG);
    expect(metricsCalls(sent)).toEqual([
      {
        method: "Emulation.setDeviceMetricsOverride",
        params: { width: 390, height: 844, deviceScaleFactor: 1, mobile: false },
      },
      { method: "Emulation.clearDeviceMetricsOverride", params: undefined },
    ]);
    // The override is always released, never left on the tab.
    expect(deps.captureApi.captureVisibleTab).not.toHaveBeenCalled();
    expect(cdp.send).toHaveBeenCalledWith(4, "Page.captureScreenshot", expect.any(Object));
  });

  it("restores a live emulate profile instead of clearing it", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const { deps, sent } = makeDeps();
    const res = await handleScreenshot(sm, { session_id: "aa11", width: 390, height: 844 }, deps);
    if ("code" in res) throw new Error(`unexpected error: ${JSON.stringify(res)}`);
    expect(metricsCalls(sent).at(-1)).toEqual({
      method: "Emulation.clearDeviceMetricsOverride",
      params: undefined,
    });
  });

  it("puts back the profile tool.emulate had applied", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    const ctx = await sm.start("aa11");
    const cdp = {
      send: vi.fn(async () => ({})),
      setDeviceMetricsOverride: vi.fn(async () => {}),
      setUserAgentOverride: vi.fn(async () => {}),
      setTouchEmulationEnabled: vi.fn(async () => {}),
      clearDeviceMetricsOverride: vi.fn(async () => {}),
      getAttachmentId: vi.fn(() => "attachment"),
      trackSessionTab: vi.fn(),
    };
    const emulateDeps: EmulateDeps = {
      cdp: cdp as unknown as EmulateCdpRunner,
      tabsApi: {
        get: vi.fn(async (tabId: number) => ({ id: tabId, windowId: 100, active: true }) as never),
        query: vi.fn(async () => [{ id: 4, windowId: 100, active: true }] as never),
      },
    };
    await handleEmulate(
      sm,
      {
        session_id: "aa11",
        overrides: { width: 390, height: 844, device_scale_factor: 3, mobile: true },
      },
      emulateDeps,
    );

    await withTemporaryViewport(
      cdp as unknown as CdpRunner,
      ctx,
      4,
      { width: 1280, height: 720 },
      async () => {},
    );

    // The temporary size is undone by reapplying emulate's profile, not by
    // clearing it — otherwise the next screenshot would lose the device
    // profile the agent asked for.
    expect(cdp.send).toHaveBeenCalledWith(4, "Emulation.setDeviceMetricsOverride", {
      width: 1280,
      height: 720,
      deviceScaleFactor: 1,
      mobile: false,
    });
    expect(cdp.send).toHaveBeenCalledWith(4, "Emulation.setDeviceMetricsOverride", {
      width: 390,
      height: 844,
      deviceScaleFactor: 3,
      mobile: true,
    });
    expect(cdp.send).not.toHaveBeenCalledWith(4, "Emulation.clearDeviceMetricsOverride", undefined);
  });

  it("releases the override even when the capture throws", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    const ctx = await sm.start("aa11");
    const cdp = { send: vi.fn(async () => ({})) } as unknown as CdpRunner;
    await expect(
      withTemporaryViewport(cdp, ctx, 4, { width: 800, height: 600 }, async () => {
        throw new Error("capture failed");
      }),
    ).rejects.toThrow("capture failed");
    expect(cdp.send).toHaveBeenCalledWith(4, "Emulation.clearDeviceMetricsOverride");
  });

  it("rejects a size combined with a ref", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    const ctx = await sm.start("aa11");
    ctx.refStore.set("e1", 999, { tabId: 4 });
    const { deps, sent } = makeDeps();
    const res = await handleScreenshot(
      sm,
      { session_id: "aa11", ref: "@e1", width: 390, height: 844 },
      deps,
    );
    expect(res).toMatchObject({ code: "invalid_params" });
    expect(sent).toEqual([]);
  });

  it("requires CDP when a size is requested", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const { deps } = makeDeps();
    const res = await handleScreenshot(
      sm,
      { session_id: "aa11", width: 390, height: 844 },
      { ...deps, cdp: undefined },
    );
    expect(res).toMatchObject({ code: "cdp_failed" });
  });

  it("leaves the tab untouched when the size is invalid", async () => {
    const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
    await sm.start("aa11");
    const { deps, sent } = makeDeps();
    const res = await handleScreenshot(sm, { session_id: "aa11", width: 0, height: 844 }, deps);
    expect(res).toMatchObject({ code: "invalid_params" });
    expect(sent).toEqual([]);
  });
});
