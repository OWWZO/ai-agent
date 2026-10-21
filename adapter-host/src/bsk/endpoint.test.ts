import { describe, expect, it } from 'vitest';

import { resolveEndpoint, BskEndpointError } from './endpoint.js';

describe('resolveEndpoint', () => {
  it('prefers OPENCLI_BSK_RELAY_URL over the legacy OPENCLI_RELAY_URL', () => {
    expect(
      resolveEndpoint({
        OPENCLI_BSK_RELAY_URL: 'ws://127.0.0.1:9100/bsk',
        OPENCLI_RELAY_URL: 'http://127.0.0.1:8080/internal/browser/rpc',
      } as NodeJS.ProcessEnv),
    ).toEqual({ wsUrl: 'ws://127.0.0.1:9100/bsk' });
  });

  it('requires OPENCLI_BSK_RELAY_URL when there is no usable fallback', () => {
    expect(() => resolveEndpoint({} as NodeJS.ProcessEnv)).toThrow(BskEndpointError);
    expect(() => resolveEndpoint({} as NodeJS.ProcessEnv)).toThrow(/OPENCLI_BSK_RELAY_URL is not set/);
    expect(() => resolveEndpoint({ OPENCLI_BSK_RELAY_URL: '   ' } as NodeJS.ProcessEnv)).toThrow(
      /OPENCLI_BSK_RELAY_URL is not set/,
    );
  });

  it('falls back to OPENCLI_RELAY_URL only when it is a WebSocket URL', () => {
    expect(
      resolveEndpoint({ OPENCLI_RELAY_URL: 'ws://127.0.0.1:9000/bsk' } as NodeJS.ProcessEnv),
    ).toEqual({ wsUrl: 'ws://127.0.0.1:9000/bsk' });
    expect(
      resolveEndpoint({ OPENCLI_RELAY_URL: 'wss://relay.example/bsk' } as NodeJS.ProcessEnv),
    ).toEqual({ wsUrl: 'wss://relay.example/bsk' });
  });

  it('rejects an http OPENCLI_RELAY_URL instead of mistaking it for the bsk gateway', () => {
    expect(() =>
      resolveEndpoint({
        OPENCLI_RELAY_URL: 'http://127.0.0.1:8080/internal/browser/rpc',
      } as NodeJS.ProcessEnv),
    ).toThrow(BskEndpointError);
    expect(() =>
      resolveEndpoint({
        OPENCLI_RELAY_URL: 'http://127.0.0.1:8080/internal/browser/rpc',
      } as NodeJS.ProcessEnv),
    ).toThrow(/OPENCLI_BSK_RELAY_URL is not set and OPENCLI_RELAY_URL is not a WebSocket URL/);
    expect(() =>
      resolveEndpoint({ OPENCLI_RELAY_URL: 'https://relay.example/browser/rpc' } as NodeJS.ProcessEnv),
    ).toThrow(/is not a WebSocket URL/);
    expect(() =>
      resolveEndpoint({ OPENCLI_RELAY_URL: 'not a url' } as NodeJS.ProcessEnv),
    ).toThrow(/is not a WebSocket URL/);
  });

  it('accepts ws and wss URLs and maps the browser id', () => {
    expect(resolveEndpoint({ OPENCLI_BSK_RELAY_URL: 'ws://127.0.0.1:9000/bsk' } as NodeJS.ProcessEnv)).toEqual(
      { wsUrl: 'ws://127.0.0.1:9000/bsk' },
    );
    expect(
      resolveEndpoint({
        OPENCLI_BSK_RELAY_URL: 'wss://relay.example/bsk',
        OPENCLI_RELAY_BROWSER_ID: ' browser-1 ',
      } as NodeJS.ProcessEnv),
    ).toEqual({ wsUrl: 'wss://relay.example/bsk', browserId: 'browser-1' });
  });

  it('rejects non-WebSocket and malformed OPENCLI_BSK_RELAY_URL values', () => {
    expect(() =>
      resolveEndpoint({ OPENCLI_BSK_RELAY_URL: 'https://relay.example/bsk' } as NodeJS.ProcessEnv),
    ).toThrow(/must use ws:\/\/ or wss:\/\//);
    expect(() =>
      resolveEndpoint({ OPENCLI_BSK_RELAY_URL: 'not a url' } as NodeJS.ProcessEnv),
    ).toThrow(/not a valid URL/);
  });

  it('maps the visitor id and the shared secret', () => {
    expect(
      resolveEndpoint({
        OPENCLI_BSK_RELAY_URL: 'ws://127.0.0.1:9000/bsk',
        OPENCLI_BSK_VISITOR_ID: ' visitor-9 ',
        OPENCLI_RELAY_SECRET: ' s3cret ',
      } as NodeJS.ProcessEnv),
    ).toEqual({
      wsUrl: 'ws://127.0.0.1:9000/bsk',
      visitorId: 'visitor-9',
      secret: 's3cret',
    });
  });

  it('treats blank optional values as absent', () => {
    expect(
      resolveEndpoint({
        OPENCLI_BSK_RELAY_URL: 'ws://127.0.0.1:9000/bsk',
        OPENCLI_RELAY_BROWSER_ID: '',
        OPENCLI_BSK_VISITOR_ID: '   ',
        OPENCLI_RELAY_SECRET: '',
      } as NodeJS.ProcessEnv),
    ).toEqual({ wsUrl: 'ws://127.0.0.1:9000/bsk' });
  });
});
