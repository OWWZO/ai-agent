/**
 * `bsk click|hover|focus|blur|fill|press|select` — pure library implementations.
 * Mirrors `crates/bsk-cli/src/cli/interaction.rs`.
 */

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import type {
  BlurResult,
  ClickParams,
  ClickResult,
  FillParams,
  FillResult,
  FocusParams,
  FocusResult,
  HoverParams,
  HoverResult,
  PressParams,
  PressResult,
  SelectParams,
  SelectResult,
} from '../protocol.js';
import { resolveModifiers, splitTarget } from './args.js';
import type {
  BlurArgs,
  BskCaller,
  ClickArgs,
  FillArgs,
  FocusArgs,
  HoverArgs,
  PressArgs,
  SelectArgs,
} from './types.js';

/** Default tool timeout used by the Rust CLI for interaction commands. */
const DEFAULT_INTERACTION_TIMEOUT_MS = 30_000;

function timeoutOf(value: number | undefined): number {
  return value ?? DEFAULT_INTERACTION_TIMEOUT_MS;
}

/**
 * `--capture` requires both image coordinates and conflicts with `--selector`
 * (commander has no `requires_all` / `conflicts_with`, so this is hand-written).
 */
function validateCapture(args: ClickArgs): void {
  const hasCapture = args.captureId !== undefined;
  const hasX = args.imageX !== undefined;
  const hasY = args.imageY !== undefined;
  if (hasCapture && (!hasX || !hasY)) {
    throw new BskUsageError('--capture requires both --image-x and --image-y');
  }
  if (!hasCapture && (hasX || hasY)) {
    throw new BskUsageError('--image-x / --image-y require --capture');
  }
  if (hasCapture && args.selector !== undefined) {
    throw new BskUsageError('--capture conflicts with --selector');
  }
  if (hasCapture && (args.target !== undefined || args.ref !== undefined)) {
    throw new BskUsageError('--capture conflicts with a target ref');
  }
}

export async function click(client: BskCaller, args: ClickArgs): Promise<ClickResult> {
  validateCapture(args);
  const target = args.captureId !== undefined ? {} : splitTarget(args.target, args.ref, args.selector);
  const modifiers = resolveModifiers(args.modifiers);
  const params: ClickParams = {
    session_id: args.sessionId,
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.captureId === undefined ? {} : { capture_id: args.captureId }),
    ...(args.imageX === undefined ? {} : { image_x: args.imageX }),
    ...(args.imageY === undefined ? {} : { image_y: args.imageY }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    button: args.button ?? 'left',
    click_count: args.clickCount ?? 1,
    ...(modifiers === undefined ? {} : { modifiers }),
    timeout_ms: timeoutOf(args.timeoutMs),
  };
  return client.call<ClickResult>(METHODS.toolClick, params, { timeoutMs: timeoutOf(args.timeoutMs) });
}

export async function hover(client: BskCaller, args: HoverArgs): Promise<HoverResult> {
  const target = splitTarget(args.target, args.ref, args.selector);
  const modifiers = resolveModifiers(args.modifiers);
  const timeoutMs = timeoutOf(args.timeoutMs);
  const params: HoverParams = {
    session_id: args.sessionId,
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(modifiers === undefined ? {} : { modifiers }),
    settle_ms: args.settleMs ?? 200,
    timeout_ms: timeoutMs,
  };
  return client.call<HoverResult>(METHODS.toolHover, params, { timeoutMs });
}

export async function focus(client: BskCaller, args: FocusArgs): Promise<FocusResult> {
  const target = splitTarget(args.target, args.ref, args.selector);
  const timeoutMs = timeoutOf(args.timeoutMs);
  const params: FocusParams = {
    session_id: args.sessionId,
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    timeout_ms: timeoutMs,
  };
  return client.call<FocusResult>(METHODS.toolFocus, params, { timeoutMs });
}

export async function blur(client: BskCaller, args: BlurArgs): Promise<BlurResult> {
  const target = splitTarget(args.target, args.ref, args.selector);
  const timeoutMs = timeoutOf(args.timeoutMs);
  const params: FocusParams = {
    session_id: args.sessionId,
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    timeout_ms: timeoutMs,
  };
  return client.call<BlurResult>(METHODS.toolBlur, params, { timeoutMs });
}

export async function fill(client: BskCaller, args: FillArgs): Promise<FillResult> {
  const target = splitTarget(args.target, args.ref, args.selector);
  const timeoutMs = timeoutOf(args.timeoutMs);
  const params: FillParams = {
    session_id: args.sessionId,
    value: args.value,
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    // `clear_before` defaults to true on the extension; only send the override.
    ...(args.noClear ? { clear_before: false } : {}),
    timeout_ms: timeoutMs,
  };
  return client.call<FillResult>(METHODS.toolFill, params, { timeoutMs });
}

export async function press(client: BskCaller, args: PressArgs): Promise<PressResult> {
  if (args.key.length === 0) throw new BskUsageError('press requires a key');
  const modifiers = resolveModifiers(args.modifiers);
  const timeoutMs = timeoutOf(args.timeoutMs);
  const params: PressParams = {
    session_id: args.sessionId,
    key: args.key,
    ...(modifiers === undefined ? {} : { modifiers }),
    ...(args.ref === undefined ? {} : { ref: args.ref }),
    ...(args.selector === undefined ? {} : { selector: args.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    ...(args.holdMs === undefined ? {} : { hold_ms: args.holdMs }),
    timeout_ms: timeoutMs,
  };
  return client.call<PressResult>(METHODS.toolPress, params, { timeoutMs });
}

export async function select(client: BskCaller, args: SelectArgs): Promise<SelectResult> {
  if (args.values.length === 0) throw new BskUsageError('select requires at least one --value');
  const target = splitTarget(args.target, args.ref, args.selector);
  const timeoutMs = timeoutOf(args.timeoutMs);
  const params: SelectParams = {
    session_id: args.sessionId,
    values: [...args.values],
    ...(target.ref === undefined ? {} : { ref: target.ref }),
    ...(target.selector === undefined ? {} : { selector: target.selector }),
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    timeout_ms: timeoutMs,
  };
  return client.call<SelectResult>(METHODS.toolSelect, params, { timeoutMs });
}
