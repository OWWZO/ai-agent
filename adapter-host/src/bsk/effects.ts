/**
 * Browser-side effect classification for every bsk method.
 *
 * Mirrors `Method::effect()` in
 * `BrowserSkill/crates/bsk-protocol/src/method.rs` exactly — including the
 * judgment calls documented there (`tool.evaluate` is a mutation, `tool.observe`
 * is transient input, `tool.wait_*` are passive reads).
 *
 * The Rust match has no `_ =>` arm, so adding a variant is a compile error.
 * TS gets the same guarantee from `Record<Method, MethodEffect>`: a missing or
 * unknown key fails `tsc`.
 */

import type { ExtensionMethod, Method, UpstreamMethod } from './methods.js';

export type MethodEffect = 'PassiveRead' | 'TransientInput' | 'BrowserMutation' | 'ControlPlane';

export const METHOD_EFFECT: Record<UpstreamMethod, MethodEffect> = {
  // ── BrowserMutation: gated by pending user interrupts ────────────────────
  'tool.tab_create': 'BrowserMutation',
  'tool.tab_close': 'BrowserMutation',
  'tool.tab_borrow': 'BrowserMutation',
  'tool.tab_return': 'BrowserMutation',
  'tool.tab_select': 'BrowserMutation',
  'tool.window_resize': 'BrowserMutation',
  'tool.emulate': 'BrowserMutation',
  'tool.navigate': 'BrowserMutation',
  'tool.navigate_back': 'BrowserMutation',
  'tool.navigate_forward': 'BrowserMutation',
  'tool.reload': 'BrowserMutation',
  'tool.click': 'BrowserMutation',
  'tool.wheel': 'BrowserMutation',
  'tool.scroll_to': 'BrowserMutation',
  'tool.focus': 'BrowserMutation',
  'tool.blur': 'BrowserMutation',
  'tool.fill': 'BrowserMutation',
  'tool.press': 'BrowserMutation',
  'tool.select': 'BrowserMutation',
  'tool.upload': 'BrowserMutation',
  'tool.download': 'BrowserMutation',
  // The daemon cannot tell a `document.title` read from `form.submit()`.
  'tool.evaluate': 'BrowserMutation',
  // May navigate via optional `url` and changes Agent Window chrome.
  'tool.record_start': 'BrowserMutation',

  // ── TransientInput: real page input, no committed state ──────────────────
  'tool.hover': 'TransientInput',
  'tool.observe': 'TransientInput',
  'tool.screenshot_full_page': 'TransientInput',

  // ── PassiveRead: observe state only ──────────────────────────────────────
  'tool.tab_list': 'PassiveRead',
  'tool.snapshot': 'PassiveRead',
  'tool.get_html': 'PassiveRead',
  'tool.screenshot': 'PassiveRead',
  'tool.screenshot_read': 'PassiveRead',
  'tool.console': 'PassiveRead',
  'tool.network': 'PassiveRead',
  'tool.wait_for_navigation': 'PassiveRead',
  'tool.wait_ms': 'PassiveRead',
  'tool.request_help': 'PassiveRead',
  'tool.record_stop': 'PassiveRead',
  'tool.record_await': 'PassiveRead',

  // ── ControlPlane: never gated (teardown after interrupt must work) ───────
  'session.start': 'ControlPlane',
  'session.start_tracked': 'ControlPlane',
  'session.request': 'ControlPlane',
  'session.stop': 'ControlPlane',
  'session.stop_all': 'ControlPlane',
  'session.list': 'ControlPlane',
  'tool.session_start': 'ControlPlane',
  'tool.session_stop': 'ControlPlane',
  'tool.debug': 'ControlPlane',
  'audit.request': 'ControlPlane',
  'system.handshake': 'ControlPlane',
  'system.ping': 'ControlPlane',
  'system.status': 'ControlPlane',
  'browser.list': 'ControlPlane',
  'transfer.begin': 'ControlPlane',
  'transfer.chunk': 'ControlPlane',
  'transfer.finish': 'ControlPlane',
  'transfer.read': 'ControlPlane',
  'transfer.release': 'ControlPlane',
  'tool.screenshot_release': 'ControlPlane',
  'cancel': 'ControlPlane',
};

// Compile-time exhaustiveness assertion. `Record<UpstreamMethod, …>` already
// rejects missing keys, but this pins the *key set* so a widening of the
// upstream method union cannot silently ride along with a widened table.
type Equal<A, B> = (<T>() => T extends A ? 1 : 2) extends <T>() => T extends B ? 1 : 2 ? true : false;
type Expect<T extends true> = T;
type _AllMethodsClassified = Expect<Equal<keyof typeof METHOD_EFFECT, UpstreamMethod>>;

/**
 * Extension-only methods have no Rust counterpart, so their effects are
 * declared here. They follow the same judgment calls: cookie and frame reads
 * inspect state, `cdp` is treated as a mutation for the same reason
 * `tool.evaluate` is — the caller picks the command.
 */
export const EXTENSION_METHOD_EFFECT: Record<ExtensionMethod, MethodEffect> = {
  'tool.cookies': 'PassiveRead',
  'tool.frames': 'PassiveRead',
  'tool.cdp': 'BrowserMutation',
};

const ALL_EFFECTS: Record<Method, MethodEffect> = { ...METHOD_EFFECT, ...EXTENSION_METHOD_EFFECT };

/** Debug writes that the daemon gates even though the method is ControlPlane. */
const DEBUG_GATED_ACTIONS: ReadonlySet<string> = new Set(['rule_add', 'rule_enable', 'replay']);

export function methodEffect(method: Method): MethodEffect {
  return ALL_EFFECTS[method];
}

/** Whether a pending user interrupt should reject this method. */
export function requiresInterruptGate(method: Method): boolean {
  const effect = ALL_EFFECTS[method];
  return effect === 'TransientInput' || effect === 'BrowserMutation';
}

export function isMutating(method: Method): boolean {
  return ALL_EFFECTS[method] === 'BrowserMutation';
}

/**
 * `tool.debug` is ControlPlane at the method level, but its `rule_add` /
 * `rule_enable` / `replay` actions mutate the browser and must be gated.
 */
export function requiresInterruptGateWithParams(method: Method, params?: unknown): boolean {
  if (requiresInterruptGate(method)) return true;
  if (method !== 'tool.debug') return false;
  if (typeof params !== 'object' || params === null) return false;
  const action = (params as Record<string, unknown>).action;
  return typeof action === 'string' && DEBUG_GATED_ACTIONS.has(action);
}
