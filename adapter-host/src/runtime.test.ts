import { afterEach, describe, expect, it, vi } from 'vitest';
import { BrowserBridge, CDPBridge } from './browser/index.js';
import { browserSession, getBrowserFactory } from './runtime.js';

describe('getBrowserFactory', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('uses BrowserBridge for regular sites by default', () => {
    expect(getBrowserFactory('xianyu')).toBe(BrowserBridge);
  });

  it('uses CDPBridge when OPENCLI_CDP_ENDPOINT is configured', () => {
    vi.stubEnv('OPENCLI_CDP_ENDPOINT', 'http://127.0.0.1:9333');

    expect(getBrowserFactory('xianyu')).toBe(CDPBridge);
  });

  it('defers direct browser cleanup until a timed-out adapter settles', async () => {
    const close = vi.fn().mockResolvedValue(undefined);
    let settleAdapter!: () => void;
    const adapterRun = new Promise<void>((resolve) => { settleAdapter = resolve; });

    class FakeBrowserFactory {
      async connect() {
        return {} as any;
      }

      async close() {
        await close();
      }
    }

    const session = browserSession(
      FakeBrowserFactory,
      async () => { throw new Error('adapter timeout'); },
      { surface: 'adapter', deferClose: () => adapterRun },
    );

    await expect(session).rejects.toThrow('adapter timeout');
    expect(close).not.toHaveBeenCalled();

    settleAdapter();
    await vi.waitFor(() => expect(close).toHaveBeenCalledTimes(1));
  });
});
