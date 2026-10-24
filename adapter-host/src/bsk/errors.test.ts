import { describe, expect, it } from 'vitest';

import { BskRpcError, BskUsageError, classifyFailure } from './errors.js';
import type { RpcErrorCode } from './errors.js';

function rpc(code: RpcErrorCode, message: string): BskRpcError {
  return new BskRpcError({ code, message });
}

describe('classifyFailure', () => {
  it.each([
    // Failures that happen after the command started driving the page.
    ['cdp_failed', 'Detached while handling command'],
    ['cdp_failed', 'Inspected target navigated or closed'],
    ['cdp_failed', 'Target closed'],
    ['cdp_failed', 'CDP command Page.navigate timed out'],
    ['timeout', 'extension did not respond in time'],
  ] as const)('treats %s / %s as unsafe to retry', (code, message) => {
    expect(classifyFailure(rpc(code, message)).class).toBe('unsafe_to_retry');
  });

  it.each([
    ['cdp_failed', 'attach failed'],
    ['cdp_failed', 'Debugger is not attached'],
    ['extension_disconnected', 'relay connection dropped'],
  ] as const)('treats %s / %s as safe to retry', (code, message) => {
    expect(classifyFailure(rpc(code, message)).class).toBe('safe_to_retry');
  });

  it.each([
    ['not_found', 'ref @e3 unknown for tab 4 in session xhs'],
    ['not_found', 'tab 42 not found'],
    ['not_found', 'No tab with id: 42'],
    ['permission_denied', 'Authorize this tab with tab_borrow before reading or operating it'],
    ['invalid_params', 'click requires a ref or a selector'],
  ] as const)('treats %s / %s as retry-after-fix', (code, message) => {
    expect(classifyFailure(rpc(code, message)).class).toBe('retry_after_fix');
  });

  it('treats an unknown session as safe because the CLI recreates it', () => {
    const info = classifyFailure(rpc('not_found', 'session xhs unknown'));
    expect(info.class).toBe('safe_to_retry');
    expect(info.hint).toMatch(/recreated automatically/);
  });

  it.each([
    ['cancelled', 'rpc aborted'],
    ['user_aborted', 'pending user interrupt'],
  ] as const)('treats %s as fatal', (code, message) => {
    expect(classifyFailure(rpc(code, message)).class).toBe('fatal');
  });

  it('falls back to fatal for an unrecognised rpc error', () => {
    expect(classifyFailure(rpc('protocol_error', 'something odd')).class).toBe('fatal');
  });

  it('does not misread a stale-ref message as a missing session', () => {
    // "unknown" precedes "session" here, so the session rule must not fire.
    const info = classifyFailure(rpc('not_found', 'ref @e3 unknown for tab 4 in session xhs'));
    expect(info.class).toBe('retry_after_fix');
    expect(info.hint).toMatch(/observe again/);
  });

  it('treats a local usage error as retry-after-fix', () => {
    expect(classifyFailure(new BskUsageError('--session is required')).class).toBe('retry_after_fix');
  });

  it('uses the usage error message as the hint, not the fatal fallback', () => {
    // 落盘失败这类 usage error 的消息里已经写了「改 --out 再重试」；
    // 用通用 fatal hint 会告诉模型「重试没意义」，把它引向错误方向。
    const info = classifyFailure(
      new BskUsageError('screenshot captured (9 bytes) but could not be written to "x.png": EACCES; retry with a writable relative --out path'),
    );
    expect(info.class).toBe('retry_after_fix');
    expect(info.hint).toContain('--out');
    expect(info.hint).not.toBe('Retrying the same command is unlikely to help.');
  });

  it('treats a non-bsk error as fatal', () => {
    expect(classifyFailure(new Error('boom')).class).toBe('fatal');
    expect(classifyFailure('boom').class).toBe('fatal');
  });

  it('always supplies a non-empty hint', () => {
    for (const info of [
      classifyFailure(rpc('cdp_failed', 'Target closed')),
      classifyFailure(rpc('protocol_error', 'weird')),
      classifyFailure(new Error('boom')),
    ]) {
      expect(info.hint.length).toBeGreaterThan(0);
    }
  });
});
