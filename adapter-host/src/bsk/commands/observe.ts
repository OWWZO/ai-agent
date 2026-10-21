/**
 * `bsk snapshot|observe|get-html`.
 * Mirrors `crates/bsk-cli/src/cli/snapshot.rs`, `observe.rs`, `get_html.rs`.
 */

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import type {
  GetHtmlParams,
  GetHtmlResult,
  ObserveParams,
  ObserveResult,
  SnapshotParams,
  SnapshotResult,
} from '../protocol.js';
import type { BskCaller, GetHtmlArgs, ObserveArgs, SnapshotArgs } from './types.js';

/** Generic tool IPC budget used by the Rust CLI (`TOOL_IPC_TIMEOUT`). */
const TOOL_TIMEOUT_MS = 35_000;

export async function snapshot(client: BskCaller, args: SnapshotArgs): Promise<SnapshotResult> {
  const params: SnapshotParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(args.maxDepth === undefined ? {} : { max_depth: args.maxDepth }),
    ...(args.maxTokens === undefined ? {} : { max_tokens: args.maxTokens }),
  };
  return client.call<SnapshotResult>(METHODS.toolSnapshot, params, { timeoutMs: TOOL_TIMEOUT_MS });
}

export async function observe(client: BskCaller, args: ObserveArgs): Promise<ObserveResult> {
  // A continuation may shrink its token budget but must not change how the
  // page was captured (`observe.rs` conflicts_with_all).
  if (
    args.cursor !== undefined &&
    (args.maxDepth !== undefined || args.debugSurfaces || args.probeHover)
  ) {
    throw new BskUsageError(
      '--cursor cannot be combined with --max-depth / --debug-surfaces / --probe-hover',
    );
  }
  const params: ObserveParams = {
    ...(args.cursor === undefined ? {} : { cursor: args.cursor }),
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(args.maxDepth === undefined ? {} : { max_depth: args.maxDepth }),
    ...(args.maxTokens === undefined ? {} : { max_tokens: args.maxTokens }),
    ...(args.debugSurfaces ? { debug_surfaces: true } : {}),
    ...(args.probeHover ? { probe_hover: true } : {}),
  };
  return client.call<ObserveResult>(METHODS.toolObserve, params, { timeoutMs: TOOL_TIMEOUT_MS });
}

export async function getHtml(client: BskCaller, args: GetHtmlArgs): Promise<GetHtmlResult> {
  const params: GetHtmlParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(args.ref === undefined ? {} : { ref: args.ref }),
    ...(args.maxBytes === undefined ? {} : { max_bytes: args.maxBytes }),
  };
  return client.call<GetHtmlResult>(METHODS.toolGetHtml, params, { timeoutMs: TOOL_TIMEOUT_MS });
}
