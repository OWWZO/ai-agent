/**
 * `bsk network` / `bsk console` — buffered page diagnostics.
 * Mirrors `crates/bsk-cli/src/cli/network.rs` and `console.rs`.
 */

import { METHODS } from '../methods.js';
import type {
  ConsoleParams,
  ConsoleResult,
  NetworkParams,
  NetworkResult,
} from '../protocol.js';
import type { BskCaller, ConsoleArgs, NetworkArgs } from './types.js';

const TOOL_TIMEOUT_MS = 35_000;

export async function network(client: BskCaller, args: NetworkArgs): Promise<NetworkResult> {
  const params: NetworkParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(args.since === undefined ? {} : { since: args.since }),
    ...(args.limit === undefined ? {} : { limit: args.limit }),
    ...(args.maxTextChars === undefined ? {} : { max_text_chars: args.maxTextChars }),
  };
  return client.call<NetworkResult>(METHODS.toolNetwork, params, { timeoutMs: TOOL_TIMEOUT_MS });
}

export async function console(client: BskCaller, args: ConsoleArgs): Promise<ConsoleResult> {
  const params: ConsoleParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(args.since === undefined ? {} : { since: args.since }),
    ...(args.limit === undefined ? {} : { limit: args.limit }),
    ...(args.maxTextChars === undefined ? {} : { max_text_chars: args.maxTextChars }),
    // Rust only forwards `include_stack` when the flag is set.
    ...(args.includeStack ? { include_stack: true } : {}),
  };
  return client.call<ConsoleResult>(METHODS.toolConsole, params, { timeoutMs: TOOL_TIMEOUT_MS });
}
