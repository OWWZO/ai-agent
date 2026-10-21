/**
 * Argument parsing helpers, mirroring the Rust CLI.
 *
 * Sources: `interaction.rs` (`looks_like_ref`, `parse_modifiers`,
 * `split_target`), `navigate.rs` (`parse_timeout_ms`), `waits.rs`
 * (`parse_duration_ms`), and `wheel.rs` (`split_optional_target`).
 * All failures throw {@link BskUsageError}; none of them print or exit.
 */

import { BskUsageError } from '../errors.js';
import type { KeyModifier } from '../protocol.js';

/** `@e3` or `e3` (decimal) → snapshot ref; anything else → CSS selector. */
export function looksLikeRef(target: string): boolean {
  const stripped = target.startsWith('@') ? target.slice(1) : target;
  if (!stripped.startsWith('e')) return false;
  const rest = stripped.slice(1);
  return rest.length > 0 && /^[0-9]+$/.test(rest);
}

export interface SplitTargetResult {
  ref?: string;
  selector?: string;
}

/**
 * Exactly one of positional / `--ref` / `--selector` wins. The positional
 * value is classified by {@link looksLikeRef}.
 */
export function splitTarget(
  positional?: string,
  explicitRef?: string,
  explicitSelector?: string,
): SplitTargetResult {
  if (positional === undefined && explicitRef === undefined && explicitSelector === undefined) {
    throw new BskUsageError(
      'missing target: pass <ref-or-selector>, --ref @eN, or --selector <css>',
    );
  }
  if (positional === undefined && explicitRef !== undefined && explicitSelector === undefined) {
    return { ref: explicitRef };
  }
  if (positional === undefined && explicitRef === undefined && explicitSelector !== undefined) {
    return { selector: explicitSelector };
  }
  if (positional !== undefined && explicitRef === undefined && explicitSelector === undefined) {
    return looksLikeRef(positional) ? { ref: positional } : { selector: positional };
  }
  throw new BskUsageError('pass exactly one of: <target>, --ref, or --selector');
}

/**
 * Wheel-style optional target: no target at all is valid (viewport centre),
 * but blank strings and conflicting values are rejected.
 */
export function splitOptionalTarget(
  positional?: string,
  explicitRef?: string,
  explicitSelector?: string,
): SplitTargetResult {
  const blanks = [positional, explicitRef, explicitSelector].some(
    (value) => value !== undefined && value.trim().length === 0,
  );
  if (blanks) throw new BskUsageError('wheel target must not be empty');
  if (positional === undefined && explicitRef === undefined && explicitSelector === undefined) {
    return {};
  }
  return splitTarget(positional, explicitRef, explicitSelector);
}

const MODIFIER_ALIASES: Record<string, KeyModifier> = {
  alt: 'alt',
  option: 'alt',
  opt: 'alt',
  ctrl: 'ctrl',
  control: 'ctrl',
  meta: 'meta',
  cmd: 'meta',
  command: 'meta',
  super: 'meta',
  shift: 'shift',
};

/** `"ctrl,Shift"` → `['ctrl','shift']`; unknown names throw. */
export function parseModifiers(input: string): KeyModifier[] {
  const out: KeyModifier[] = [];
  for (const raw of input.split(',')) {
    const name = raw.trim();
    if (name.length === 0) continue;
    const normalised = MODIFIER_ALIASES[name.toLowerCase()];
    if (!normalised) throw new BskUsageError(`unknown modifier '${name.toLowerCase()}'`);
    if (!out.includes(normalised)) out.push(normalised);
  }
  return out;
}

/** Accepts either the CLI string form or an already-parsed array. */
export function resolveModifiers(input?: string | KeyModifier[]): KeyModifier[] | undefined {
  if (input === undefined) return undefined;
  const parsed = typeof input === 'string' ? parseModifiers(input) : dedupeModifiers(input);
  return parsed.length > 0 ? parsed : undefined;
}

