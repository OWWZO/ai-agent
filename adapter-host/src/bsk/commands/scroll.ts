/**
 * `bsk scroll-to` / `bsk wheel`.
 * Mirrors `crates/bsk-cli/src/cli/scroll.rs` and `wheel.rs`.
 */

import { METHODS } from '../methods.js';
import type {
  ScrollToParams,
  ScrollToResult,
  WheelParams,
  WheelResult,
} from '../protocol.js';
import { resolveModifiers, splitOptionalTarget, splitTarget, validateWheelDeltas } from './args.js';
import type { BskCaller, ScrollToArgs, WheelArgs } from './types.js';

const DEFAULT_TIMEOUT_MS = 30_000;

export async function scrollTo(client: BskCaller, args: ScrollToArgs): Promise<ScrollToResult> {
  const target = splitTarget(args.target, args.ref, args.selector);
  const timeoutMs = args.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const params: ScrollToParams = {
    session_id: args.sessionId,
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    timeout_ms: timeoutMs,
  };
  return client.call<ScrollToResult>(METHODS.toolScrollTo, params, { timeoutMs });
}

export async function wheel(client: BskCaller, args: WheelArgs): Promise<WheelResult> {
  const deltaX = args.deltaX ?? 0;
  const deltaY = args.deltaY ?? 0;
  validateWheelDeltas(deltaX, deltaY);
  // No target at all is legal: the extension dispatches at the viewport centre.
  const target = splitOptionalTarget(args.target, args.ref, args.selector);
  const modifiers = resolveModifiers(args.modifiers);
  const timeoutMs = args.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const params: WheelParams = {
    session_id: args.sessionId,
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    delta_x: deltaX,
    delta_y: deltaY,
    ...(modifiers === undefined ? {} : { modifiers }),
    timeout_ms: timeoutMs,
  };
  return client.call<WheelResult>(METHODS.toolWheel, params, { timeoutMs });
}
