/**
 * Browser/daemon-level commands: `browsers`, `system.status`, handshake,
 * `window resize`, `emulate`.
 * Mirrors `crates/bsk-cli/src/cli/browsers.rs`, `status.rs`, `window.rs`,
 * `emulate.rs`.
 */

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import type {
  BrowserListParams,
  BrowserListResult,
  EmulateOverrides,
  EmulateParams,
  EmulateResult,
  HandshakeParams,
  HandshakeResult,
  PingResult,
  StatusResult,
  WindowResizeParams,
  WindowResizeResult,
} from '../protocol.js';
import type {
  BskCaller,
  BrowserListArgs,
  EmulateArgs,
  HandshakeArgs,
  SystemStatusArgs,
  WindowResizeArgs,
} from './types.js';

const BROWSER_QUERY_TIMEOUT_MS = 5_000;

export async function browserList(
  client: BskCaller,
  args: BrowserListArgs = {},
): Promise<BrowserListResult> {
  const params: BrowserListParams = {
    ...(args.waitForBrowserMs === undefined ? {} : { wait_for_browser_ms: args.waitForBrowserMs }),
  };
  return client.call<BrowserListResult>(METHODS.browserList, params, {
    // A browser-connect wait may legitimately hold the call open.
    timeoutMs: (args.waitForBrowserMs ?? 0) + BROWSER_QUERY_TIMEOUT_MS,
  });
}

export async function systemStatus(
  client: BskCaller,
  args: SystemStatusArgs = {},
): Promise<StatusResult> {
  const params =
    args.waitForBrowserMs === undefined ? undefined : { wait_for_browser_ms: args.waitForBrowserMs };
  return client.call<StatusResult>(METHODS.systemStatus, params, {
    timeoutMs: (args.waitForBrowserMs ?? 0) + BROWSER_QUERY_TIMEOUT_MS,
  });
}

export async function ping(client: BskCaller): Promise<PingResult> {
  return client.call<PingResult>(METHODS.systemPing, undefined, { timeoutMs: BROWSER_QUERY_TIMEOUT_MS });
}

/**
 * `system.handshake` — the mandatory first frame on a fresh connection.
 * Compatibility is evaluated by the peer (`evaluate_handshake_compat`); this
 * client only transmits the declared protocol version.
 */
export async function handshake(
  client: BskCaller,
  args: HandshakeArgs,
): Promise<HandshakeResult> {
  const params: HandshakeParams = {
    client: args.client,
    version: args.version,
    protocol_version: args.protocolVersion,
    instance_id: args.instanceId,
    browser: args.browser,
    label: args.label,
    ...(args.minCompatiblePeer === undefined
      ? {}
      : { min_compatible_peer: args.minCompatiblePeer }),
    ...(args.minCompatibleProtocol === undefined
      ? {}
      : { min_compatible_protocol: args.minCompatibleProtocol }),
  };
  return client.call<HandshakeResult>(METHODS.systemHandshake, params, { timeoutMs: 10_000 });
}

export async function windowResize(
  client: BskCaller,
  args: WindowResizeArgs,
): Promise<WindowResizeResult> {
  const params: WindowResizeParams = {
    session_id: args.sessionId,
    width: args.width,
    height: args.height,
  };
  return client.call<WindowResizeResult>(METHODS.toolWindowResize, params, { timeoutMs: 35_000 });
}

// ── Emulate ─────────────────────────────────────────────────────────────────

export interface DevicePreset {
  name: string;
  width: number;
  height: number;
  deviceScaleFactor: number;
  mobile: boolean;
  userAgent: string;
  maxTouchPoints: number;
}

const IPHONE_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';
const IPAD_UA =
  'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';

/** Verbatim copy of `DEVICE_PRESETS` in `crates/bsk-cli/src/cli/emulate.rs`. */
export const DEVICE_PRESETS: readonly DevicePreset[] = [
  {
    name: 'iphone-14',
    width: 390,
    height: 844,
    deviceScaleFactor: 3.0,
    mobile: true,
    userAgent: IPHONE_UA,
    maxTouchPoints: 5,
  },
  {
    name: 'iphone-14-pro-max',
    width: 430,
    height: 932,
    deviceScaleFactor: 3.0,
    mobile: true,
    userAgent: IPHONE_UA,
    maxTouchPoints: 5,
  },
  {
    name: 'iphone-se',
    width: 375,
    height: 667,
    deviceScaleFactor: 2.0,
    mobile: true,
    userAgent: IPHONE_UA,
    maxTouchPoints: 5,
  },
  {
    name: 'pixel-7',
    width: 412,
    height: 915,
    deviceScaleFactor: 2.625,
    mobile: true,
    userAgent:
      'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.43 Mobile Safari/537.36',
    maxTouchPoints: 5,
  },
  {
    name: 'galaxy-s23',
    width: 360,
    height: 780,
    deviceScaleFactor: 3.0,
    mobile: true,
    userAgent:
      'Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.43 Mobile Safari/537.36',
    maxTouchPoints: 5,
  },
  {
    name: 'ipad-mini',
    width: 744,
    height: 1133,
    deviceScaleFactor: 2.0,
    mobile: true,
    userAgent: IPAD_UA,
    maxTouchPoints: 5,
  },
  {
    name: 'galaxy-tab-s8',
    width: 800,
    height: 1280,
    deviceScaleFactor: 2.0,
    mobile: true,
    userAgent:
      'Mozilla/5.0 (Linux; Android 14; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.43 Safari/537.36',
    maxTouchPoints: 5,
  },
];

