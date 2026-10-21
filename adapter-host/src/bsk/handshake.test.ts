/**
 * Tests for the opt-in `system.handshake` helper.
 *
 * The compat cases are a direct port of `compat_tests` /
 * `compare_protocol_tests` / `parse_major` tests in
 * `BrowserSkill/crates/bsk-protocol/src/system.rs`, so a behaviour change on
 * either side shows up as a failing assertion here.
 */

import { describe, expect, it } from 'vitest';

import {
  BRIDGE_CLIENT_ID,
  HANDSHAKE_TIMEOUT_MS,
  MIN_COMPATIBLE_PEER,
  MIN_COMPATIBLE_PROTOCOL,
  PROTOCOL_VERSION,
  buildHandshakeParams,
  compareProtocol,
  evaluateCompat,
  parseHandshakeResult,
  parseProtocolMajor,
  parseProtocolParts,
} from './handshake.js';
import type { HandshakeResult } from './protocol.js';

/** Minimal `HandshakeResult`; only the two protocol fields drive the verdict. */
function peer(protocolVersion: string, minCompatibleProtocol?: string): HandshakeResult {
  return {
    server: 'browser-skill-daemon',
    version: '0.1.0',
    protocol_version: protocolVersion,
    ...(minCompatibleProtocol === undefined ? {} : { min_compatible_protocol: minCompatibleProtocol }),
  };
}

const REQUIRED_FIELDS = [
  'client',
  'version',
  'protocol_version',
  'instance_id',
  'browser',
  'label',
  'min_compatible_peer',
  'min_compatible_protocol',
] as const;

describe('handshake constants', () => {
  it('advertise the protocol revision the extension expects', () => {
    expect(PROTOCOL_VERSION).toBe('1.3');
    expect(MIN_COMPATIBLE_PROTOCOL).toBe('1.0');
    expect(MIN_COMPATIBLE_PEER).toBe('0.0.0');
    expect(HANDSHAKE_TIMEOUT_MS).toBe(10_000);
  });
});

describe('buildHandshakeParams', () => {
  it('emits every field the wire payload requires', () => {
    const params = buildHandshakeParams({
      version: '1.0.0',
      instanceId: 'inst-1',
      browser: { name: 'chrome', version: '131.0' },
      label: 'agent-window',
    });

    // 经由 unknown 转换：HandshakeParams 无索引签名，直接断言会触发 TS2352。
    const bag = params as unknown as Record<string, unknown>;
    for (const field of REQUIRED_FIELDS) {
      expect(params, `missing ${field}`).toHaveProperty(field);
      expect(bag[field], `blank ${field}`).not.toBeUndefined();
    }
  });

  it('applies the extension defaults', () => {
    const params = buildHandshakeParams({
      version: '1.0.0',
      instanceId: 'inst-1',
      browser: { name: 'chrome', version: '131.0' },
      label: 'agent-window',
    });

    expect(params).toEqual({
      client: BRIDGE_CLIENT_ID,
      version: '1.0.0',
      protocol_version: PROTOCOL_VERSION,
      instance_id: 'inst-1',
      browser: { name: 'chrome', version: '131.0' },
      label: 'agent-window',
      min_compatible_peer: MIN_COMPATIBLE_PEER,
      min_compatible_protocol: MIN_COMPATIBLE_PROTOCOL,
    });
  });

  it('lets the caller override the defaults', () => {
    const params = buildHandshakeParams({
      client: 'custom-client',
      version: '2.0.0',
      protocolVersion: '1.1',
      instanceId: 'inst-2',
      browser: { name: 'firefox', version: '133.0' },
      label: 'lab',
      minCompatiblePeer: '0.1.0',
      minCompatibleProtocol: '1.1',
    });

    expect(params.client).toBe('custom-client');
    expect(params.protocol_version).toBe('1.1');
    expect(params.min_compatible_peer).toBe('0.1.0');
    expect(params.min_compatible_protocol).toBe('1.1');
  });

  it('round-trips through parseHandshakeResult as an ok verdict', () => {
    const params = buildHandshakeParams({
      version: '1.0.0',
      instanceId: 'inst-1',
      browser: { name: 'chrome', version: '131.0' },
      label: 'agent-window',
    });

    // A peer echoing our own declaration is trivially compatible.
    expect(parseHandshakeResult(peer(params.protocol_version, params.min_compatible_protocol))).toEqual({
      compat: 'ok',
    });
  });
});

