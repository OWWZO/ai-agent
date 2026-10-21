/**
 * Session lifecycle against the browser extension.
 * Mirrors `crates/bsk-cli/src/cli/session.rs` + `daemon/sessions.rs`.
 *
 * Corrected per the protocol: the wire method is `tool.session_start`
 * (NOT `session.start`), and its params are `{session_id,
 * browser_instance_id?, width?, height?, focused?}` with result
 * `{agent_window_id?, interaction?}`. The `session.*` methods are the daemon's
 * own CLI-facing surface and are kept for parity only.
 */

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import type {
  SessionListResult,
  SessionStartParams,
  SessionStopParams,
  SessionStopResult,
  ToolSessionStartParams,
  ToolSessionStartResult,
  ToolSessionStopParams,
  ToolSessionStopResult,
} from '../protocol.js';
import type {
  BskCaller,
  SessionRequestArgs,
  SessionStartArgs,
  SessionStopAllArgs,
  SessionStopArgs,
} from './types.js';

/** Agent Window dimensions must stay within 100..=7680 CSS pixels. */
export const MIN_WINDOW_DIMENSION = 100;
export const MAX_WINDOW_DIMENSION = 7680;

/** The daemon holds `tool.session_start` open while the extension reconnects. */
const SESSION_START_TIMEOUT_MS = 45_000;
const SESSION_STOP_TIMEOUT_MS = 60 * 60 * 1000;

function validateDimension(value: number, label: string): void {
  if (!Number.isInteger(value) || value < MIN_WINDOW_DIMENSION || value > MAX_WINDOW_DIMENSION) {
    throw new BskUsageError(
      `${label} ${value} out of range (${MIN_WINDOW_DIMENSION}..=${MAX_WINDOW_DIMENSION})`,
    );
  }
}

export async function sessionStart(
  client: BskCaller,
  args: SessionStartArgs,
): Promise<ToolSessionStartResult> {
  if (!args.sessionId) throw new BskUsageError('session start requires a session id');
  if ((args.width === undefined) !== (args.height === undefined)) {
    throw new BskUsageError('--width and --height must be given together');
  }
  if (args.width !== undefined) validateDimension(args.width, 'window width');
  if (args.height !== undefined) validateDimension(args.height, 'window height');
  const params: ToolSessionStartParams = {
    session_id: args.sessionId,
    ...(args.browserInstanceId === undefined
      ? {}
      : { browser_instance_id: args.browserInstanceId }),
    ...(args.width === undefined ? {} : { width: args.width }),
    ...(args.height === undefined ? {} : { height: args.height }),
    ...(args.focused === undefined ? {} : { focused: args.focused }),
  };
  return client.call<ToolSessionStartResult>(METHODS.toolSessionStart, params, {
    timeoutMs: SESSION_START_TIMEOUT_MS,
  });
}

export async function sessionStop(
  client: BskCaller,
  args: SessionStopArgs,
): Promise<ToolSessionStopResult> {
  if (!args.sessionId) throw new BskUsageError('session stop requires a session id');
  const params: ToolSessionStopParams = { session_id: args.sessionId };
  return client.call<ToolSessionStopResult>(METHODS.toolSessionStop, params, {
    timeoutMs: SESSION_STOP_TIMEOUT_MS,
  });
}

/**
 * Daemon-level `session.stop` (releases the daemon's session registry entry,
 * returning borrowed tabs). Use after `tool.session_stop`, or on its own when
 * talking to the daemon rather than the extension.
 */
export async function sessionStopDaemon(
  client: BskCaller,
  args: SessionStopArgs,
): Promise<SessionStopResult> {
  if (!args.sessionId) throw new BskUsageError('session stop requires a session id');
  const params: SessionStopParams = { session_id: args.sessionId, all: false };
  return client.call<SessionStopResult>(METHODS.sessionStop, params, {
    timeoutMs: SESSION_STOP_TIMEOUT_MS,
  });
}

export async function sessionStopAll(
  client: BskCaller,
  _args: SessionStopAllArgs = {},
): Promise<SessionStopResult> {
  return client.call<SessionStopResult>(METHODS.sessionStopAll, undefined, {
    timeoutMs: SESSION_STOP_TIMEOUT_MS,
  });
}

export async function sessionList(client: BskCaller): Promise<SessionListResult> {
  return client.call<SessionListResult>(METHODS.sessionList, undefined, { timeoutMs: 5_000 });
}

/** Daemon-level `session.start` (CLI parity; the adapter uses `sessionStart`). */
export async function sessionStartDaemon(
  client: BskCaller,
  args: SessionStartArgs,
): Promise<DaemonSessionStartResult> {
  const params: SessionStartParams = {
    ...(args.browserInstanceId === undefined
      ? {}
      : { browser_instance_id: args.browserInstanceId }),
    ...(args.width === undefined ? {} : { width: args.width }),
    ...(args.height === undefined ? {} : { height: args.height }),
    ...(args.focused === undefined ? {} : { focused: args.focused }),
  };
  return client.call<DaemonSessionStartResult>(METHODS.sessionStart, params, {
    timeoutMs: SESSION_START_TIMEOUT_MS,
  });
}

/** Reply shape of the daemon-level `session.start` (see `session.rs` StartReply). */
export interface DaemonSessionStartResult {
  session_id: string;
  browser_instance_id: string;
  agent_window_id?: number;
  interaction?: ToolSessionStartResult['interaction'];
}

/** Recoverable start request (`session.request`). */
export async function sessionRequest(
  client: BskCaller,
  args: SessionRequestArgs,
): Promise<Record<string, unknown>> {
  const params = {
    request_id: args.requestId,
    action: args.action ?? 'status',
  };
  return client.call<Record<string, unknown>>(METHODS.sessionRequest, params, {
    timeoutMs: 40_000,
  });
}
