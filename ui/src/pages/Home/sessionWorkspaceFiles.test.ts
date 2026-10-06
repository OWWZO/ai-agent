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
});
