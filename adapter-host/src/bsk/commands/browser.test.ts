import { describe, expect, it } from 'vitest';

import { BskUsageError } from '../errors.js';
import { METHODS } from '../methods.js';
import {
  DEVICE_PRESETS,
  browserList,
  buildEmulateParams,
  emulate,
  findPreset,
  handshake,
  ping,
  presetNames,
  systemStatus,
  windowResize,
} from './browser.js';
import { RecordingCaller } from './test-support.js';

describe('browser list / status / handshake', () => {
  it('browserList forwards the wait hint', async () => {
    const client = new RecordingCaller().reply(METHODS.browserList, { browsers: [] });
    await browserList(client, { waitForBrowserMs: 1_000 });
    expect(client.last(METHODS.browserList).params).toEqual({ wait_for_browser_ms: 1_000 });
    expect(client.last(METHODS.browserList).options?.timeoutMs).toBe(6_000);
  });

  it('systemStatus omits params when no wait is requested', async () => {
    const client = new RecordingCaller().reply(METHODS.systemStatus, {});
    await systemStatus(client);
    expect(client.last(METHODS.systemStatus).params).toBeUndefined();
  });

  it('ping and handshake use the protocol shape', async () => {
    const client = new RecordingCaller()
      .reply(METHODS.systemPing, { pong: true })
      .reply(METHODS.systemHandshake, { server: 'daemon', protocol_version: '1.0' });
    expect((await ping(client)).pong).toBe(true);
    await handshake(client, {
      client: 'reactor-adapter',
      version: '1.0.0',
      protocolVersion: '1.3',
      instanceId: 'b1',
      browser: { name: 'chrome', version: '131' },
      label: 'Work',
      minCompatibleProtocol: '1.0',
    });
    expect(client.last(METHODS.systemHandshake).params).toEqual({
      client: 'reactor-adapter',
      version: '1.0.0',
      protocol_version: '1.3',
      instance_id: 'b1',
      browser: { name: 'chrome', version: '131' },
      label: 'Work',
      min_compatible_protocol: '1.0',
    });
  });

  it('windowResize maps dimensions', async () => {
    const client = new RecordingCaller().reply(METHODS.toolWindowResize, {});
    await windowResize(client, { sessionId: 's1', width: 1280, height: 800 });
    expect(client.last(METHODS.toolWindowResize).params).toEqual({
      session_id: 's1',
      width: 1280,
      height: 800,
    });
  });
});

describe('emulate', () => {
  it('mirrors the Rust device presets', () => {
    expect(DEVICE_PRESETS.map((preset) => preset.name)).toEqual([
      'iphone-14',
      'iphone-14-pro-max',
      'iphone-se',
      'pixel-7',
      'galaxy-s23',
      'ipad-mini',
      'galaxy-tab-s8',
    ]);
    expect(findPreset('iphone-14')).toMatchObject({ width: 390, height: 844, maxTouchPoints: 5 });
    expect(findPreset('nope')).toBeUndefined();
    expect(presetNames()).toContain('galaxy-s23');
  });

  it('applies a preset as the base and lets manual flags override it', () => {
    const params = buildEmulateParams({ sessionId: 's1', device: 'iphone-14', width: 400 });
    expect(params).toEqual({
      session_id: 's1',
      overrides: {
        width: 400,
        height: 844,
        device_scale_factor: 3,
        mobile: true,
        user_agent: expect.stringContaining('iPhone'),
        touch: true,
        max_touch_points: 5,
      },
    });
  });

  it('supports off and rejects it combined with anything else', () => {
    expect(buildEmulateParams({ sessionId: 's1', tabId: 2, off: true })).toEqual({
      session_id: 's1',
      tab_id: 2,
      off: true,
    });
    expect(() => buildEmulateParams({ sessionId: 's1', off: true, device: 'iphone-14' })).toThrow(
      /--off cannot be combined/,
    );
    expect(() => buildEmulateParams({ sessionId: 's1', off: true, touch: true })).toThrow(
      /--off cannot be combined/,
    );
  });

  it('validates cross-field requirements on the merged result', () => {
    expect(() => buildEmulateParams({ sessionId: 's1' })).toThrow(/nothing to do/);
    expect(() => buildEmulateParams({ sessionId: 's1', device: 'nope' })).toThrow(
      /unknown device preset 'nope'/,
    );
    expect(() => buildEmulateParams({ sessionId: 's1', width: 390 })).toThrow(
      /--width and --height/,
    );
    expect(() => buildEmulateParams({ sessionId: 's1', dpr: 2 })).toThrow(
      /require --width\/--height/,
    );
    expect(() =>
      buildEmulateParams({ sessionId: 's1', acceptLanguage: 'zh-CN', width: 390, height: 844 }),
    ).toThrow(/--accept-language requires --ua/);
    expect(
      buildEmulateParams({
        sessionId: 's1',
        width: 390,
        height: 844,
        ua: 'UA',
        acceptLanguage: 'zh-CN',
        touch: true,
      }),
    ).toMatchObject({ overrides: { accept_language: 'zh-CN', touch: true } });
  });

  it('emulate dispatches the built params', async () => {
    const client = new RecordingCaller().reply(METHODS.toolEmulate, { cleared: false });
    await emulate(client, { sessionId: 's1', device: 'pixel-7' });
    expect(client.last(METHODS.toolEmulate).params).toMatchObject({
      session_id: 's1',
      overrides: { width: 412, height: 915, mobile: true },
    });
  });

  it('throws BskUsageError for bad combinations', () => {
    expect(() => buildEmulateParams({ sessionId: 's1' })).toThrow(BskUsageError);
  });
});