export function presetNames(): string {
  return DEVICE_PRESETS.map((preset) => preset.name).join(', ');
}

export function findPreset(name: string): DevicePreset | undefined {
  return DEVICE_PRESETS.find((preset) => preset.name === name);
}

function invalidParams(message: string): BskUsageError {
  return new BskUsageError(message);
}

/**
 * Build `tool.emulate` params from CLI-style args: preset forms the base,
 * manual flags override individual fields, then cross-field checks run on the
 * merged result (exactly like `emulate.rs::build_params`).
 */
export function buildEmulateParams(args: EmulateArgs): EmulateParams {
  const hasManual =
    args.width !== undefined ||
    args.height !== undefined ||
    args.dpr !== undefined ||
    args.mobile === true ||
    args.noMobile === true ||
    args.ua !== undefined ||
    args.acceptLanguage !== undefined ||
    args.touch === true ||
    args.noTouch === true ||
    args.maxTouchPoints !== undefined;

  if (args.off) {
    if (args.device !== undefined || hasManual) {
      throw invalidParams(
        '--off cannot be combined with --device or manual emulation options',
      );
    }
    return {
      session_id: args.sessionId,
      ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
      off: true,
    };
  }

  if (args.device === undefined && !hasManual) {
    throw invalidParams(
      `nothing to do: pass --device <preset> (${presetNames()}), manual options (--width/--height/--dpr/--mobile/--ua/--touch), or --off`,
    );
  }

  let overrides: EmulateOverrides;
  if (args.device !== undefined) {
    const preset = findPreset(args.device);
    if (!preset) {
      throw invalidParams(
        `unknown device preset '${args.device}' (available: ${presetNames()})`,
      );
    }
    overrides = {
      width: preset.width,
      height: preset.height,
      device_scale_factor: preset.deviceScaleFactor,
      mobile: preset.mobile,
      user_agent: preset.userAgent,
      touch: true,
      max_touch_points: preset.maxTouchPoints,
    };
  } else {
    overrides = {};
  }

  if (args.width !== undefined) overrides.width = args.width;
  if (args.height !== undefined) overrides.height = args.height;
  if (args.dpr !== undefined) overrides.device_scale_factor = args.dpr;
  if (args.mobile === true) overrides.mobile = true;
  if (args.noMobile === true) overrides.mobile = false;
  if (args.ua !== undefined) overrides.user_agent = args.ua;
  if (args.acceptLanguage !== undefined) overrides.accept_language = args.acceptLanguage;
  if (args.touch === true || args.maxTouchPoints !== undefined) overrides.touch = true;
  if (args.noTouch === true) overrides.touch = false;
  if (args.maxTouchPoints !== undefined) overrides.max_touch_points = args.maxTouchPoints;

  const hasWidth = overrides.width !== undefined;
  const hasHeight = overrides.height !== undefined;
  if (hasWidth !== hasHeight) {
    throw invalidParams('--width and --height must be provided together');
  }
  if (
    !hasWidth &&
    (overrides.device_scale_factor !== undefined || overrides.mobile !== undefined)
  ) {
    throw invalidParams(
      '--dpr/--mobile/--no-mobile require --width/--height (viewport metrics need dimensions)',
    );
  }
  if (overrides.accept_language !== undefined && overrides.user_agent === undefined) {
    throw invalidParams('--accept-language requires --ua');
  }

  return {
    session_id: args.sessionId,
    ...(args.tabId === undefined ? {} : { tab_id: args.tabId }),
    overrides,
  };
}

export async function emulate(client: BskCaller, args: EmulateArgs): Promise<EmulateResult> {
  const params = buildEmulateParams(args);
  return client.call<EmulateResult>(METHODS.toolEmulate, params, { timeoutMs: 35_000 });
}
