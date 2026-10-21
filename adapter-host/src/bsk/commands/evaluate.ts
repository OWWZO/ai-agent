/**
 * `bsk evaluate <expression>`.
 * Mirrors `crates/bsk-cli/src/cli/evaluate.rs`.
 *
 * `ok:false` is returned as data (a JS throw is not an RPC error), exactly as
 * the Rust CLI keeps exit code 0 for it. Callers that need a hard failure
 * should inspect `ok` (see `evaluateValue`).
 */

import { METHODS } from '../methods.js';
import type { EvaluateParams, EvaluateResult } from '../protocol.js';
import type { BskCaller, EvaluateArgs } from './types.js';

const EVALUATE_TIMEOUT_MS = 30_000;

export async function evaluate(client: BskCaller, args: EvaluateArgs): Promise<EvaluateResult> {
  const timeoutMs = args.timeoutMs ?? EVALUATE_TIMEOUT_MS;
  const params: EvaluateParams = {
    session_id: args.sessionId,
    expression: args.expression,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    await_promise: args.awaitPromise ?? true,
    return_by_value: args.returnByValue ?? true,
    timeout_ms: timeoutMs,
  };
  return client.call<EvaluateResult>(METHODS.toolEvaluate, params, { timeoutMs });
}

/**
 * Convenience for adapters: unwraps the bsk envelope and returns `value`
 * directly, throwing when the expression threw.
 */
export async function evaluateValue<T = unknown>(
  client: BskCaller,
  args: EvaluateArgs,
): Promise<T> {
  const result = await evaluate(client, args);
  if (!result.ok) {
    throw new Error(result.error?.text ?? 'evaluate failed');
  }
  return result.value as T;
}
