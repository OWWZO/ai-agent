// `tool.frames` — enumerate a tab's frame graph, including out-of-process
// iframes.
//
// Mirrors the OpenCLI extension's `frames` action. The heavy lifting already
// lives in `ChromiumCdp.getFrameGraph()` (attach → Page.getFrameTree →
// OOPIF discovery → DOM.getFrameOwner); this handler only resolves the
// session/tab and flattens the graph into a wire shape.

import type { CdpFrameGraph } from "@/browser-driver/frame-graph";
import type { SessionManager } from "@/session-manager/manager";
import type { FrameEntry, FramesParams, FramesResult, RpcError } from "@/transport/types";
import {
  type CdpRunner,
  type ChromeTabsApi,
  chromeTabsApi,
  enforceToolTargetScope,
  isRpcError,
  lookupSession,
  resolveTargetTab,
} from "./shared";

export interface FramesDeps {
  cdp: CdpRunner;
  tabsApi: ChromeTabsApi;
  signal?: AbortSignal;
}

/** `Page.getFrameTree` reply, used only when the driver has no frame graph. */
interface FrameTreeReply {
  frameTree?: {
    frame?: { id?: string; url?: string };
    childFrames?: unknown[];
  };
}

/**
 * Handler for `tool.frames`.
 *
 * Frame enumeration is a passive read: it attaches the debugger and walks the
 * frame tree but dispatches no page input, so a user tab may be inspected
 * without borrowing it.
 */
export async function handleFrames(
  manager: SessionManager,
  params: FramesParams,
  deps: FramesDeps,
): Promise<FramesResult | RpcError> {
  const ctxOrErr = lookupSession(manager, params, "frames");
  if (isRpcError(ctxOrErr)) return ctxOrErr;
  const ctx = ctxOrErr;
  if (deps.signal?.aborted) return { code: "cancelled", message: "frames aborted" };

  const target = await resolveTargetTab(manager, ctx, params.tab_id, deps.tabsApi);
  if (isRpcError(target)) return target;
  const denied = enforceToolTargetScope(ctx, target, "passive_read", "frames");
  if (denied) return denied;

  try {
    const graph = deps.cdp.getFrameGraph
      ? await deps.cdp.getFrameGraph(target.tabId)
      : await readRootFrameOnly(deps.cdp, target.tabId);
    if (isRpcError(graph)) return graph;
    return {
      tab_id: target.tabId,
      root_frame_id: graph.rootFrameId,
      frames: graph.frames.map(toFrameEntry),
      unavailable_frames: (graph.unavailableFrames ?? []).map(toFrameEntry),
    };
  } catch (err) {
    return {
      code: "cdp_failed",
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

/**
 * Fallback for drivers without `getFrameGraph` (test doubles, older builds):
 * read the root frame tree and report just that. Cross-origin children are
 * invisible here, which is why `unavailable_frames` stays empty rather than
 * pretending the tab has no iframes.
 */
async function readRootFrameOnly(
  cdp: CdpRunner,
  tabId: number,
): Promise<CdpFrameGraph | RpcError> {
  const reply = await cdp.send<FrameTreeReply>(tabId, "Page.getFrameTree", {});
  const rootFrameId = reply.frameTree?.frame?.id;
  if (!rootFrameId) {
    return { code: "cdp_failed", message: "Page.getFrameTree did not return a root frame id" };
  }
  const url = reply.frameTree?.frame?.url;
  return {
    rootFrameId,
    frames: [{ frameId: rootFrameId, ...(url ? { url } : {}), target: { tabId } }],
  };
}

function toFrameEntry(frame: CdpFrameGraph["frames"][number]): FrameEntry {
  return {
    frame_id: frame.frameId,
    ...(frame.parentFrameId ? { parent_frame_id: frame.parentFrameId } : {}),
    ...(frame.url ? { url: frame.url } : {}),
    // A frame carrying its own CDP session id runs out of process; frames
    // sharing the root target are same-process children.
    out_of_process: frame.target.sessionId !== undefined,
  };
}

export function defaultFramesDeps(cdp: CdpRunner, signal?: AbortSignal): FramesDeps {
  return { cdp, tabsApi: chromeTabsApi, ...(signal ? { signal } : {}) };
}
