import { describe, expect, it, vi } from "vitest";
import { SessionManager } from "@/session-manager/manager";
import type { ChromeTabsApi } from "../shared";
import { type ChromeCookiesApi, handleCookies } from "../cookies";

function fakeAgentWindow(ids: number[]) {
  let i = 0;
  return {
    create: vi.fn(async () => {
      const id = ids[i++];
      if (id === undefined) throw new Error("ran out of fake ids");
      return { windowId: id, initialTabIds: [] };
    }),
    remove: vi.fn(async () => {}),
    ensureActiveTab: vi.fn(async () => 1),
  };
}

async function makeManager(): Promise<SessionManager> {
  const sm = new SessionManager({ agentWindow: fakeAgentWindow([100]) });
  await sm.start("aa11");
  return sm;
}

function fakeTabs(tabUrl = "https://example.test/path"): ChromeTabsApi {
  const tab = { id: 4, windowId: 100, active: true, url: tabUrl } as chrome.tabs.Tab;
  return {
    get: vi.fn(async () => tab),
    query: vi.fn(async () => [tab]),
  };
}

function fakeCookies() {
  const calls: chrome.cookies.GetAllDetails[] = [];
  const api: ChromeCookiesApi = {
    getAll: vi.fn(async (details: chrome.cookies.GetAllDetails) => {
      calls.push(details);
      return [
        {
          name: "sid",
          value: "abc",
          domain: ".example.test",
          path: "/",
          secure: true,
          httpOnly: true,
          session: false,
          hostOnly: false,
          storeId: "0",
        } as chrome.cookies.Cookie,
        {
          name: "temp",
          value: "1",
          domain: ".example.test",
          path: "/",
          secure: false,
          httpOnly: false,
          session: true,
          hostOnly: false,
          storeId: "0",
        } as chrome.cookies.Cookie,
      ];
    }),
  };
  return { api, calls };
}

describe("handleCookies", () => {
  it("scopes by the target tab origin when neither domain nor url is given", async () => {
    const sm = await makeManager();
    const { api, calls } = fakeCookies();
    const result = await handleCookies(
      sm,
      { session_id: "aa11" },
      { tabsApi: fakeTabs(), cookiesApi: api },
    );

    expect(calls).toEqual([{ url: "https://example.test" }]);
    expect(result).toMatchObject({ tab_id: 4, scope: "https://example.test" });
  });

  it("prefers an explicit url over an explicit domain", async () => {
    const sm = await makeManager();
    const { api, calls } = fakeCookies();
    await handleCookies(
      sm,
      { session_id: "aa11", domain: "example.test", url: "https://other.test/x" },
      { tabsApi: fakeTabs(), cookiesApi: api },
    );

    expect(calls).toEqual([{ url: "https://other.test/x" }]);
  });

  it("falls back to an explicit domain when no url is given", async () => {
    const sm = await makeManager();
    const { api, calls } = fakeCookies();
    await handleCookies(
      sm,
      { session_id: "aa11", domain: "example.test" },
      { tabsApi: fakeTabs(), cookiesApi: api },
    );

    expect(calls).toEqual([{ domain: "example.test" }]);
  });

  it("refuses rather than dumping every cookie when no scope is resolvable", async () => {
    const sm = await makeManager();
    const { api, calls } = fakeCookies();
    // about:blank has no origin to scope by.
    const result = await handleCookies(
      sm,
      { session_id: "aa11" },
      { tabsApi: fakeTabs("about:blank"), cookiesApi: api },
    );

    expect(result).toMatchObject({ code: "invalid_params" });
    expect(calls).toEqual([]);
  });

  it("drops expiration_date for session cookies", async () => {
    const sm = await makeManager();
    const { api } = fakeCookies();
    const result = await handleCookies(
      sm,
      { session_id: "aa11", domain: "example.test" },
      { tabsApi: fakeTabs(), cookiesApi: api },
    );

    expect(result).toMatchObject({
      cookies: [
        { name: "sid", http_only: true, secure: true },
        { name: "temp", http_only: false, secure: false },
      ],
    });
    const cookies = (result as unknown as { cookies: Array<Record<string, unknown>> }).cookies;    expect(cookies[0]).not.toHaveProperty("expiration_date");
    expect(cookies[1]).not.toHaveProperty("expiration_date");
  });

  it("rejects an unknown session", async () => {
    const sm = await makeManager();
    const { api } = fakeCookies();
    const result = await handleCookies(
      sm,
      { session_id: "zz99", domain: "example.test" },
      { tabsApi: fakeTabs(), cookiesApi: api },
    );
    expect(result).toMatchObject({ code: "not_found" });
  });

  it("surfaces a chrome failure as protocol_error", async () => {
    const sm = await makeManager();
    const api: ChromeCookiesApi = {
      getAll: vi.fn(async () => {
        throw new Error("simulated chrome failure");
      }),
    };
    const result = await handleCookies(
      sm,
      { session_id: "aa11", domain: "example.test" },
      { tabsApi: fakeTabs(), cookiesApi: api },
    );
    expect(result).toMatchObject({ code: "protocol_error" });
  });
});
