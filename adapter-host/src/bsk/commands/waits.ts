/**
 * `bsk wait-for-navigation` / `bsk wait-ms`.
 * Mirrors `crates/bsk-cli/src/cli/waits.rs`.
 *
 * There is no `tool.wait_for_element` in the protocol — element waits must be
 * expressed as `wait_ms` plus a follow-up `snapshot`/`evaluate`.
 */

import { METHODS } from '../methods.js';
import type {
  WaitForNavigationParams,
  WaitForNavigationResult,
  WaitMsParams,
  WaitMsResult,
} from '../protocol.js';
import type { BskCaller, WaitForNavigationArgs, WaitMsArgs } from './types.js';

const WAIT_NAVIGATION_TIMEOUT_MS = 30_000;

export async function waitForNavigation(
  client: BskCaller,
  args: WaitForNavigationArgs,
): Promise<WaitForNavigationResult> {
  const timeoutMs = args.timeoutMs ?? WAIT_NAVIGATION_TIMEOUT_MS;
  const params: WaitForNavigationParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    wait_until: args.waitUntil ?? 'load',
    timeout_ms: timeoutMs,
  };
  return client.call<WaitForNavigationResult>(METHODS.toolWaitForNavigation, params, {
    timeoutMs,
  });
}

export async function waitMs(client: BskCaller, args: WaitMsArgs): Promise<WaitMsResult> {
  const params: WaitMsParams = { duration_ms: args.durationMs };
  // The daemon sleeps for the requested duration; give the call 15s of slack
  // so a long wait is not cut off by the client timeout.
  return client.call<WaitMsResult>(METHODS.toolWaitMs, params, {
    timeoutMs: args.durationMs + 15_000,
  });
}
