import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { copyToClipboard } from "./utils";

/**
 * 这个项目的 vitest 跑在 node 环境（没有 jsdom），而 copyToClipboard 的
 * execCommand 兜底需要 document。这里手工造一个最小 document，
 * 比为了几个测试引入 jsdom 依赖更划算。
 */
type FakeTextarea = {
  value: string;
  focus: () => void;
  select: () => void;
  setAttribute: (name: string, value: string) => void;
  style: { cssText: string };
};

function installFakeDocument(execCommand: () => boolean) {
  const created: FakeTextarea[] = [];
  const removed: FakeTextarea[] = [];
  const fakeDocument = {
    createElement: () => {
      const el: FakeTextarea = {
        value: "",
        focus: () => undefined,
        select: () => undefined,
        setAttribute: () => undefined,
        style: { cssText: "" },
      };
      created.push(el);
      return el;
    },
    body: {
      appendChild: () => undefined,
      removeChild: (el: FakeTextarea) => {
        removed.push(el);
      },
    },
    execCommand,
  };
  (globalThis as { document?: unknown }).document = fakeDocument;
  return {
    created,
    removed,
  };
}

describe("copyToClipboard", () => {
  const originalClipboard = navigator.clipboard;

  const stubClipboard = (
    writeText: ((value: string) => Promise<void>) | undefined
  ) => {
    Object.defineProperty(navigator, "clipboard", {
      value: writeText ? { writeText } : undefined,
      configurable: true,
      writable: true,
    });
  };

  beforeEach(() => {
    stubClipboard(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    Object.defineProperty(navigator, "clipboard", {
      value: originalClipboard,
      configurable: true,
      writable: true,
    });
    delete (globalThis as { document?: unknown }).document;
  });

  it("Clipboard API 可用时写入并返回 true", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard(writeText);

    await expect(copyToClipboard("hello")).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith("hello");
  });

  it("数字与 undefined 都转成字符串", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard(writeText);

    await copyToClipboard(42);
    expect(writeText).toHaveBeenLastCalledWith("42");
    await copyToClipboard(undefined);
    expect(writeText).toHaveBeenLastCalledWith("");
  });

  it("writeText 被拒时返回 false，且不偷偷走 execCommand 兜底", async () => {
    // 这是 review 修复的核心语义：API 存在但写入失败时不兜底，
    // 否则调用方分不清「不可用」和「这次失败」，UI 反馈会失真。
    stubClipboard(vi.fn().mockRejectedValue(new Error("denied")));
    const execCommand = vi.fn().mockReturnValue(true);
    installFakeDocument(execCommand);

    await expect(copyToClipboard("x")).resolves.toBe(false);
    expect(execCommand).not.toHaveBeenCalled();
  });

  it("Clipboard API 整体缺失时退到 execCommand", async () => {
    const { created, removed } = installFakeDocument(() => true);

    await expect(copyToClipboard("x")).resolves.toBe(true);
    // textarea 用完必须移除，否则会在 body 里堆积
    expect(created).toHaveLength(1);
    expect(removed).toHaveLength(1);
    expect(created[0].value).toBe("x");
  });

  it("execCommand 返回 false 时如实返回 false", async () => {
    installFakeDocument(() => false);
    await expect(copyToClipboard("x")).resolves.toBe(false);
  });

  it("execCommand 抛错时返回 false，不把异常抛给调用方", async () => {
    installFakeDocument(() => {
      throw new Error("boom");
    });
    await expect(copyToClipboard("x")).resolves.toBe(false);
  });
});
