/**
 * `bsk navigate|navigate-back|navigate-forward|reload`.
 * Mirrors `crates/bsk-cli/src/cli/navigate.rs`.
 *
 * Defaults match the Rust CLI: navigate waits `load` with a 30s budget, while
 * history/reload commands use 15s.
 */

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import type {
  NavigateBackResult,
  NavigateForwardResult,
  NavigateParams,
  NavigateResult,
  ReloadParams,
  ReloadResult,
} from '../protocol.js';
import type { BskCaller, NavigateArgs, NavigateHistoryArgs, ReloadArgs } from './types.js';

const NAVIGATE_TIMEOUT_MS = 30_000;
const HISTORY_TIMEOUT_MS = 15_000;

export async function navigate(client: BskCaller, args: NavigateArgs): Promise<NavigateResult> {
  if (!args.url || args.url.length === 0) throw new BskUsageError('navigate requires a url');
  if (!args.sessionId) throw new BskUsageError('navigate requires --session <session-id>');
  const timeoutMs = args.timeoutMs ?? NAVIGATE_TIMEOUT_MS;
  const params: NavigateParams = {
    session_id: args.sessionId,
    url: args.url,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    wait_until: args.waitUntil ?? 'load',
    timeout_ms: timeoutMs,
  };
  return client.call<NavigateResult>(METHODS.toolNavigate, params, { timeoutMs });
}

export async function navigateBack(
  client: BskCaller,
  args: NavigateHistoryArgs,
): Promise<NavigateBackResult> {
  const timeoutMs = args.timeoutMs ?? HISTORY_TIMEOUT_MS;
  const params = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    wait_until: args.waitUntil ?? 'load',
    timeout_ms: timeoutMs,
  };
  return client.call<NavigateBackResult>(METHODS.toolNavigateBack, params, { timeoutMs });
}

export async function navigateForward(
  client: BskCaller,
  args: NavigateHistoryArgs,
): Promise<NavigateForwardResult> {
  const timeoutMs = args.timeoutMs ?? HISTORY_TIMEOUT_MS;
  const params = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    wait_until: args.waitUntil ?? 'load',
    timeout_ms: timeoutMs,
  };
  return client.call<NavigateForwardResult>(METHODS.toolNavigateForward, params, { timeoutMs });
}

export async function reload(client: BskCaller, args: ReloadArgs): Promise<ReloadResult> {
  const timeoutMs = args.timeoutMs ?? HISTORY_TIMEOUT_MS;
  const params: ReloadParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    wait_until: args.waitUntil ?? 'load',
    timeout_ms: timeoutMs,
    // Rust only sends `hard` when the flag is set.
    ...(args.hard ? { hard: true } : {}),
  };
  return client.call<ReloadResult>(METHODS.toolReload, params, { timeoutMs });
}
