import { trimTrailingSlash } from "@/pages/WorkspaceImageGeneration/utils";

function buildCurrentToolOrigin(): string {
  if (typeof window === "undefined") {
    return "";
  }
  return `${window.location.protocol}//${window.location.host}`;
}

function buildCurrentToolBaseUrl(): string {
  const origin = buildCurrentToolOrigin();
  if (!origin) {
    return "";
  }
  return `${origin}/tool`;
}

function shouldRewriteToCurrentTool(url: URL): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  const currentHost = window.location.host;
  const currentHostname = window.location.hostname;
  const isLoopback =
    url.hostname === "127.0.0.1" ||
    url.hostname === "localhost";
  const isLegacyTopDomain =
    url.hostname === "owwzo.top" ||
    url.hostname === "www.owwzo.top" ||
    url.hostname === "owwzo.cloud" ||
    url.hostname === "www.owwzo.cloud";
  const isToolPort = url.port === "1601";

  if (isToolPort || (isLoopback && !url.port)) {
    return true;
  }

  if (isLegacyTopDomain && currentHostname !== url.hostname) {
    return true;
  }

  return url.host === currentHost && url.pathname.startsWith("/tool/");
}

export function normalizeToolBaseUrlForBrowser(rawUrl?: string | null): string {
  const normalized = trimTrailingSlash(rawUrl || "");
  const currentToolBaseUrl = buildCurrentToolBaseUrl();
  const currentOrigin = buildCurrentToolOrigin();

  if (!normalized) {
    return currentToolBaseUrl;
  }

  try {
    const parsed = new URL(normalized, currentOrigin || "https://workspace.local");
    if (!shouldRewriteToCurrentTool(parsed)) {
      return parsed.toString().replace(/\/$/, "");
    }

    if (!currentToolBaseUrl) {
      return parsed.toString().replace(/\/$/, "");
    }

    const currentToolUrl = new URL(currentToolBaseUrl);
    currentToolUrl.pathname = parsed.pathname.startsWith("/tool")
      ? parsed.pathname
      : `/tool${parsed.pathname}`;
    currentToolUrl.search = parsed.search;
    currentToolUrl.hash = parsed.hash;
    return currentToolUrl.toString().replace(/\/$/, "");
  } catch {
    if (!currentToolBaseUrl) {
      return normalized;
    }
    if (normalized === "/tool") {
      return currentToolBaseUrl;
    }
    if (normalized.startsWith("/tool/")) {
      return `${currentOrigin}${normalized}`;
    }
    if (normalized.startsWith("/")) {
      return `${currentOrigin}${normalized}`;
    }
    return normalized;
  }
}

export function normalizeFileUrlForBrowser(rawUrl?: string | null): string {
  const normalized = (rawUrl || "").trim();
  if (!normalized) {
    return "";
  }

  try {
    const parsed = new URL(normalized);
    if (!shouldRewriteToCurrentTool(parsed)) {
      return parsed.toString();
    }

    const currentToolBaseUrl = buildCurrentToolBaseUrl();
    if (!currentToolBaseUrl) {
      return parsed.toString();
    }

    const currentToolUrl = new URL(currentToolBaseUrl);
    currentToolUrl.pathname = parsed.pathname.startsWith("/tool/")
      ? parsed.pathname
      : `/tool${parsed.pathname}`;
    currentToolUrl.search = parsed.search;
    currentToolUrl.hash = parsed.hash;
    return currentToolUrl.toString();
  } catch {
    if (normalized.startsWith("/tool/")) {
      const currentOrigin = buildCurrentToolOrigin();
      return currentOrigin ? `${currentOrigin}${normalized}` : normalized;
    }
    return normalized;
  }
}
