import { describe, expect, it } from 'vitest';

import { BskUsageError } from '../errors.js';
import {
  looksLikeRef,
  parseCompletionCriteria,
  parseDurationMs,
  parseModifiers,
  parseTimeoutMs,
  resolveModifiers,
  splitOptionalTarget,
  splitTarget,
  validateWheelDeltas,
} from './args.js';

describe('looksLikeRef', () => {
  it('accepts @eN and eN only', () => {
    expect(looksLikeRef('@e3')).toBe(true);
    expect(looksLikeRef('e42')).toBe(true);
    expect(looksLikeRef('@e0')).toBe(true);
    expect(looksLikeRef('button')).toBe(false);
    expect(looksLikeRef('.btn')).toBe(false);
    expect(looksLikeRef('@')).toBe(false);
    expect(looksLikeRef('e')).toBe(false);
    expect(looksLikeRef('email')).toBe(false);
    expect(looksLikeRef('e3x')).toBe(false);
    expect(looksLikeRef('@E3')).toBe(false);
    expect(looksLikeRef('')).toBe(false);
  });
});

describe('splitTarget', () => {
  it('classifies a lone positional', () => {
    expect(splitTarget('@e3')).toEqual({ ref: '@e3' });
    expect(splitTarget('e3')).toEqual({ ref: 'e3' });
    expect(splitTarget('.btn')).toEqual({ selector: '.btn' });
  });

  it('passes explicit flags through', () => {
    expect(splitTarget(undefined, '@e4')).toEqual({ ref: '@e4' });
    expect(splitTarget(undefined, undefined, '#id')).toEqual({ selector: '#id' });
  });

  it('requires exactly one source', () => {
    expect(() => splitTarget()).toThrow(BskUsageError);
    expect(() => splitTarget(undefined, undefined, undefined)).toThrow(/missing target/);
    expect(() => splitTarget('@e3', 'e4')).toThrow(/exactly one/);
    expect(() => splitTarget('@e3', undefined, '#id')).toThrow(/exactly one/);
    expect(() => splitTarget(undefined, 'e3', '#id')).toThrow(/exactly one/);
  });

  it('rejects empty strings via splitOptionalTarget but allows no target', () => {
    expect(splitOptionalTarget()).toEqual({});
    expect(splitOptionalTarget('@e3')).toEqual({ ref: '@e3' });
    expect(splitOptionalTarget(undefined, undefined, '#panel')).toEqual({ selector: '#panel' });
    expect(() => splitOptionalTarget('')).toThrow(/must not be empty/);
    expect(() => splitOptionalTarget(undefined, '  ')).toThrow(/must not be empty/);
    expect(() => splitOptionalTarget('e3', 'e4')).toThrow(/exactly one/);
  });
});

describe('parseModifiers', () => {
  it('normalises aliases and dedupes', () => {
    expect(parseModifiers('')).toEqual([]);
    expect(parseModifiers('ctrl,Shift')).toEqual(['ctrl', 'shift']);
    expect(parseModifiers('cmd')).toEqual(['meta']);
    expect(parseModifiers('option,opt,alt')).toEqual(['alt']);
    expect(parseModifiers('control,super,command')).toEqual(['ctrl', 'meta']);
    expect(parseModifiers('ctrl,ctrl')).toEqual(['ctrl']);
    expect(parseModifiers(' alt , ,shift ')).toEqual(['alt', 'shift']);
  });

  it('rejects unknown names', () => {
    expect(() => parseModifiers('garbage')).toThrow(/unknown modifier 'garbage'/);
    expect(() => parseModifiers('ctrl,hyper')).toThrow(BskUsageError);
  });

  it('resolveModifiers accepts arrays and returns undefined for empty', () => {
    expect(resolveModifiers(undefined)).toBeUndefined();
    expect(resolveModifiers('')).toBeUndefined();
    expect(resolveModifiers('alt,ctrl')).toEqual(['alt', 'ctrl']);
    expect(resolveModifiers(['shift', 'shift'])).toEqual(['shift']);
    expect(() => resolveModifiers(['nope' as never])).toThrow(BskUsageError);
  });
});

describe('parseTimeoutMs / parseDurationMs', () => {
  it('parses the short duration grammar', () => {
    expect(parseTimeoutMs('30s')).toBe(30_000);
    expect(parseTimeoutMs('250ms')).toBe(250);
    expect(parseTimeoutMs('1m')).toBe(60_000);
    expect(parseTimeoutMs('5')).toBe(5);
    expect(parseTimeoutMs('5s')).toBe(5_000);
    expect(parseTimeoutMs(1500)).toBe(1500);
    expect(parseDurationMs('0')).toBe(0);
    expect(parseDurationMs('0ms')).toBe(0);
  });

  it('rejects zero, negative, unknown suffixes and junk', () => {
    for (const value of ['0', '0ms', '-1', '1h', '1d', 'nope', '', '  ']) {
      expect(() => parseTimeoutMs(value), value).toThrow(BskUsageError);
    }
    expect(() => parseTimeoutMs(-1)).toThrow(BskUsageError);
    expect(() => parseTimeoutMs(1.5)).toThrow(BskUsageError);
    expect(() => parseDurationMs('-1')).toThrow(BskUsageError);
    expect(() => parseDurationMs('nope')).toThrow(BskUsageError);
  });

  it('rejects values that overflow u32 milliseconds', () => {
    expect(() => parseTimeoutMs('4294968s')).toThrow(/too large for u32/);
  });
});

describe('validateWheelDeltas', () => {
  it('requires finite non-zero input', () => {
    expect(() => validateWheelDeltas(0, 600)).not.toThrow();
    expect(() => validateWheelDeltas(-120, 0)).not.toThrow();
    expect(() => validateWheelDeltas(0, 0)).toThrow(/at least one/);
    expect(() => validateWheelDeltas(Number.NaN, 1)).toThrow(/finite/);
    expect(() => validateWheelDeltas(1, Number.POSITIVE_INFINITY)).toThrow(/finite/);
  });
});

describe('parseCompletionCriteria', () => {
  it('parses objects and strings', () => {
    expect(parseCompletionCriteria(undefined)).toBeUndefined();
    expect(parseCompletionCriteria({ any: [] })).toEqual({ any: [] });
    expect(parseCompletionCriteria('{"any":[{"url_contains":"/x"}]}')).toEqual({
      any: [{ url_contains: '/x' }],
    });
  });

  it('rejects invalid JSON and non-objects', () => {
    expect(() => parseCompletionCriteria('{')).toThrow(/invalid --completion-criteria JSON/);
    expect(() => parseCompletionCriteria('[]')).toThrow(/must be a JSON object/);
    expect(() => parseCompletionCriteria('null')).toThrow(/must be a JSON object/);
  });
});
