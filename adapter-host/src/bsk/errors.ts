/**
 * bsk wire errors.
 *
 * `ErrorCode` is the exact `#[serde(rename_all = "snake_case")]` set from
 * `BrowserSkill/crates/bsk-protocol/src/error.rs`. `extension_disconnected`
 * is NOT a protocol code: the Java relay synthesises it when the browser
 * extension drops while requests are in flight (contract §6.4), so it only
 * ever appears on the client side.
 */

export type ErrorCode =
  | 'unknown_method'
  | 'unsupported'
  | 'invalid_params'
  | 'not_found'
  | 'permission_denied'
  | 'timeout'
  | 'cdp_failed'
  | 'protocol_error'
  | 'cancelled'
  | 'user_aborted'
  | 'version_too_old'
  | 'multiple_browsers_online'
  | 'no_browser_connected';

export const ERROR_CODES: readonly ErrorCode[] = [
  'unknown_method',
  'unsupported',
  'invalid_params',
  'not_found',
  'permission_denied',
  'timeout',
  'cdp_failed',
  'protocol_error',
  'cancelled',
  'user_aborted',
  'version_too_old',
  'multiple_browsers_online',
  'no_browser_connected',
];

/** Client-side synthetic code used when the relay/extension connection drops. */
export type ClientErrorCode = 'extension_disconnected';

export type RpcErrorCode = ErrorCode | ClientErrorCode;

export interface RpcError {
  code: RpcErrorCode;
  message: string;
  data?: unknown;
}

/** Raised for every structured failure, local or remote. */
export class BskRpcError extends Error {
  readonly code: RpcErrorCode;
  readonly data?: unknown;
  readonly rpcId?: string;

  constructor(error: RpcError, rpcId?: string) {
    super(error.message);
    this.name = 'BskRpcError';
    this.code = error.code;
    this.data = error.data;
    this.rpcId = rpcId;
  }

  toJSON(): RpcError & { rpc_id?: string } {
    return {
      code: this.code,
      message: this.message,
      ...(this.data === undefined ? {} : { data: this.data }),
      ...(this.rpcId === undefined ? {} : { rpc_id: this.rpcId }),
    };
  }
}

/** Local argument-validation failure (mirrors the Rust CLI's `CliError::Local`). */
export class BskUsageError extends Error {
  readonly code = 'invalid_args';

  constructor(message: string) {
    super(message);
    this.name = 'BskUsageError';
  }
}

export function isRpcError(value: unknown): value is RpcError {
  if (typeof value !== 'object' || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.code === 'string' && typeof record.message === 'string';
}

export function isBskRpcError(value: unknown): value is BskRpcError {
  return value instanceof BskRpcError;
}

// ---------------------------------------------------------------------------
// Failure classification
// ---------------------------------------------------------------------------

/**
 * Whether a caller may safely re-run the same command.
 *
 * The distinction that matters is *when* the failure happened relative to page
 * code running. A failure before any page code ran leaves nothing to duplicate;
 * a failure mid-execution leaves an unknown outcome, so a blind re-run could
 * submit a form twice.
 */
export type FailureClass =
  /** Failed before any page code ran — a new attempt cannot double-apply. */
  | 'safe_to_retry'
  /** Died mid-execution — the outcome is unknown. Inspect before retrying. */
  | 'unsafe_to_retry'
  /** A precondition is wrong (stale ref, unborrowed tab, bad args). Fix it first. */
  | 'retry_after_fix'
  /** Retrying the same command will not help. */
  | 'fatal';

export interface FailureInfo {
  class: FailureClass;
  hint: string;
}

const INSPECT_FIRST =
  'Inspect the page state before retrying; a blind re-run may repeat a write.';

/** Ordered rules: the first match wins. */
const RULES: ReadonlyArray<{ test: (code: string, message: string) => boolean; info: FailureInfo }> = [
  {
    // Died after the command started driving the page.
    test: (code, message) =>
      code === 'cdp_failed' &&
      /Detached while handling command|Inspected target navigated|Target closed/.test(message),
    info: { class: 'unsafe_to_retry', hint: `The page changed while the command was running. ${INSPECT_FIRST}` },
  },
  {
    test: (code, message) => code === 'cdp_failed' && /timed out/i.test(message),
    info: { class: 'unsafe_to_retry', hint: `The command timed out and may still have applied. ${INSPECT_FIRST}` },
  },
  {
    // Failed before any page code ran.
    test: (code, message) =>
      code === 'cdp_failed' && /attach failed|Debugger is not attached/i.test(message),
    info: {
      class: 'safe_to_retry',
      hint: 'The debugger could not attach before any page code ran. Retrying is safe.',
    },
  },
  {
    test: (code, message) => code === 'not_found' && /ref .* unknown/i.test(message),
    info: {
      class: 'retry_after_fix',
      hint: 'The element reference is stale (the page changed). Run observe again to get fresh refs.',
    },
  },
  {
    test: (code, message) => code === 'not_found' && /(tab .* not found|No tab with id)/i.test(message),
    info: {
      class: 'retry_after_fix',
      hint: 'The tab is gone. Run tab list to pick a live tab, or tab create for a new one.',
    },
  },
  {
    // The CLI self-heals this one; keep the hint honest about that.
    test: (code, message) => code === 'not_found' && /session .* unknown/.test(message),
    info: {
      class: 'safe_to_retry',
      hint: 'The session was not found; it is recreated automatically before the command is re-sent.',
    },
  },
  {
    test: (code) => code === 'permission_denied',
    info: {
      class: 'retry_after_fix',
      hint: 'The tab is outside this session’s Agent Window. Borrow it with tab borrow first.',
    },
  },
  {
    test: (code) => code === 'invalid_params',
    info: { class: 'retry_after_fix', hint: 'Fix the arguments before retrying.' },
  },
  {
    test: (code) => code === 'timeout',
    info: {
      class: 'unsafe_to_retry',
      hint: `The command may still be running. ${INSPECT_FIRST}`,
    },
  },
  {
    test: (code) => code === 'extension_disconnected',
    info: {
      class: 'safe_to_retry',
      hint: 'The browser extension disconnected before answering. Reconnect it, then retry.',
    },
  },
  {
    test: (code) => code === 'cancelled' || code === 'user_aborted',
    info: { class: 'fatal', hint: 'The user or caller cancelled this command.' },
  },
];

const FALLBACK: FailureInfo = {
  class: 'fatal',
  hint: 'Retrying the same command is unlikely to help.',
};

/**
 * Classify a failure by retry safety.
 *
 * Matching is on `(code, message)` because the extension has no dedicated
 * "retry safety" field. Unknown shapes fall back to `fatal` on purpose: it is
 * better to make an agent inspect the page than to invite a duplicate write.
 */
export function classifyFailure(error: unknown): FailureInfo {
  if (!(error instanceof BskRpcError)) {
    // Local usage errors are fixable; anything else is unknown.
    return error instanceof BskUsageError
      ? { class: 'retry_after_fix', hint: FALLBACK.hint }
      : FALLBACK;
  }
  for (const rule of RULES) {
    if (rule.test(error.code, error.message)) return rule.info;
  }
  return FALLBACK;
}