describe('parseProtocolMajor', () => {
  it('accepts canonical and single-segment forms', () => {
    expect(parseProtocolMajor('1.0')).toBe(1);
    expect(parseProtocolMajor('1.0.0')).toBe(1);
    expect(parseProtocolMajor('1')).toBe(1);
    expect(parseProtocolMajor('123')).toBe(123);
  });

  it('ignores trailing segments', () => {
    expect(parseProtocolMajor('1.')).toBe(1);
    expect(parseProtocolMajor('1.x')).toBe(1);
  });

  it('rejects empty, signed, and scientific notation', () => {
    expect(parseProtocolMajor('.1')).toBeNull();
    expect(parseProtocolMajor('1x')).toBeNull();
    expect(parseProtocolMajor('1e3')).toBeNull();
    expect(parseProtocolMajor('x.0')).toBeNull();
    expect(parseProtocolMajor('')).toBeNull();
    expect(parseProtocolMajor('+1')).toBeNull();
  });

  it('caps at MAX_SAFE_INTEGER like the daemon', () => {
    expect(parseProtocolMajor('9007199254740991')).toBe(9_007_199_254_740_991);
    expect(parseProtocolMajor('9007199254740992')).toBeNull();
    expect(parseProtocolMajor('18446744073709551615')).toBeNull();
  });

  // Mirrors `parse_major_tests::accepts_leading_zeros`.
  it('accepts leading zeros', () => {
    expect(parseProtocolMajor('01')).toBe(1);
    expect(parseProtocolMajor('01.0')).toBe(1);
    expect(parseProtocolMajor('007.9')).toBe(7);
  });
});

describe('parseProtocolParts / compareProtocol', () => {
  // Mirrors `compare_protocol_tests::major_only_treated_as_zero_minor`.
  it('treats a trailing dot as minor 0', () => {
    expect(parseProtocolParts('1.')).toEqual({ major: 1, minor: 0 });
    expect(parseProtocolParts('1')).toEqual({ major: 1, minor: 0 });
    expect(compareProtocol('1.', '1.0')).toBe(0);
    expect(compareProtocol('1', '1.0')).toBe(0);
  });

  it('orders on minor within one major', () => {
    expect(compareProtocol('1.1', '1.0')).toBeGreaterThan(0);
    expect(compareProtocol('1.0', '1.1')).toBeLessThan(0);
    expect(compareProtocol('1.0', '1.0')).toBe(0);
  });

  it('orders on major before minor', () => {
    expect(compareProtocol('2.0', '1.9')).toBeGreaterThan(0);
    expect(compareProtocol('0.9', '1.0')).toBeLessThan(0);
  });

  it('returns null when either side is unparseable', () => {
    expect(compareProtocol('oops', '1.0')).toBeNull();
    expect(compareProtocol('1.0', '1.x')).toBeNull();
    expect(parseProtocolParts('1.x')).toBeNull();
  });
});

