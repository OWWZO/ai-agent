import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => {
  const render = vi.fn();
  const createRoot = vi.fn(() => ({ render }));

  return {
    render,
    createRoot,
  };
});

vi.mock('./App', () => ({
  default: () => null,
}));

vi.mock('react-dom/client', () => ({ createRoot: mocks.createRoot }));

describe('main entry', () => {
  beforeEach(() => {
    mocks.render.mockClear();
    mocks.createRoot.mockClear();
    const rootElement = { id: 'root' };
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      value: { getElementById: vi.fn((id: string) => (id === 'root' ? rootElement : null)) },
    });
    vi.resetModules();
  });

  afterEach(() => {
    Reflect.deleteProperty(globalThis, 'document');
  });

  it('挂载应用到 #root', async () => {
    await import('./main');

    expect(mocks.createRoot).toHaveBeenCalledWith(expect.objectContaining({ id: 'root' }));
    expect(mocks.render).toHaveBeenCalledTimes(1);
  });
});
