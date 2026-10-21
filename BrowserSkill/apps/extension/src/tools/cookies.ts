// `tool.cookies` — read the profile's cookie store for one domain / URL.
//
// Mirrors the OpenCLI extension's `cookies` action, including its safety
// rule: a caller must name a scope. Dumping every cookie in the profile
// would leak unrelated sessions into the agent's context.
//
// Read-only by design. There is no write path — the OpenCLI surface this
// mirrors only ever called `chrome.cookies.getAll`, and a cookie writer is
// a credential-minting primitive that should not arrive by accident.

import type { SessionManager } from "@/session-manager/manager";
import type { CookieEntry, CookiesParams, CookiesResult, RpcError } from "@/transport/types";
import {
  type ChromeTabsApi,
  chromeTabsApi,
  enforceToolTargetScope,
  isRpcError,
  lookupSession,
  resolveTargetTab,
} from "./shared";

/** Subset of `chrome.cookies` we depend on, injectable for unit tests. */
export interface ChromeCookiesApi {
  getAll(details: chrome.cookies.GetAllDetails): Promise<chrome.cookies.Cookie[]>;
}

export const chromeCookiesApi: ChromeCookiesApi = {
  getAll: (details) => chrome.cookies.getAll(details),
};

export interface CookiesDeps {
  tabsApi: ChromeTabsApi;
  cookiesApi: ChromeCookiesApi;
  signal?: AbortSignal;
}

/**
 * Handler for `tool.cookies`.
 *
 * Scope resolution: an explicit `url` wins over `domain`; when neither is
 * given the target tab's origin is used, so a caller that already resolved
 * a tab does not have to restate the site. The result echoes the scope that
 * was actually applied so the caller can tell which one took effect.
 */
export async function handleCookies(
  manager: SessionManager,
  params: CookiesParams,
  deps: CookiesDeps,
): Promise<CookiesResult | RpcError> {
  const ctxOrErr = lookupSession(manager, params, "cookies");
  if (isRpcError(ctxOrErr)) return ctxOrErr;
  const ctx = ctxOrErr;
  if (deps.signal?.aborted) return { code: "cancelled", message: "cookies aborted" };

  const target = await resolveTargetTab(manager, ctx, params.tab_id, deps.tabsApi);
  if (isRpcError(target)) return target;
  // Reading cookies never dispatches page input, so a passive read is
  // allowed to inspect a user tab that was not borrowed.
  const denied = enforceToolTargetScope(ctx, target, "passive_read", "cookies");
  if (denied) return denied;

  const scopeOrErr = resolveCookieScope(params, target.url);
  if (isRpcError(scopeOrErr)) return scopeOrErr;
  const { scope, details } = scopeOrErr;

  try {
    const cookies = await deps.cookiesApi.getAll(details);
    return {
      tab_id: target.tabId,
      scope,
      cookies: cookies.map(toCookieEntry),
    };
  } catch (err) {
    return {
      code: "protocol_error",
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

/**
 * Pick the cookie scope. An explicit `url` wins; otherwise a `domain`; otherwise
 * the tab's own origin. Refuses rather than falling back to "every cookie in
 * the profile" when nothing is resolvable.
 */
function resolveCookieScope(
  params: CookiesParams,
  tabUrl: string | undefined,
): { scope: string; details: chrome.cookies.GetAllDetails } | RpcError {
  const url = params.url?.trim();
  if (url) return { scope: url, details: { url } };

  const domain = params.domain?.trim();
  if (domain) return { scope: domain, details: { domain } };

  const origin = originOf(tabUrl);
  if (origin) return { scope: origin, details: { url: origin } };

  return {
    code: "invalid_params",
    message:
      "cookies requires a domain or url scope (the target tab has no resolvable origin either)",
  };
}

function originOf(url: string | undefined): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    // `chrome.cookies.getAll({url})` needs an http(s) origin; opaque schemes
    // such as about:blank or chrome:// never carry cookies.
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") return null;
    return parsed.origin;
  } catch {
    return null;
  }
}

function toCookieEntry(cookie: chrome.cookies.Cookie): CookieEntry {
  return {
    name: cookie.name,
    value: cookie.value,
    domain: cookie.domain,
    path: cookie.path,
    secure: cookie.secure,
    http_only: cookie.httpOnly,
    ...(typeof cookie.expirationDate === "number"
      ? { expiration_date: cookie.expirationDate }
      : {}),
  };
}

/** Default deps for the dispatcher path, which always has the real APIs. */
export function defaultCookiesDeps(signal?: AbortSignal): CookiesDeps {
  return { tabsApi: chromeTabsApi, cookiesApi: chromeCookiesApi, ...(signal ? { signal } : {}) };
}
