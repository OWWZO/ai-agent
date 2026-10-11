import { beforeEach, describe, expect, it, vi } from "vitest";

const STORAGE_KEY = "reactor:code-preview-wrap";

/**
 * 测试跑在 node 环境（本仓库没有 jsdom），所以用假的 `window.localStorage`
 * 顶上去；模块级 store 的初始化时机在 import 时，因此每个用例都要
 * `resetModules` 后重新 import。
 */
function createStorage(initial: Record<string, string> = {}): Storage {
  const map = new Map<string, string>(Object.entries(initial));
  const storage = {
    getItem: (key: string) => (map.has(key) ? (map.get(key) as string) : null),
    setItem: (key: string, value: string) => {
      map.set(key, value);
    },
    removeItem: (key: string) => {
      map.delete(key);
    },
    clear: () => map.clear(),
    key: (index: number) => Array.from(map.keys())[index] ?? null,
    get length() {
      return map.size;
    },
  };
  return storage as unknown as Storage;
}

async function loadModule(storage?: Storage) {
  vi.resetModules();
  if (storage) {
    vi.stubGlobal("window", { localStorage: storage });
  }
  return await import("./codePreviewWrap");
}

describe("codePreviewWrap", () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it("默认开启自动换行", async () => {
    const mod = await loadModule();
    expect(mod.readCodePreviewWrap()).toBe(true);
  });

  it("读取已持久化的 false", async () => {
    const mod = await loadModule(createStorage({ [STORAGE_KEY]: "false" }));
    expect(mod.readCodePreviewWrap()).toBe(false);
  });

  it("非法存储值回退默认值", async () => {
    const mod = await loadModule(createStorage({ [STORAGE_KEY]: "maybe" }));
    expect(mod.readCodePreviewWrap()).toBe(true);
  });

  it("写入会持久化并通知订阅者，同值写入不重复通知", async () => {
    const storage = createStorage();
    const mod = await loadModule(storage);
    const listener = vi.fn();
    const unsubscribe = mod.subscribeCodePreviewWrap(listener);

    mod.setCodePreviewWrap(false);
    expect(listener).toHaveBeenCalledTimes(1);
    expect(storage.getItem(STORAGE_KEY)).toBe("false");
    expect(mod.readCodePreviewWrap()).toBe(false);

    mod.setCodePreviewWrap(false);
    expect(listener).toHaveBeenCalledTimes(1);

    unsubscribe();
    mod.setCodePreviewWrap(true);
    expect(listener).toHaveBeenCalledTimes(1);
    expect(mod.readCodePreviewWrap()).toBe(true);
  });

  it("toggle 取反并返回新值", async () => {
    const mod = await loadModule(createStorage());
    expect(mod.toggleCodePreviewWrap()).toBe(false);
    expect(mod.toggleCodePreviewWrap()).toBe(true);
  });

  it("localStorage 写失败时静默降级，内存态仍然生效", async () => {
    const storage = createStorage();
    vi.spyOn(storage, "setItem").mockImplementation(() => {
      throw new Error("QuotaExceededError");
    });
    const mod = await loadModule(storage);

    expect(() => mod.setCodePreviewWrap(false)).not.toThrow();
    expect(mod.readCodePreviewWrap()).toBe(false);
  });

  it("没有 window 时（SSR / node）不抛错", async () => {
    const mod = await loadModule();
    expect(() => mod.setCodePreviewWrap(false)).not.toThrow();
    expect(mod.readCodePreviewWrap()).toBe(false);
  });
});