function dedupeModifiers(values: KeyModifier[]): KeyModifier[] {
  const out: KeyModifier[] = [];
  for (const value of values) {
    if (!(value in MODIFIER_ALIASES)) {
      throw new BskUsageError(`unknown modifier '${String(value)}'`);
    }
    if (!out.includes(value)) out.push(value);
  }
  return out;
}

/**
 * Short duration grammar: bare integer = milliseconds, or `ms` / `s` / `m`.
 * Zero is rejected (`--timeout` must be positive) and the value must fit u32.
 */
export function parseTimeoutMs(input: string | number): number {
  const ms = parseDurationValue(input, 'timeout');
  if (ms === 0) throw new BskUsageError(`invalid timeout '${input}': must be greater than zero`);
  if (ms > 0xffff_ffff) throw new BskUsageError(`timeout '${input}' too large for u32 ms`);
  return ms;
}

/**
 * Same grammar as {@link parseTimeoutMs} but zero is allowed and the value is
 * not capped — used by `wait-ms` (the daemon enforces its own 5-minute cap).
 */
export function parseDurationMs(input: string | number): number {
  return parseDurationValue(input, 'duration');
}

function parseDurationValue(input: string | number, label: string): number {
  if (typeof input === 'number') {
    if (!Number.isFinite(input) || !Number.isInteger(input) || input < 0) {
      throw new BskUsageError(`invalid ${label} '${input}': expected non-negative integer`);
    }
    return input;
  }
  const arg = input.trim();
  if (arg.length === 0) throw new BskUsageError(`empty ${label}`);
  let numStr: string;
  let suffix: 'ms' | 's' | 'm';
  if (arg.endsWith('ms')) {
    numStr = arg.slice(0, -2);
    suffix = 'ms';
  } else if (arg.endsWith('s')) {
    numStr = arg.slice(0, -1);
    suffix = 's';
  } else if (arg.endsWith('m')) {
    numStr = arg.slice(0, -1);
    suffix = 'm';
  } else {
    numStr = arg;
    suffix = 'ms';
  }
  const trimmed = numStr.trim();
  if (!/^[0-9]+$/.test(trimmed)) {
    throw new BskUsageError(`invalid ${label} '${arg}': expected number`);
  }
  const n = Number(trimmed);
  if (!Number.isSafeInteger(n)) {
    throw new BskUsageError(`invalid ${label} '${arg}': value too large`);
  }
  const factor = suffix === 'ms' ? 1 : suffix === 's' ? 1_000 : 60_000;
  const ms = n * factor;
  if (!Number.isSafeInteger(ms)) {
    throw new BskUsageError(`invalid ${label} '${arg}': value too large`);
  }
  return ms;
}

/** Positive integer validation for `--limit` / `--max-text-chars` style flags. */
export function parsePositiveInt(value: number | string, label: string): number {
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isInteger(n) || n < 1) {
    throw new BskUsageError(`${label} must be a positive integer`);
  }
  return n;
}

/** Reject non-finite wheel deltas and the all-zero gesture. */
export function validateWheelDeltas(deltaX: number, deltaY: number): void {
  if (!Number.isFinite(deltaX) || !Number.isFinite(deltaY)) {
    throw new BskUsageError('wheel deltas must be finite numbers');
  }
  if (deltaX === 0 && deltaY === 0) {
    throw new BskUsageError('at least one of --delta-x or --delta-y must be non-zero');
  }
}

/** Parse `--completion-criteria` JSON for `request-help`. */
export function parseCompletionCriteria(
  input: string | Record<string, unknown> | undefined,
): Record<string, unknown> | undefined {
  if (input === undefined) return undefined;
  if (typeof input !== 'string') return input;
  try {
    const parsed: unknown = JSON.parse(input);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      throw new BskUsageError('completion criteria must be a JSON object');
    }
    return parsed as Record<string, unknown>;
  } catch (error) {
    if (error instanceof BskUsageError) throw error;
    throw new BskUsageError(
      `invalid --completion-criteria JSON: ${error instanceof Error ? error.message : String(error)}`,
    );
  }
}
