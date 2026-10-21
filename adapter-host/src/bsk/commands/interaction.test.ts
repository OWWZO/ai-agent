import { describe, expect, it } from 'vitest';

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import { blur, click, fill, focus, hover, press, select } from './interaction.js';
import { RecordingCaller } from './test-support.js';

describe('click', () => {
  it('maps a ref target and applies Rust defaults', async () => {
    const client = new RecordingCaller().reply(METHODS.toolClick, { tab_id: 1, x: 2, y: 3 });
    await click(client, { sessionId: 's1', target: '@e3' });
    expect(client.last(METHODS.toolClick).params).toEqual({
      session_id: 's1',
      ref: '@e3',
      button: 'left',
      click_count: 1,
      timeout_ms: 30_000,
    });
    expect(client.last(METHODS.toolClick).options?.timeoutMs).toBe(30_000);
  });

  it('maps selectors, tab, button, count, and modifiers', async () => {
    const client = new RecordingCaller().reply(METHODS.toolClick, {});
    await click(client, {
      sessionId: 's1',
      target: '.btn',
      tabId: 7,
      button: 'right',
      clickCount: 2,
      modifiers: 'ctrl,Shift',
      timeoutMs: 5_000,
    });
    expect(client.last(METHODS.toolClick).params).toEqual({
      session_id: 's1',
      selector: '.btn',
      tab_id: 7,
      button: 'right',
      click_count: 2,
      modifiers: ['ctrl', 'shift'],
      timeout_ms: 5_000,
    });
  });

  it('supports capture point clicks', async () => {
    const client = new RecordingCaller().reply(METHODS.toolClick, {});
    await click(client, {
      sessionId: 's1',
      captureId: 'cap-1',
      imageX: 10,
      imageY: 20,
    });
    expect(client.last(METHODS.toolClick).params).toMatchObject({
      capture_id: 'cap-1',
      image_x: 10,
      image_y: 20,
    });
  });

  it('enforces capture/coordinate/selector exclusivity locally', async () => {
    const client = new RecordingCaller();
    await expect(
      click(client, { sessionId: 's1', captureId: 'cap-1', imageX: 1 }),
    ).rejects.toThrow(BskUsageError);
    await expect(
      click(client, { sessionId: 's1', imageX: 1, imageY: 2, target: '@e3' }),
    ).rejects.toThrow(/require --capture/);
    await expect(
      click(client, {
        sessionId: 's1',
        captureId: 'cap-1',
        imageX: 1,
        imageY: 2,
        selector: '#a',
      }),
    ).rejects.toThrow(/conflicts with --selector/);
    await expect(
      click(client, {
        sessionId: 's1',
        captureId: 'cap-1',
        imageX: 1,
        imageY: 2,
        ref: '@e3',
      }),
    ).rejects.toThrow(/conflicts with a target ref/);
    expect(client.calls).toHaveLength(0);
  });

  it('requires exactly one target', async () => {
    await expect(click(new RecordingCaller(), { sessionId: 's1' })).rejects.toThrow(
      /missing target/,
    );
  });
});

describe('hover / focus / blur', () => {
  it('hover sends settle_ms and modifiers', async () => {
    const client = new RecordingCaller().reply(METHODS.toolHover, {});
    await hover(client, { sessionId: 's1', ref: '@e1', settleMs: 50, modifiers: 'alt' });
    expect(client.last(METHODS.toolHover).params).toEqual({
      session_id: 's1',
      ref: '@e1',
      modifiers: ['alt'],
      settle_ms: 50,
      timeout_ms: 30_000,
    });
  });

  it('focus and blur map the same target shape', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.toolFocus, { tab_id: 1, focused: true })
      .reply(METHODS.toolBlur, { tab_id: 1, was_focused: true, focused: false });
    await focus(client, { sessionId: 's1', selector: '#a', tabId: 3 });
    await blur(client, { sessionId: 's1', target: 'e2' });
    expect(client.last(METHODS.toolFocus).params).toEqual({
      session_id: 's1',
      selector: '#a',
      tab_id: 3,
      timeout_ms: 30_000,
    });
    expect(client.last(METHODS.toolBlur).params).toEqual({
      session_id: 's1',
      ref: 'e2',
      timeout_ms: 30_000,
    });
  });
});

describe('fill / press / select', () => {
  it('fill omits clear_before by default and sends false with noClear', async () => {
    const client = new RecordingCaller().reply(METHODS.toolFill, { value_length: 2 });
    await fill(client, { sessionId: 's1', target: '@e1', value: 'hi' });
    expect(client.last(METHODS.toolFill).params).toEqual({
      session_id: 's1',
      value: 'hi',
      ref: '@e1',
      timeout_ms: 30_000,
    });
    await fill(client, { sessionId: 's1', target: '@e1', value: 'hi', noClear: true });
    expect(client.last(METHODS.toolFill).params).toMatchObject({ clear_before: false });
  });

  it('press carries the key, optional target, and hold time', async () => {
    const client = new RecordingCaller().reply(METHODS.toolPress, {});
    await press(client, {
      sessionId: 's1',
      key: 'Enter',
      ref: '@e2',
      holdMs: 40,
      modifiers: 'meta',
    });
    expect(client.last(METHODS.toolPress).params).toEqual({
      session_id: 's1',
      key: 'Enter',
      modifiers: ['meta'],
      ref: '@e2',
      hold_ms: 40,
      timeout_ms: 30_000,
    });
    await expect(press(client, { sessionId: 's1', key: '' })).rejects.toThrow(BskUsageError);
  });

  it('select forwards the full value list', async () => {
    const client = new RecordingCaller().reply(METHODS.toolSelect, {});
    await select(client, { sessionId: 's1', target: '@e3', values: ['us', 'ca'] });
    expect(client.last(METHODS.toolSelect).params).toEqual({
      session_id: 's1',
      values: ['us', 'ca'],
      ref: '@e3',
      timeout_ms: 30_000,
    });
    await expect(select(client, { sessionId: 's1', target: '@e3', values: [] })).rejects.toThrow(
      /at least one --value/,
    );
  });
});
