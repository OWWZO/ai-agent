/**
 * `bsk cookies` / `bsk frames` / `bsk cdp` — browser primitives the upstream
 * bsk protocol has no shape for.
 *
 * These mirror the OpenCLI extension's `cookies` / `frames` / `cdp` actions.
 * They are extension-level `tool.*` methods: the Java gateway forwards unknown
 * `tool.*` frames verbatim, so nothing on the gateway side is involved.
 */

import { EXTENSION_METHODS } from '../methods.js';
import type { CdpParams, CdpResult, CookiesParams, CookiesResult, FramesParams, FramesResult } from '../protocol.js';
import type { BskCaller, CdpArgs, CookiesArgs, FramesArgs } from './types.js';

const TOOL_TIMEOUT_MS = 35_000;

export async function cookies(client: BskCaller, args: CookiesArgs): Promise<CookiesResult> {
  const params: CookiesParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(args.domain === undefined ? {} : { domain: args.domain }),
    ...(args.url === undefined ? {} : { url: args.url }),
  };
  return client.call<CookiesResult>(EXTENSION_METHODS.toolCookies, params, {
    timeoutMs: TOOL_TIMEOUT_MS,
  });
}

export async function frames(client: BskCaller, args: FramesArgs): Promise<FramesResult> {
  const params: FramesParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
  };
  return client.call<FramesResult>(EXTENSION_METHODS.toolFrames, params, {
    timeoutMs: TOOL_TIMEOUT_MS,
  });
}

export async function cdp(client: BskCaller, args: CdpArgs): Promise<CdpResult> {
  const params: CdpParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    method: args.method,
    ...(args.params === undefined ? {} : { params: args.params }),
  };
  return client.call<CdpResult>(EXTENSION_METHODS.toolCdp, params, {
    timeoutMs: TOOL_TIMEOUT_MS,
  });
}
