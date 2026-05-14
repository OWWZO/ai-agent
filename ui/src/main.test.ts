import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => {
  const render = vi.fn();
  const createRoot = vi.fn(() => ({ render }));

  return {
    render,
    createRoot,
    patchState: {
      imported: false,
    },
  };
});

vi.mock('@ant-design/v5-patch-for-react-19', () => {
  mocks.patchState.imported = true;
  return {};
});

vi.mock('./App', () => ({
  default: () => null,
}));

vi.mock('react-dom/client', () => ({ createRoot: mocks.createRoot }));

describe('main entry', () => {
  beforeEach(() => {
    mocks.patchState.imported = false;
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

  it('在挂载应用前引入 Ant Design React 19 兼容补丁', async () => {
    await import('./main');

    expect(mocks.patchState.imported).toBe(true);
    expect(mocks.createRoot).toHaveBeenCalledWith(expect.objectContaining({ id: 'root' }));
    expect(mocks.render).toHaveBeenCalledTimes(1);
  });
});
