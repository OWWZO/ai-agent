/**
 * `bsk request-help` — pause for a human to finish an in-page step.
 * Mirrors `crates/bsk-cli/src/cli/human_loop.rs`.
 */

import { METHODS } from '../methods.js';
import type {
  HelpTarget,
  RequestHelpParams,
  RequestHelpResult,
} from '../protocol.js';
import { looksLikeRef, parseCompletionCriteria } from './args.js';
import type { BskCaller, RequestHelpArgs } from './types.js';

const DEFAULT_HELP_TIMEOUT_MS = 300_000;

/**
 * Classify a `--target` value: `@eN` / `eN` → ref, everything else → CSS
 * selector. Unlike `splitTarget`, this never throws (a miss is reported by
 * the extension through `resolved_targets`).
 */
export function parseHelpTarget(raw: string): HelpTarget {
  const trimmed = raw.trim();
  return looksLikeRef(trimmed) ? { ref: trimmed } : { selector: trimmed };
}

export async function requestHelp(
  client: BskCaller,
  args: RequestHelpArgs,
): Promise<RequestHelpResult> {
  const timeoutMs = args.timeoutMs ?? DEFAULT_HELP_TIMEOUT_MS;
  const targets = args.targets?.map(parseHelpTarget);
  const completionCriteria = parseCompletionCriteria(args.completionCriteria);
  const params: RequestHelpParams = {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    prompt: args.prompt,
    ...(args.title === undefined ? {} : { title: args.title }),
    ...(targets === undefined || targets.length === 0 ? {} : { targets }),
    ...(completionCriteria === undefined ? {} : { completion_criteria: completionCriteria }),
    timeout_ms: timeoutMs,
  };
  return client.call<RequestHelpResult>(METHODS.toolRequestHelp, params, {
    // The human gets the full budget; the transport must outlive it.
    timeoutMs: timeoutMs + 15_000,
  });
}
