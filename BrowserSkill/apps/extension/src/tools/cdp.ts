// `tool.cdp` — dispatch one allowlisted CDP method against a tab.
//
// Mirrors the OpenCLI extension's `cdp` action. The allowlist is the whole
// safety story: `chrome.debugger.sendCommand` can do anything the browser can
// do, including `Network.setCookie`, `Page.navigate`, or reading another
// origin's storage. An open passthrough would make every other tool's scope
// checks meaningless, so only methods that are already reachable through a
// higher-level tool are permitted here.

import type { SessionManager } from "@/session-manager/manager";
import type { CdpParams, CdpResult, RpcError } from "@/transport/types";
import {
  type CdpRunner,
  type ChromeTabsApi,
  chromeTabsApi,
  enforceToolTargetScope,
  isRpcError,
  lookupSession,
  resolveTargetTab,
} from "./shared";

/**
 * CDP methods a caller may dispatch. Transcribed verbatim from the OpenCLI
 * extension (`browser-extension/src/background.ts` `CDP_ALLOWLIST`), minus
 * `Runtime.enable` — bsk attaches with its own setup sequence and does not
 * need a caller to enable the Runtime domain.
 */
export const CDP_ALLOWLIST: ReadonlySet<string> = new Set([
  // Agent DOM context
  "Accessibility.enable",
  "Accessibility.getFullAXTree",
  "DOM.enable",
  "DOM.getDocument",
  "DOM.getBoxModel",
  "DOM.getContentQuads",
  "DOM.focus",
  "DOM.querySelector",
  "DOM.querySelectorAll",
  "DOM.scrollIntoViewIfNeeded",
  "DOMSnapshot.captureSnapshot",
  // Native input events
  "Input.dispatchMouseEvent",
  "Input.dispatchKeyEvent",
  "Input.insertText",
  // Page metrics & screenshots
  "Page.getLayoutMetrics",
  "Page.captureScreenshot",
  "Page.getFrameTree",
  "Page.handleJavaScriptDialog",
  // Emulation (used by screenshot full-page)
  "Emulation.setDeviceMetricsOverride",
  "Emulation.clearDeviceMetricsOverride",
]);

export interface CdpDeps {
  cdp: CdpRunner;
  tabsApi: ChromeTabsApi;
  signal?: AbortSignal;
}

/**
 * Handler for `tool.cdp`.
 *
 * Classified as a browser mutation: an allowlisted method such as
 * `Input.dispatchMouseEvent` submits real input, so the target must be inside
 * the Agent Window (or borrowed) exactly like `tool.click`.
 */
export async function handleCdp(
  manager: SessionManager,
  params: CdpParams,
  deps: CdpDeps,
): Promise<CdpResult | RpcError> {
  if (!params || typeof params.method !== "string" || params.method.length === 0) {
    return { code: "invalid_params", message: "cdp requires a method" };
  }
  if (!CDP_ALLOWLIST.has(params.method)) {
    return {
      code: "permission_denied",
      message: `CDP method not permitted: ${params.method}`,
    };
  }
  const ctxOrErr = lookupSession(manager, params, "cdp");
  if (isRpcError(ctxOrErr)) return ctxOrErr;
  const ctx = ctxOrErr;
  if (deps.signal?.aborted) return { code: "cancelled", message: "cdp aborted" };

  const target = await resolveTargetTab(manager, ctx, params.tab_id, deps.tabsApi);
  if (isRpcError(target)) return target;
  const denied = enforceToolTargetScope(ctx, target, "browser_mutation", "cdp");
  if (denied) return denied;

  try {
    const result = await deps.cdp.send<unknown>(
      target.tabId,
      params.method,
      params.params ?? {},
    );
    return { tab_id: target.tabId, method: params.method, result: result ?? null };
  } catch (err) {
    return {
      code: "cdp_failed",
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

export function defaultCdpDeps(cdp: CdpRunner, signal?: AbortSignal): CdpDeps {
  return { cdp, tabsApi: chromeTabsApi, ...(signal ? { signal } : {}) };
}
