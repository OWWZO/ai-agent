import { describe, expect, it, vi } from "vitest";

import { loadCachedSessionFiles } from "./sessionWorkspaceFiles";

describe("loadCachedSessionFiles", () => {
  it("deduplicates in-flight requests and reuses successful results", async () => {
    const cache = new Map<string, string[]>();
    const requests = new Map<string, Promise<string[]>>();
    const load = vi.fn(async () => ["report.md"]);

    const first = loadCachedSessionFiles("session-1", cache, requests, load);
    const second = loadCachedSessionFiles("session-1", cache, requests, load);
    expect(second).toBe(first);
    await expect(first).resolves.toEqual(["report.md"]);
    await expect(
      loadCachedSessionFiles("session-1", cache, requests, load)
    ).resolves.toEqual(["report.md"]);
    expect(load).toHaveBeenCalledOnce();
  });

  it("allows retry after a failed request", async () => {
    const cache = new Map<string, string[]>();
    const requests = new Map<string, Promise<string[]>>();
    const load = vi.fn()
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce(["recovered.md"]);

    await expect(
      loadCachedSessionFiles("session-1", cache, requests, load)
    ).rejects.toThrow("offline");
    await expect(
      loadCachedSessionFiles("session-1", cache, requests, load)
    ).resolves.toEqual(["recovered.md"]);
  });

  it("force reloads a cached session and replaces the cached result", async () => {
    const cache = new Map<string, string[]>([["session-1", ["old.md"]]]);
    const requests = new Map<string, Promise<string[]>>();
    const load = vi.fn(async () => ["new.md"]);

    await expect(
      loadCachedSessionFiles("session-1", cache, requests, load, true)
    ).resolves.toEqual(["new.md"]);

    expect(load).toHaveBeenCalledOnce();
    expect(cache.get("session-1")).toEqual(["new.md"]);
  });

  it("force reload does not reuse an older in-flight request", async () => {
    const cache = new Map<string, string[]>();
    const requests = new Map<string, Promise<string[]>>();
    let resolveFirst: (value: string[]) => void = () => undefined;
    const firstResult = new Promise<string[]>((resolve) => {
      resolveFirst = resolve;
    });
    const load = vi.fn()
      .mockReturnValueOnce(firstResult)
      .mockResolvedValueOnce(["fresh.md"]);

    const first = loadCachedSessionFiles("session-1", cache, requests, load);
    const forced = loadCachedSessionFiles("session-1", cache, requests, load, true);

    await expect(forced).resolves.toEqual(["fresh.md"]);
    resolveFirst(["stale.md"]);
    await expect(first).resolves.toEqual(["stale.md"]);
    expect(cache.get("session-1")).toEqual(["fresh.md"]);
    expect(load).toHaveBeenCalledTimes(2);
  });
});