describe('evaluateCompat', () => {
  it('ok when the protocol strings match', () => {
    expect(evaluateCompat('1.0', '1.0', '1.0', '1.0')).toEqual({ compat: 'ok' });
  });

  it('skew when the peer minor is newer but the major matches', () => {
    expect(evaluateCompat('1.1', '1.0', '1.0', '1.0')).toEqual({ compat: 'skew' });
  });

  it('skew when our minor is newer but the major matches', () => {
    expect(evaluateCompat('1.0', '1.0', '1.1', '1.0')).toEqual({ compat: 'skew' });
  });

  it('rejects on protocol major mismatch', () => {
    const verdict = evaluateCompat('2.0', '1.0', '1.0', '1.0');
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toContain('protocol major mismatch');
  });

  it('rejects when the peer is below our protocol floor', () => {
    // 必须同 major：major 不同会先被 major 分支拦下（Rust 侧顺序相同）。
    const verdict = evaluateCompat('1.0', '1.0', '1.3', '1.1');
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toContain('below local min_compatible_protocol');
  });

  it('rejects at the major check before the floor check', () => {
    // major 0 != 1：先命中 major 分支，reason 里不会提到 floor。
    const verdict = evaluateCompat('0.9', '0.5', '1.0', '1.0');
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toContain('protocol major mismatch');
  });

  it('rejects when we are below the peer protocol floor', () => {
    const verdict = evaluateCompat('1.0', '1.5', '1.0', '1.0');
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toContain('local protocol');
    expect(verdict.reason).toContain('min_compatible_protocol');
  });

  it('rejects an unparseable peer protocol floor and names it', () => {
    const verdict = evaluateCompat('1.0', 'not-a-protocol', '1.0', '1.0');
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toContain('peer min_compatible_protocol');
    expect(verdict.reason).toContain('not-a-protocol');
  });

  it('rejects an unparseable peer protocol version', () => {
    // "oops" 的 major 解析失败 → major 分支先命中；断言只要求是 reject。
    const verdict = evaluateCompat('oops', '1.0', '1.0', '1.0');
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toBeDefined();
  });

  it('rejects an unparseable local protocol version first', () => {
    // 本端 "oops" 无法解析 → 同样在 major 分支被拦。
    const verdict = evaluateCompat('1.0', 'nope', 'oops', '1.0');
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toBeDefined();
  });

  it('rejects an unparseable protocol with a matching major', () => {
    // major 段可解析但 minor 段不是数字：major 分支放行，后续判定才 reject。
    const verdict = evaluateCompat('1.x', '1.0', '1.0', '1.0');
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toContain('unparseable');
  });

  it('tolerates a null peer floor', () => {
    expect(evaluateCompat('1.0', null, '1.0', '1.0')).toEqual({ compat: 'ok' });
    // Without a floor the peer cannot veto our newer minor.
    expect(evaluateCompat('1.0', null, '1.4', '1.0')).toEqual({ compat: 'skew' });
  });

  it('treats a trailing-dot peer version as minor 0', () => {
    // "1." 解析为 (1,0)，与本端 "1.0" 等价 → ok；与本端 "1.1" 相比 → skew。
    expect(evaluateCompat('1.', '1.0', '1.0', '1.0')).toEqual({ compat: 'ok' });
    expect(evaluateCompat('1.', null, '1.1', '1.0')).toEqual({ compat: 'skew' });
    expect(compareProtocol('1.', '1.0')).toBe(0);
  });

  it('accepts leading zeros in the peer version', () => {
    // "01.0" 解析为 (1,0) ≡ "1.0" → ok；"01.1" 为 (1,1) vs (1,0) → skew。
    expect(evaluateCompat('01.0', '01.0', '1.0', '1.0')).toEqual({ compat: 'ok' });
    expect(evaluateCompat('01.1', '1.0', '1.0', '1.0')).toEqual({ compat: 'skew' });
  });

  it('treats a three-segment version as its first two', () => {
    expect(evaluateCompat('1.3.0', '1.0', '1.3', '1.0')).toEqual({ compat: 'ok' });
  });

  it('ignores app semver drift', () => {
    // App versions never enter the comparison, so any drift is invisible here.
    expect(evaluateCompat('1.0', '1.0', '1.0', '1.0')).toEqual({ compat: 'ok' });
  });
});

describe('parseHandshakeResult', () => {
  it('ok for a peer echoing our own declaration', () => {
    expect(parseHandshakeResult(peer('1.3', '1.0'))).toEqual({ compat: 'ok' });
  });

  it('skew for same-major minor drift', () => {
    expect(parseHandshakeResult(peer('1.2', '1.0'))).toEqual({ compat: 'skew' });
  });

  it('reject for a different protocol major', () => {
    const verdict = parseHandshakeResult(peer('2.0', '1.0'));
    expect(verdict.compat).toBe('reject');
    expect(verdict.reason).toContain('protocol major mismatch');
  });

  it('tolerates a peer that omits min_compatible_protocol', () => {
    const result: HandshakeResult = {
      server: 'browser-skill-daemon',
      version: '0.1.0',
      protocol_version: '1.3',
    };
    expect(parseHandshakeResult(result)).toEqual({ compat: 'ok' });
  });

  it('honours an explicit self declaration', () => {
    expect(parseHandshakeResult(peer('1.3', '1.0'), { protocolVersion: '1.3', minCompatibleProtocol: '1.0' })).toEqual({
      compat: 'ok',
    });
    expect(parseHandshakeResult(peer('1.0', '1.0'), { protocolVersion: '1.3', minCompatibleProtocol: '1.3' }).compat).toBe(
      'reject',
    );
  });
});
