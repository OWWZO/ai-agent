import { describe, expect, it } from 'vitest';

import { METHODS } from '../methods.js';
import {
  readScreenshotCapture,
  screenshot,
  screenshotFullPage,
  screenshotRead,
  screenshotRelease,
  validateScreenshotChunk,
} from './screenshot.js';
import { RecordingCaller } from './test-support.js';

const PNG_BASE64 = Buffer.from('png-bytes').toString('base64');

describe('screenshot', () => {
  it('maps session/tab/ref', async () => {
    const client = new RecordingCaller().reply(METHODS.toolScreenshot, {});
    await screenshot(client, { sessionId: 's1', tabId: 3, ref: '@e2' });
    expect(client.last(METHODS.toolScreenshot).params).toEqual({
      session_id: 's1',
      tab_id: 3,
      ref: '@e2',
    });
  });

  it('full page defaults to a 2m budget with IPC slack', async () => {
    const client = new RecordingCaller().reply(METHODS.toolScreenshotFullPage, {});
    await screenshotFullPage(client, { sessionId: 's1', scope: 'current' });
    expect(client.last(METHODS.toolScreenshotFullPage).params).toEqual({
      scope: 'current',
      session_id: 's1',
      timeout_ms: 120_000,
    });
    expect(client.last(METHODS.toolScreenshotFullPage).options?.timeoutMs).toBe(125_000);
  });

  it('read/release map capture ids', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.toolScreenshotRead, {})
      .reply(METHODS.toolScreenshotRelease, { released: true });
    await screenshotRead(client, { sessionId: 's1', captureId: 'cap', offset: 12 });
    await screenshotRelease(client, { sessionId: 's1', captureId: 'cap' });
    expect(client.last(METHODS.toolScreenshotRead).params).toEqual({
      session_id: 's1',
      capture_id: 'cap',
      offset: 12,
    });
    expect(client.last(METHODS.toolScreenshotRelease).params).toEqual({
      session_id: 's1',
      capture_id: 'cap',
    });
  });
});

describe('validateScreenshotChunk', () => {
  const valid = { data_base64: PNG_BASE64, next_offset: 9, eof: true };

  it('accepts a complete chunk', () => {
    expect(validateScreenshotChunk(valid, 0, 9).toString()).toBe('png-bytes');
  });

  it('rejects gaps, overruns, empty data, and wrong eof', () => {
    expect(() => validateScreenshotChunk(valid, 1, 10)).toThrow(/incomplete or invalid/);
    expect(() => validateScreenshotChunk(valid, 0, 10)).toThrow(/incomplete or invalid/);
    expect(() => validateScreenshotChunk({ ...valid, eof: false }, 0, 9)).toThrow(
      /incomplete or invalid/,
    );
    expect(() =>
      validateScreenshotChunk({ data_base64: '', next_offset: 0, eof: false }, 0, 9),
    ).toThrow(/incomplete or invalid/);
    expect(() =>
      validateScreenshotChunk({ ...valid, data_base64: 'A'.repeat(349_532) }, 0, 300_000),
    ).toThrow(/exceeds 256 KiB/);
  });
});

describe('readScreenshotCapture', () => {
  it('drains chunks and releases the capture', async () => {
    const first = Buffer.from('abc');
    const second = Buffer.from('defg');
    const client = new RecordingCaller()
      .reply(METHODS.toolScreenshotRead, (params: unknown) => {
        const { offset } = params as { offset: number };
        return offset === 0
          ? { data_base64: first.toString('base64'), next_offset: 3, eof: false }
          : { data_base64: second.toString('base64'), next_offset: 7, eof: true };
      })
      .reply(METHODS.toolScreenshotRelease, { released: true });
    const bytes = await readScreenshotCapture(client, {
      sessionId: 's1',
      captureId: 'cap',
      byteSize: 7,
    });
    expect(bytes.toString()).toBe('abcdefg');
    expect(client.callsOf(METHODS.toolScreenshotRead).map((c) => c.params)).toEqual([
      { session_id: 's1', capture_id: 'cap', offset: 0 },
      { session_id: 's1', capture_id: 'cap', offset: 3 },
    ]);
    expect(client.last(METHODS.toolScreenshotRelease).params).toEqual({
      session_id: 's1',
      capture_id: 'cap',
    });
  });

  it('releases even when a chunk is invalid', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.toolScreenshotRead, {
        data_base64: PNG_BASE64,
        next_offset: 4,
        eof: false,
      })
      .reply(METHODS.toolScreenshotRelease, { released: true });
    await expect(
      readScreenshotCapture(client, { sessionId: 's1', captureId: 'cap', byteSize: 9 }),
    ).rejects.toThrow(/incomplete or invalid/);
    expect(client.callsOf(METHODS.toolScreenshotRelease)).toHaveLength(1);
  });
});
